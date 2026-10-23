@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package app.yapper.ui.server

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.yapper.data.CategoryEntity
import app.yapper.data.ChannelKind
import app.yapper.data.ChannelSummary
import app.yapper.data.Outcome
import app.yapper.data.ServerEntity
import app.yapper.data.VoicePresence
import app.yapper.data.YapperRepository
import app.yapper.ui.components.Avatar
import app.yapper.ui.components.ConfirmDialog
import app.yapper.ui.components.EmptyHint
import app.yapper.ui.components.Notice
import app.yapper.ui.components.ScreenHeader
import app.yapper.ui.components.UnreadDot
import app.yapper.ui.components.YapperField
import app.yapper.ui.theme.Body
import app.yapper.ui.theme.Blurple
import app.yapper.ui.theme.Heading
import app.yapper.ui.theme.Muted
import app.yapper.ui.theme.Panel
import app.yapper.ui.yapperViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ServerUiState(
    val server: ServerEntity? = null,
    val categories: List<CategoryEntity> = emptyList(),
    val channels: List<ChannelSummary> = emptyList(),
    val voice: List<VoicePresence> = emptyList(),
    val memberCount: Int = 0,
    val invite: String? = null,
    val isOwner: Boolean = false,
    val missing: Boolean = false,
)

class ServerViewModel(private val repository: YapperRepository, private val serverId: Long) : ViewModel() {
    private val head = combine(
        repository.observeServer(serverId),
        repository.observeCategories(serverId),
        repository.observeChannelSummaries(serverId),
        repository.observeServerVoice(serverId),
    ) { server, categories, channels, voice ->
        Head(server, categories, channels, voice)
    }
    private val tail = combine(
        repository.observeMemberCount(serverId),
        repository.observeInviteCode(serverId),
        repository.observeSession(),
    ) { count, invite, me ->
        Triple(count, invite, me)
    }
    val state = combine(head, tail) { top, bottom ->
        val me = bottom.third
        ServerUiState(
            server = top.server,
            categories = top.categories,
            channels = top.channels,
            voice = top.voice,
            memberCount = bottom.first,
            invite = bottom.second,
            isOwner = me != null && top.server?.ownerId == me.id,
            missing = top.server == null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ServerUiState())

    private val _banner = MutableStateFlow<String?>(null)
    val banner = _banner.asStateFlow()

    fun clearBanner() {
        _banner.value = null
    }

    fun createChannel(name: String, voice: Boolean) {
        viewModelScope.launch {
            val kind = if (voice) ChannelKind.VOICE else ChannelKind.TEXT
            val result = repository.createChannel(serverId, name, kind)
            if (result is Outcome.Err) _banner.value = result.message
        }
    }

    fun deleteChannel(channelId: Long) {
        viewModelScope.launch {
            val result = repository.deleteChannel(channelId)
            if (result is Outcome.Err) _banner.value = result.message
        }
    }

    fun leave(onDone: () -> Unit) {
        viewModelScope.launch {
            when (val result = repository.leaveServer(serverId)) {
                is Outcome.Ok -> onDone()
                is Outcome.Err -> _banner.value = result.message
            }
        }
    }

    fun deleteServer(onDone: () -> Unit) {
        viewModelScope.launch {
            when (val result = repository.deleteServer(serverId)) {
                is Outcome.Ok -> onDone()
                is Outcome.Err -> _banner.value = result.message
            }
        }
    }

    private data class Head(
        val server: ServerEntity?,
        val categories: List<CategoryEntity>,
        val channels: List<ChannelSummary>,
        val voice: List<VoicePresence>,
    )
}

@Composable
fun ServerScreen(
    serverId: Long,
    onBack: () -> Unit,
    onChannel: (Long, String) -> Unit,
    onMembers: () -> Unit,
) {
    val viewModel = yapperViewModel(key = "server-$serverId") { ServerViewModel(it, serverId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val banner by viewModel.banner.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    var showCreate by rememberSaveable { mutableStateOf(false) }
    var voiceChannel by rememberSaveable { mutableStateOf(false) }
    var channelName by rememberSaveable { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<Long?>(null) }
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    var confirmDeleteServer by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(Panel)) {
        val server = state.server
        ScreenHeader(
            title = server?.name ?: "Server",
            subtitle = if (state.memberCount > 0) "${state.memberCount} members" else "",
            onBack = onBack,
        ) {
            IconButton(onClick = onMembers) {
                Icon(Icons.Filled.People, contentDescription = "Members", tint = Heading)
            }
            if (state.isOwner) {
                IconButton(onClick = { showCreate = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "Add channel", tint = Heading)
                }
            }
        }
        Notice(banner, viewModel::clearBanner)
        if (state.missing) {
            EmptyHint("This server is gone", "It may have been deleted.")
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    InviteLine(state.invite) { code ->
                        clipboard.setText(AnnotatedString(code))
                        viewModel.clearBanner()
                    }
                }
                val grouped = state.channels.groupBy { it.channel.categoryId }
                val categories = state.categories
                val uncategorized = grouped[null].orEmpty()
                if (uncategorized.isNotEmpty()) {
                    items(uncategorized, key = { it.channel.id }) { channel ->
                        ChannelRow(channel, state.voice, state.isOwner, onChannel) { pendingDelete = channel.channel.id }
                    }
                }
                categories.forEach { category ->
                    item(key = "cat-${category.id}") {
                        Text(
                            text = category.name.uppercase(),
                            color = Muted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                        )
                    }
                    items(grouped[category.id].orEmpty(), key = { it.channel.id }) { channel ->
                        ChannelRow(channel, state.voice, state.isOwner, onChannel) { pendingDelete = channel.channel.id }
                    }
                }
                item {
                    Row(Modifier.padding(16.dp)) {
                        if (state.isOwner) {
                            TextButton(onClick = { confirmDeleteServer = true }) { Text("Delete server", color = app.yapper.ui.theme.Danger) }
                        } else {
                            TextButton(onClick = { confirmLeave = true }) { Text("Leave server", color = app.yapper.ui.theme.Danger) }
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            containerColor = Panel,
            title = { Text("New channel", color = Heading) },
            text = {
                Column {
                    YapperField(channelName, { channelName = it }, "Channel name")
                    TextButton(onClick = { voiceChannel = !voiceChannel }) {
                        Text(if (voiceChannel) "Voice lounge" else "Text channel", color = Blurple)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.createChannel(channelName, voiceChannel)
                    channelName = ""
                    showCreate = false
                }) { Text("Create", color = Blurple) }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("Cancel", color = Muted) } },
        )
    }
    pendingDelete?.let { channelId ->
        ConfirmDialog(
            title = "Delete channel?",
            body = "Messages in it go with it.",
            confirm = "Delete",
            danger = true,
            onConfirm = {
                viewModel.deleteChannel(channelId)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
    if (confirmLeave) {
        ConfirmDialog(
            title = "Leave server?",
            body = "You can rejoin with the invite code.",
            confirm = "Leave",
            danger = true,
            onConfirm = { viewModel.leave(onBack) },
            onDismiss = { confirmLeave = false },
        )
    }
    if (confirmDeleteServer) {
        ConfirmDialog(
            title = "Delete this server?",
            body = "Channels, messages, and the invite go away for everyone on this phone.",
            confirm = "Delete",
            danger = true,
            onConfirm = { viewModel.deleteServer(onBack) },
            onDismiss = { confirmDeleteServer = false },
        )
    }
}

@Composable
private fun InviteLine(code: String?, onCopy: (String) -> Unit) {
    if (code == null) return
    TextButton(onClick = { onCopy(code) }, modifier = Modifier.padding(horizontal = 8.dp)) {
        Text("Invite: $code  ·  tap to copy", color = Blurple, fontSize = 13.sp)
    }
}

@Composable
private fun ChannelRow(
    summary: ChannelSummary,
    voice: List<VoicePresence>,
    owner: Boolean,
    onChannel: (Long, String) -> Unit,
    onDelete: () -> Unit,
) {
    val channel = summary.channel
    val voiceMembers = voice.filter { it.channelId == channel.id }
    Column(
        Modifier
            .combinedClickable(
                onClick = { onChannel(channel.id, channel.kind) },
                onLongClick = { if (owner) onDelete() },
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (channel.kind == ChannelKind.VOICE) Icons.AutoMirrored.Filled.VolumeUp else Icons.Filled.Tag,
                contentDescription = null,
                tint = Muted,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = channel.name,
                color = if (summary.unread > 0) Heading else Body,
                fontWeight = if (summary.unread > 0) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (channel.kind == ChannelKind.VOICE && summary.voiceCount > 0) {
                Text("${summary.voiceCount}", color = Muted, fontSize = 12.sp)
            }
            Spacer(Modifier.width(8.dp))
            UnreadDot(summary.unread)
        }
        voiceMembers.forEach { member ->
            Row(
                modifier = Modifier.padding(start = 28.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(member.displayName, androidx.compose.ui.graphics.Color(member.accentColor), 24.dp, member.status, Panel)
                Spacer(Modifier.width(8.dp))
                Text(member.displayName, color = Body, fontSize = 14.sp, modifier = Modifier.weight(1f))
                if (member.selfDeafened || member.selfMuted) {
                    Text(if (member.selfDeafened) "deafened" else "muted", color = Muted, fontSize = 11.sp)
                }
            }
        }
    }
}
