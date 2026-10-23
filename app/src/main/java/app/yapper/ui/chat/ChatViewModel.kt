@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package app.yapper.ui.chat

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yapper.data.ChannelKind
import app.yapper.data.ChatContext
import app.yapper.data.DEFAULT_ROLE_COLOR
import app.yapper.data.MessageBundle
import app.yapper.data.Outcome
import app.yapper.data.PendingAttachment
import app.yapper.data.YapperRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReactionChip(val emoji: String, val count: Int, val mine: Boolean)

data class MessageRow(
    val id: Long,
    val authorId: Long,
    val authorName: String,
    val username: String,
    val avatarColor: Long,
    val nameColor: Long,
    val content: String,
    val createdAt: Long,
    val edited: Boolean,
    val pinned: Boolean,
    val replyToId: Long?,
    val replyAuthor: String?,
    val replySnippet: String?,
    val reactions: List<ReactionChip>,
    val attachmentName: String?,
    val attachmentMime: String?,
    val attachmentPath: String?,
    val mine: Boolean,
)

data class ChatUiState(
    val loading: Boolean = true,
    val missing: Boolean = false,
    val locked: Boolean = false,
    val title: String = "",
    val subtitle: String = "",
    val messages: List<MessageRow> = emptyList(),
    val pinned: List<MessageRow> = emptyList(),
    val reply: MessageRow? = null,
    val editing: Boolean = false,
    val pendingName: String? = null,
    val banner: String? = null,
    val search: String = "",
    val myId: Long = 0,
    val canModerate: Boolean = false,
    val knownUsernames: Set<String> = emptySet(),
)

private data class ChatDraft(
    val search: String = "",
    val replyId: Long? = null,
    val editingId: Long? = null,
    val pending: PendingAttachment? = null,
    val banner: String? = null,
)

class ChatViewModel(private val repository: YapperRepository, private val channelId: Long) : ViewModel() {
    private val draft = MutableStateFlow(ChatDraft())
    private var sendInFlight = false

    private val live = repository.observeBundles(channelId)
    private val shown = draft.flatMapLatest { current ->
        if (current.search.isBlank()) repository.observeBundles(channelId)
        else repository.searchBundles(channelId, current.search)
    }

    val state = combine(repository.observeChatContext(channelId), live, shown, draft) { context, all, visible, current ->
        mapState(context, all, visible, current)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatUiState())

    init {
        viewModelScope.launch {
            live.collect { bundles ->
                val newest = bundles.firstOrNull { !it.message.deleted }?.message?.id ?: return@collect
                repository.markRead(channelId, newest)
            }
        }
    }

    fun setSearch(value: String) = draft.update { it.copy(search = value, banner = null) }

    fun beginReply(id: Long) = draft.update { it.copy(replyId = id, editingId = null, banner = null) }

    fun beginEdit(id: Long) = draft.update { it.copy(editingId = id, replyId = null, banner = null) }

    fun clearModes() = draft.update { it.copy(replyId = null, editingId = null, banner = null) }

    fun clearBanner() = draft.update { it.copy(banner = null) }

    fun attach(uri: Uri) {
        viewModelScope.launch {
            when (val result = repository.importAttachment(uri)) {
                is Outcome.Ok -> {
                    draft.value.pending?.let { repository.discardFile(it.path) }
                    draft.update { it.copy(pending = result.value, banner = null) }
                }
                is Outcome.Err -> draft.update { it.copy(banner = result.message) }
            }
        }
    }

    fun clearAttachment() {
        draft.value.pending?.let { repository.discardFile(it.path) }
        draft.update { it.copy(pending = null) }
    }

    fun send(text: String, onFinished: (Boolean) -> Unit) {
        val current = draft.value
        viewModelScope.launch {
            sendInFlight = true
            val result = if (current.editingId != null) {
                repository.editMessage(current.editingId, text)
            } else {
                repository.sendMessage(channelId, text, current.replyId, current.pending)
            }
            sendInFlight = false
            if (result is Outcome.Ok) {
                draft.update { it.copy(replyId = null, editingId = null, pending = null, banner = null) }
                onFinished(true)
            } else {
                draft.update { it.copy(banner = (result as Outcome.Err).message) }
                onFinished(false)
            }
        }
    }

    fun toggleReaction(messageId: Long, emoji: String) {
        viewModelScope.launch {
            val result = repository.toggleReaction(messageId, emoji)
            if (result is Outcome.Err) draft.update { it.copy(banner = result.message) }
        }
    }

    fun delete(messageId: Long) {
        viewModelScope.launch {
            val result = repository.deleteMessage(messageId)
            if (result is Outcome.Err) draft.update { it.copy(banner = result.message) }
        }
    }

    fun togglePin(messageId: Long) {
        viewModelScope.launch {
            val result = repository.togglePin(messageId)
            if (result is Outcome.Err) draft.update { it.copy(banner = result.message) }
        }
    }

    override fun onCleared() {
        if (!sendInFlight) draft.value.pending?.let { repository.discardFile(it.path) }
    }

    private fun mapState(
        context: ChatContext,
        all: List<MessageBundle>,
        visible: List<MessageBundle>,
        current: ChatDraft,
    ): ChatUiState {
        val channel = context.channel
        val me = context.me
        if (channel == null) return ChatUiState(loading = me == null, missing = me != null)
        val others = context.members.filter { it.id != me?.id }
        val title = when (channel.kind) {
            ChannelKind.DM -> others.firstOrNull()?.displayName ?: "Direct message"
            ChannelKind.GROUP_DM -> channel.name.ifBlank { others.joinToString { it.displayName } }
            ChannelKind.TEXT -> "# ${channel.name}"
            else -> channel.name
        }
        val subtitle = when (channel.kind) {
            ChannelKind.TEXT -> channel.topic
            ChannelKind.DM -> others.firstOrNull()?.customStatus?.ifBlank { "@${others.first().username}" }.orEmpty()
            ChannelKind.GROUP_DM -> context.members.joinToString { it.displayName }
            else -> ""
        }
        val known = context.members.map { it.username }.toSet()
        val rows = mapRows(visible, context, me?.id ?: 0)
        val display = if (current.search.isBlank()) rows.asReversed() else rows
        val pinned = mapRows(all.filter { it.message.pinned && !it.message.deleted }, context, me?.id ?: 0)
        val byId = (mapRows(all, context, me?.id ?: 0) + display).associateBy { it.id }
        return ChatUiState(
            loading = false,
            missing = false,
            locked = !context.accessible,
            title = title,
            subtitle = subtitle,
            messages = display,
            pinned = pinned,
            reply = current.replyId?.let { byId[it] },
            editing = current.editingId != null,
            pendingName = current.pending?.name,
            banner = current.banner,
            search = current.search,
            myId = me?.id ?: 0,
            canModerate = context.canModerate,
            knownUsernames = known,
        )
    }

    private fun mapRows(bundles: List<MessageBundle>, context: ChatContext, meId: Long): List<MessageRow> {
        val byId = bundles.associateBy { it.message.id }
        return bundles.filter { !it.message.deleted }.map { bundle ->
            val message = bundle.message
            val reply = message.replyToId?.let { byId[it] }
            val attachment = bundle.attachments.firstOrNull()
            MessageRow(
                id = message.id,
                authorId = bundle.author.id,
                authorName = bundle.author.displayName,
                username = bundle.author.username,
                avatarColor = bundle.author.accentColor,
                nameColor = nameColor(bundle.author.id, context.roleColors),
                content = message.content,
                createdAt = message.createdAt,
                edited = message.editedAt != null,
                pinned = message.pinned,
                replyToId = message.replyToId,
                replyAuthor = reply?.author?.displayName,
                replySnippet = when {
                    message.replyToId == null -> null
                    reply == null -> "Earlier message"
                    reply.message.deleted -> "Message deleted"
                    else -> reply.message.content.take(80)
                },
                reactions = bundle.reactions.groupBy { it.emoji }.map { (emoji, list) ->
                    ReactionChip(emoji, list.size, list.any { it.userId == meId })
                }.sortedBy { it.emoji },
                attachmentName = attachment?.fileName,
                attachmentMime = attachment?.mime,
                attachmentPath = attachment?.path,
                mine = bundle.author.id == meId,
            )
        }
    }

    private fun nameColor(authorId: Long, colors: Map<Long, Long>): Long {
        val color = colors[authorId] ?: return 0xFFF2F3F5
        return if (color == DEFAULT_ROLE_COLOR) 0xFFF2F3F5 else color
    }
}

val chatEmoji = listOf("👍", "❤️", "😂", "🔥", "😮", "😢", "✅", "🎉", "👀", "💯", "🙌", "⭐")
