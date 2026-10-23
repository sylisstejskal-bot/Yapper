@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package app.yapper.ui.chat

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.yapper.data.Limits
import app.yapper.ui.components.Avatar
import app.yapper.ui.components.ConfirmDialog
import app.yapper.ui.components.EmptyHint
import app.yapper.ui.components.Notice
import app.yapper.ui.components.ScreenHeader
import app.yapper.ui.formatClock
import app.yapper.ui.formatDayLabel
import app.yapper.ui.sameDay
import app.yapper.ui.theme.Blurple
import app.yapper.ui.theme.Body
import app.yapper.ui.theme.Chat
import app.yapper.ui.theme.Danger
import app.yapper.ui.theme.Heading
import app.yapper.ui.theme.LinkBlue
import app.yapper.ui.theme.Muted
import app.yapper.ui.theme.Panel
import app.yapper.ui.theme.Raised
import app.yapper.ui.yapperViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface ChatEntry {
    val key: String

    data class Day(val label: String, val anchorId: Long) : ChatEntry {
        override val key: String = "d-$anchorId"
    }

    data class Bubble(val row: MessageRow, val grouped: Boolean) : ChatEntry {
        override val key: String = "m-${row.id}"
    }
}

@Composable
fun ChatScreen(channelId: Long, onBack: () -> Unit, onOpenProfile: (Long) -> Unit) {
    val viewModel = yapperViewModel(key = "chat-$channelId") { ChatViewModel(it, channelId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var text by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<MessageRow?>(null) }
    var pendingDelete by remember { mutableStateOf<MessageRow?>(null) }
    var showPins by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) viewModel.attach(uri)
    }
    val entries = remember(state.messages) { buildEntries(state.messages) }

    LaunchedEffect(state.messages.lastOrNull()?.id, state.search) {
        if (state.search.isNotBlank() || entries.isEmpty()) return@LaunchedEffect
        val last = entries.lastIndex
        val visible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        if (visible < 0 || visible >= last - 3) listState.scrollToItem(last)
    }

    Column(Modifier.fillMaxSize().background(Chat).imePadding()) {
        ScreenHeader(state.title, state.subtitle, onBack) {
            if (state.pinned.isNotEmpty()) {
                IconButton(onClick = { showPins = true }) {
                    Icon(Icons.Filled.PushPin, contentDescription = "Pins", tint = Heading)
                }
            }
            IconButton(onClick = { searchOpen = !searchOpen }) {
                Icon(Icons.Filled.Search, contentDescription = "Search", tint = Heading)
            }
        }
        if (searchOpen) {
            SearchField(state.search, viewModel::setSearch) {
                searchOpen = false
                viewModel.setSearch("")
            }
        }
        Notice(state.banner, viewModel::clearBanner)
        when {
            state.loading -> EmptyHint("Opening channel", "Messages are coming from the phone database.")
            state.missing -> EmptyHint("Channel is gone", "It may have been deleted.")
            state.locked -> EmptyHint("No access", "You aren't in this conversation.")
            else -> {
                LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth()) {
                    items(entries, key = { it.key }) { entry ->
                        when (entry) {
                            is ChatEntry.Day -> DayDivider(entry.label)
                            is ChatEntry.Bubble -> MessageBlock(
                                row = entry.row,
                                grouped = entry.grouped,
                                known = state.knownUsernames,
                                onLongPress = { selected = entry.row },
                                onAvatar = { onOpenProfile(entry.row.authorId) },
                                onReaction = { emoji -> viewModel.toggleReaction(entry.row.id, emoji) },
                                onReplyJump = { replyId ->
                                    val index = entries.indexOfFirst { it is ChatEntry.Bubble && it.row.id == replyId }
                                    if (index >= 0) scope.launch { listState.animateScrollToItem(index) }
                                },
                            )
                        }
                    }
                }
                Composer(
                    text = text,
                    onText = { if (it.length <= Limits.MESSAGE) text = it },
                    placeholder = "Message ${state.title}",
                    reply = state.reply,
                    editing = state.editing,
                    pendingName = state.pendingName,
                    onSend = {
                        viewModel.send(text) { ok -> if (ok) text = "" }
                    },
                    onAttach = { picker.launch("*/*") },
                    onClearReply = {
                        viewModel.clearModes()
                        if (state.editing) text = ""
                    },
                    onClearAttachment = viewModel::clearAttachment,
                )
            }
        }
    }

    selected?.let { row ->
        AlertDialog(
            onDismissRequest = { selected = null },
            containerColor = Panel,
            title = { Text(row.authorName, color = Heading) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        chatEmoji.take(8).forEach { emoji ->
                            Text(
                                emoji,
                                modifier = Modifier.clickable {
                                    viewModel.toggleReaction(row.id, emoji)
                                    selected = null
                                }.padding(4.dp),
                                fontSize = 22.sp,
                            )
                        }
                    }
                    TextButton(onClick = { viewModel.beginReply(row.id); selected = null }) { Text("Reply", color = Body) }
                    if (row.mine) {
                        TextButton(onClick = {
                            text = row.content
                            viewModel.beginEdit(row.id)
                            selected = null
                        }) { Text("Edit", color = Body) }
                    }
                    if (row.mine || state.canModerate) {
                        TextButton(onClick = { viewModel.togglePin(row.id); selected = null }) {
                            Text(if (row.pinned) "Unpin" else "Pin", color = Body)
                        }
                    }
                    TextButton(onClick = {
                        clipboard.setText(AnnotatedString(row.content))
                        selected = null
                    }) { Text("Copy", color = Body) }
                    if (row.mine || state.canModerate) {
                        TextButton(onClick = { pendingDelete = row; selected = null }) { Text("Delete", color = Danger) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { selected = null }) { Text("Close", color = Muted) } },
        )
    }
    pendingDelete?.let { row ->
        ConfirmDialog(
            title = "Delete message?",
            body = "People will see it disappear. Replies keep a placeholder.",
            confirm = "Delete",
            danger = true,
            onConfirm = {
                viewModel.delete(row.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
    if (showPins) {
        AlertDialog(
            onDismissRequest = { showPins = false },
            containerColor = Panel,
            title = { Text("Pinned", color = Heading) },
            text = {
                Column {
                    state.pinned.forEach { pin ->
                        Text("${pin.authorName}: ${pin.content}", color = Body, modifier = Modifier.padding(vertical = 6.dp))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPins = false }) { Text("Close", color = Blurple) } },
        )
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, onClose: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clip(RoundedCornerShape(8.dp)).background(Raised).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.weight(1f).padding(vertical = 12.dp),
            textStyle = androidx.compose.ui.text.TextStyle(color = Heading, fontSize = 15.sp),
            cursorBrush = SolidColor(Blurple),
            singleLine = true,
            decorationBox = { inner ->
                if (value.isEmpty()) Text("Search this channel", color = Muted)
                inner()
            },
        )
        IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Close search", tint = Muted) }
    }
}

@Composable
private fun DayDivider(label: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
        Text(
            label,
            color = Muted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Raised).padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun MessageBlock(
    row: MessageRow,
    grouped: Boolean,
    known: Set<String>,
    onLongPress: () -> Unit,
    onAvatar: () -> Unit,
    onReaction: (String) -> Unit,
    onReplyJump: (Long) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = {}, onLongClick = onLongPress)
            .padding(horizontal = 12.dp, vertical = if (grouped) 1.dp else 8.dp),
    ) {
        if (!grouped && row.replySnippet != null) {
            Text(
                text = "↪ ${row.replyAuthor ?: "Reply"}: ${row.replySnippet}",
                color = Muted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 52.dp, bottom = 2.dp).clickable {
                    row.replyToId?.let(onReplyJump)
                },
            )
        }
        Row {
            if (grouped) {
                Spacer(Modifier.width(52.dp))
            } else {
                Box(Modifier.clickable(onClick = onAvatar)) {
                    Avatar(row.authorName, androidx.compose.ui.graphics.Color(row.avatarColor), 40.dp, background = Chat)
                }
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                if (!grouped) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(row.authorName, color = androidx.compose.ui.graphics.Color(row.nameColor), fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(8.dp))
                        Text(formatClock(row.createdAt), color = Muted, fontSize = 11.sp)
                        if (row.pinned) {
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.Filled.PushPin, contentDescription = "Pinned", tint = Muted, modifier = Modifier.size(12.dp))
                        }
                    }
                }
                if (row.content.isNotBlank()) {
                    Text(messageText(row.content, known), color = Body, fontSize = 15.sp)
                }
                if (row.edited) Text("(edited)", color = Muted, fontSize = 11.sp)
                if (row.attachmentPath != null && row.attachmentName != null && row.attachmentMime != null) {
                    AttachmentBlock(row.attachmentName, row.attachmentMime, row.attachmentPath)
                }
                if (row.reactions.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                        row.reactions.forEach { chip ->
                            Text(
                                "${chip.emoji} ${chip.count}",
                                color = if (chip.mine) Heading else Body,
                                fontSize = 13.sp,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (chip.mine) Blurple.copy(alpha = 0.35f) else Raised)
                                    .clickable { onReaction(chip.emoji) }
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentBlock(name: String, mime: String, path: String) {
    if (mime.startsWith("image/")) {
        val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, path) {
            value = withContext(Dispatchers.IO) {
                runCatching { decodeSampled(path)?.asImageBitmap() }.getOrNull()
            }
        }
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!,
                contentDescription = name,
                modifier = Modifier.padding(top = 6.dp).fillMaxWidth().heightIn(max = 220.dp).clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Fit,
            )
        } else {
            Text(name, color = LinkBlue, modifier = Modifier.padding(top = 4.dp))
        }
    } else {
        Text(name, color = LinkBlue, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun Composer(
    text: String,
    onText: (String) -> Unit,
    placeholder: String,
    reply: MessageRow?,
    editing: Boolean,
    pendingName: String?,
    onSend: () -> Unit,
    onAttach: () -> Unit,
    onClearReply: () -> Unit,
    onClearAttachment: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().background(Chat).padding(8.dp)) {
        if (reply != null) ModeLine("Replying to ${reply.authorName}", onClearReply)
        if (editing) ModeLine("Editing message", onClearReply)
        if (pendingName != null) ModeLine(pendingName, onClearAttachment)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onAttach) { Icon(Icons.Filled.Image, contentDescription = "Attach", tint = Muted) }
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(Raised).padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = onText,
                    textStyle = androidx.compose.ui.text.TextStyle(color = Heading, fontSize = 15.sp),
                    cursorBrush = SolidColor(Blurple),
                    maxLines = 6,
                    decorationBox = { inner ->
                        if (text.isEmpty()) Text(placeholder, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        inner()
                    },
                )
            }
            IconButton(onClick = onSend, enabled = text.isNotBlank() || pendingName != null) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = if (text.isNotBlank() || pendingName != null) Blurple else Muted)
            }
        }
        if (text.length >= 1800) {
            Text("${text.length}/${Limits.MESSAGE}", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(start = 48.dp))
        }
    }
}

@Composable
private fun ModeLine(label: String, onClear: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp, start = 8.dp)) {
        Text(label, color = LinkBlue, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
        IconButton(onClick = onClear) { Icon(Icons.Filled.Close, contentDescription = "Clear", tint = Muted) }
    }
}

private fun buildEntries(messages: List<MessageRow>): List<ChatEntry> {
    val entries = mutableListOf<ChatEntry>()
    messages.forEachIndexed { index, row ->
        val previous = messages.getOrNull(index - 1)
        if (previous == null || !sameDay(previous.createdAt, row.createdAt)) {
            entries += ChatEntry.Day(formatDayLabel(row.createdAt), row.id)
        }
        val grouped = previous != null &&
            previous.authorId == row.authorId &&
            sameDay(previous.createdAt, row.createdAt) &&
            row.createdAt - previous.createdAt < 7 * 60_000 &&
            row.replySnippet == null
        entries += ChatEntry.Bubble(row, grouped)
    }
    return entries
}

private fun messageText(content: String, known: Set<String>): AnnotatedString = buildAnnotatedString {
    val regex = Regex("@(?:everyone|[a-z0-9_]{2,20})")
    var index = 0
    for (match in regex.findAll(content)) {
        append(content.substring(index, match.range.first))
        val name = match.value.removePrefix("@")
        if (name == "everyone" || name in known) {
            withStyle(SpanStyle(color = LinkBlue, fontWeight = FontWeight.SemiBold)) { append(match.value) }
        } else {
            append(match.value)
        }
        index = match.range.last + 1
    }
    append(content.substring(index))
}

private fun decodeSampled(path: String): android.graphics.Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (bounds.outWidth / sample > 1200 && bounds.outWidth > 0) sample *= 2
    return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
}
