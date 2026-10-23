package app.yapper.ui.friends

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.yapper.data.FriendRow
import app.yapper.data.Outcome
import app.yapper.data.Presence
import app.yapper.data.YapperRepository
import app.yapper.ui.components.Avatar
import app.yapper.ui.components.EmptyHint
import app.yapper.ui.components.Notice
import app.yapper.ui.components.PrimaryButton
import app.yapper.ui.components.ScreenHeader
import app.yapper.ui.components.YapperField
import app.yapper.ui.components.statusLabel
import app.yapper.ui.theme.Blurple
import app.yapper.ui.theme.Body
import app.yapper.ui.theme.Heading
import app.yapper.ui.theme.Muted
import app.yapper.ui.theme.Panel
import app.yapper.ui.theme.Raised
import app.yapper.ui.yapperViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FriendsViewModel(private val repository: YapperRepository) : ViewModel() {
    val friends = repository.observeFriends().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _banner = MutableStateFlow<String?>(null)
    val banner = _banner.asStateFlow()

    fun clearBanner() {
        _banner.value = null
    }

    fun add(username: String) = act { repository.requestFriend(username) }
    fun accept(id: Long) = act { repository.acceptFriend(id) }
    fun remove(id: Long) = act { repository.declineOrCancel(id) }

    fun message(id: Long, onOpen: (Long) -> Unit) {
        viewModelScope.launch {
            when (val result = repository.openDm(id)) {
                is Outcome.Ok -> onOpen(result.value)
                is Outcome.Err -> _banner.value = result.message
            }
        }
    }

    private fun act(block: suspend () -> Outcome<Unit>) {
        viewModelScope.launch {
            val result = block()
            _banner.value = if (result is Outcome.Err) result.message else null
        }
    }
}

@Composable
fun FriendsScreen(onOpenChat: (Long) -> Unit, onOpenProfile: (Long) -> Unit) {
    val viewModel = yapperViewModel { FriendsViewModel(it) }
    val friends by viewModel.friends.collectAsStateWithLifecycle()
    val banner by viewModel.banner.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf("online") }
    var username by rememberSaveable { mutableStateOf("") }
    val shown = when (filter) {
        "online" -> friends.filter { it.accepted && it.user.status != Presence.OFFLINE }
        "all" -> friends.filter { it.accepted }
        "pending" -> friends.filter { !it.accepted }
        else -> emptyList()
    }

    Column(Modifier.fillMaxSize().background(Panel)) {
        ScreenHeader("Friends")
        Notice(banner, viewModel::clearBanner)
        Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip("Online", filter == "online") { filter = "online" }
            FilterChip("All", filter == "all") { filter = "all" }
            FilterChip("Pending", filter == "pending") { filter = "pending" }
            FilterChip("Add", filter == "add") { filter = "add" }
        }
        if (filter == "add") {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                YapperField(username, { username = it }, "Username")
                PrimaryButton("Send friend request") {
                    viewModel.add(username)
                    username = ""
                }
            }
        } else if (shown.isEmpty()) {
            EmptyHint(
                title = if (filter == "pending") "No requests" else "No one here",
                body = if (filter == "pending") "Incoming requests from people like Rio show up in this tab." else "Add someone by username. Demo people: nova, pixel, moss, juniper, rio.",
            )
        } else {
            LazyColumn {
                items(shown, key = { it.user.id }) { row ->
                    FriendLine(
                        row = row,
                        onOpen = { onOpenProfile(row.user.id) },
                        onMessage = { viewModel.message(row.user.id, onOpenChat) },
                        onAccept = { viewModel.accept(row.user.id) },
                        onRemove = { viewModel.remove(row.user.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (selected) Heading else Muted,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) Blurple else Raised)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun FriendLine(
    row: FriendRow,
    onOpen: () -> Unit,
    onMessage: () -> Unit,
    onAccept: () -> Unit,
    onRemove: () -> Unit,
) {
    app.yapper.ui.components.ClickRow(onClick = onOpen) {
        Avatar(row.user.displayName, Color(row.user.accentColor), 44.dp, row.user.status, Panel)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(row.user.displayName, color = Heading, fontWeight = FontWeight.SemiBold)
            val subtitle = when {
                row.incoming -> "Incoming request"
                row.outgoing -> "Request sent"
                row.user.customStatus.isNotBlank() -> row.user.customStatus
                else -> statusLabel(row.user.status)
            }
            Text(subtitle, color = Muted, fontSize = 13.sp, maxLines = 1)
        }
        when {
            row.incoming -> {
                TextButton(onClick = onAccept) { Text("Accept", color = Blurple) }
                TextButton(onClick = onRemove) { Text("Decline", color = Body) }
            }
            row.outgoing -> TextButton(onClick = onRemove) { Text("Cancel", color = Body) }
            row.accepted -> {
                IconButton(onClick = onMessage) { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Message", tint = Heading) }
            }
        }
    }
}
