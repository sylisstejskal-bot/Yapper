package app.yapper.ui.servers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.yapper.data.Outcome
import app.yapper.data.ServerSummary
import app.yapper.data.YapperRepository
import app.yapper.ui.components.EmptyHint
import app.yapper.ui.components.Mark
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

class ServersViewModel(private val repository: YapperRepository) : ViewModel() {
    val servers = repository.observeServers().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _banner = MutableStateFlow<String?>(null)
    val banner = _banner.asStateFlow()

    fun clearBanner() {
        _banner.value = null
    }

    fun create(name: String, onOpen: (Long) -> Unit) = run("Couldn't create that server", onOpen) {
        repository.createServer(name)
    }

    fun join(code: String, onOpen: (Long) -> Unit) = run("Couldn't join", onOpen) {
        repository.joinServer(code)
    }

    private fun run(fallback: String, onOpen: (Long) -> Unit, block: suspend () -> Outcome<Long>) {
        viewModelScope.launch {
            when (val result = block()) {
                is Outcome.Ok -> onOpen(result.value)
                is Outcome.Err -> _banner.value = result.message.ifBlank { fallback }
            }
        }
    }
}

@Composable
fun ServersScreen(onOpen: (Long) -> Unit) {
    val viewModel = yapperViewModel { ServersViewModel(it) }
    val servers by viewModel.servers.collectAsStateWithLifecycle()
    val banner by viewModel.banner.collectAsStateWithLifecycle()
    var showCreate by rememberSaveable { mutableStateOf(false) }
    var showJoin by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(Panel)) {
        ScreenHeader(title = "Servers", subtitle = "Everything in here is free") {
            IconButton(onClick = { showJoin = true }) {
                Text("Join", color = Blurple, fontWeight = FontWeight.Bold)
            }
            IconButton(onClick = { showCreate = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Create server", tint = Heading)
            }
        }
        Notice(banner, viewModel::clearBanner)
        if (servers.isEmpty()) {
            EmptyHint(
                title = "No servers yet",
                body = "Create one, or join with an invite. The seeded ones are yapper, owls, and reels.",
            )
        } else {
            LazyColumn {
                items(servers, key = { it.server.id }) { summary ->
                    ServerRow(summary) { onOpen(summary.server.id) }
                }
            }
        }
    }

    if (showCreate) {
        NameDialog(
            title = "Create a server",
            label = "Server name",
            confirm = "Create",
            onDismiss = { showCreate = false },
            onConfirm = { name ->
                showCreate = false
                viewModel.create(name, onOpen)
            },
        )
    }
    if (showJoin) {
        NameDialog(
            title = "Join with an invite",
            label = "Invite code",
            confirm = "Join",
            onDismiss = { showJoin = false },
            onConfirm = { code ->
                showJoin = false
                viewModel.join(code, onOpen)
            },
        )
    }
}

@Composable
private fun ServerRow(summary: ServerSummary, onClick: () -> Unit) {
    app.yapper.ui.components.ClickRow(onClick = onClick) {
        Mark(summary.server.iconLetter, androidx.compose.ui.graphics.Color(summary.server.accentColor), 52.dp, corner = 16.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(summary.server.name, color = Heading, fontWeight = FontWeight.SemiBold)
            val subtitle = if (summary.unread > 0) "${summary.unread} unread" else summary.server.description.ifBlank { "No description" }
            Text(subtitle, color = Muted, fontSize = 13.sp, maxLines = 1)
        }
        UnreadDot(summary.unread)
    }
}

@Composable
private fun NameDialog(
    title: String,
    label: String,
    confirm: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        title = { Text(title, color = Heading) },
        text = { YapperField(value, { value = it }, label) },
        confirmButton = { TextButton(onClick = { onConfirm(value) }) { Text(confirm, color = Blurple) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Muted) } },
    )
}
