package app.yapper.ui.dms

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.yapper.data.FriendRow
import app.yapper.data.InboxRow
import app.yapper.data.Outcome
import app.yapper.data.YapperRepository
import app.yapper.ui.components.Avatar
import app.yapper.ui.components.ClickRow
import app.yapper.ui.components.EmptyHint
import app.yapper.ui.components.Notice
import app.yapper.ui.components.ScreenHeader
import app.yapper.ui.components.UnreadDot
import app.yapper.ui.components.YapperField
import app.yapper.ui.theme.Blurple
import app.yapper.ui.theme.Heading
import app.yapper.ui.theme.Muted
import app.yapper.ui.theme.Panel
import app.yapper.ui.yapperViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DmsViewModel(private val repository: YapperRepository) : ViewModel() {
    val inbox = repository.observeInbox().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val friends = repository.observeFriends().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _banner = MutableStateFlow<String?>(null)
    val banner = _banner.asStateFlow()

    fun clearBanner() {
        _banner.value = null
    }

    fun createGroup(name: String, ids: List<Long>, onOpen: (Long) -> Unit) {
        viewModelScope.launch {
            when (val result = repository.createGroup(name, ids)) {
                is Outcome.Ok -> onOpen(result.value)
                is Outcome.Err -> _banner.value = result.message
            }
        }
    }
}

@Composable
fun DmsScreen(onOpen: (Long) -> Unit) {
    val viewModel = yapperViewModel { DmsViewModel(it) }
    val inbox by viewModel.inbox.collectAsStateWithLifecycle()
    val friends by viewModel.friends.collectAsStateWithLifecycle()
    val banner by viewModel.banner.collectAsStateWithLifecycle()
    var showGroup by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(Panel)) {
        ScreenHeader("Direct messages", "Same features as servers, still free") {
            IconButton(onClick = { showGroup = true }) {
                Icon(Icons.Filled.Add, contentDescription = "New group", tint = Heading)
            }
        }
        Notice(banner, viewModel::clearBanner)
        if (inbox.isEmpty()) {
            EmptyHint("No conversations yet", "Open a friend and tap Message, or start a group from the plus button.")
        } else {
            LazyColumn {
                items(inbox, key = { it.channelId }) { row ->
                    InboxLine(row) { onOpen(row.channelId) }
                }
            }
        }
    }
    if (showGroup) {
        GroupDialog(
            friends = friends.filter { it.accepted },
            onDismiss = { showGroup = false },
            onCreate = { name, ids ->
                showGroup = false
                viewModel.createGroup(name, ids, onOpen)
            },
        )
    }
}

@Composable
private fun InboxLine(row: InboxRow, onClick: () -> Unit) {
    ClickRow(onClick) {
        Avatar(row.letter, Color(row.accent), 48.dp, row.status, Panel)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(row.title, color = if (row.unread > 0) Heading else BodyOrHeading(row.unread), fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(row.subtitle, color = Muted, fontSize = 13.sp, maxLines = 1)
        }
        UnreadDot(row.unread)
    }
}

@Composable
private fun BodyOrHeading(unread: Int): Color = if (unread > 0) Heading else app.yapper.ui.theme.Body

@Composable
private fun GroupDialog(friends: List<FriendRow>, onDismiss: () -> Unit, onCreate: (String, List<Long>) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        title = { Text("New group", color = Heading) },
        text = {
            Column {
                YapperField(name, { name = it }, "Group name")
                Text("Pick at least two friends", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                friends.forEach { friend ->
                    val on = friend.user.id in selected
                    TextButton(onClick = {
                        selected = if (on) selected - friend.user.id else selected + friend.user.id
                    }) {
                        Text(if (on) "✓ ${friend.user.displayName}" else friend.user.displayName, color = if (on) Blurple else Heading)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(name, selected.toList()) }) { Text("Create", color = Blurple) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Muted) } },
    )
}
