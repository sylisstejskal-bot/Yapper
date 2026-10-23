package app.yapper.ui.server

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.yapper.data.DEFAULT_ROLE_COLOR
import app.yapper.data.MemberUi
import app.yapper.data.Outcome
import app.yapper.data.YapperRepository
import app.yapper.ui.components.Avatar
import app.yapper.ui.components.ClickRow
import app.yapper.ui.components.ConfirmDialog
import app.yapper.ui.components.Notice
import app.yapper.ui.components.ScreenHeader
import app.yapper.ui.components.statusLabel
import app.yapper.ui.theme.Body
import app.yapper.ui.theme.Danger
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

class MembersViewModel(private val repository: YapperRepository, private val serverId: Long) : ViewModel() {
    val members = repository.observeMembers(serverId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val ownerId = combine(repository.observeServer(serverId), repository.observeSession()) { server, me ->
        server?.ownerId to me?.id
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null to null)

    private val _banner = MutableStateFlow<String?>(null)
    val banner = _banner.asStateFlow()

    fun clearBanner() {
        _banner.value = null
    }

    fun kick(userId: Long) {
        viewModelScope.launch {
            val result = repository.kick(serverId, userId)
            if (result is Outcome.Err) _banner.value = result.message
        }
    }
}

@Composable
fun MembersScreen(serverId: Long, onBack: () -> Unit, onOpenProfile: (Long) -> Unit) {
    val viewModel = yapperViewModel(key = "members-$serverId") { MembersViewModel(it, serverId) }
    val members by viewModel.members.collectAsStateWithLifecycle()
    val ownership by viewModel.ownerId.collectAsStateWithLifecycle()
    val banner by viewModel.banner.collectAsStateWithLifecycle()
    var pendingKick by remember { mutableStateOf<MemberUi?>(null) }
    val (ownerId, myId) = ownership

    Column(Modifier.fillMaxSize().background(Panel)) {
        ScreenHeader("Members", "${members.size} people", onBack)
        Notice(banner, viewModel::clearBanner)
        LazyColumn {
            items(members, key = { it.userId }) { member ->
                val nameColor = if (member.roleColor == DEFAULT_ROLE_COLOR) Heading else Color(member.roleColor)
                ClickRow(onClick = { onOpenProfile(member.userId) }) {
                    Avatar(member.displayName, Color(member.accentColor), 40.dp, member.status, Panel)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(member.displayName, color = nameColor, fontWeight = FontWeight.SemiBold)
                        val detail = member.customStatus.ifBlank { member.roleName ?: statusLabel(member.status) }
                        Text(detail, color = Muted, fontSize = 13.sp, maxLines = 1)
                    }
                    if (ownerId != null && ownerId == myId && member.userId != myId) {
                        TextButton(onClick = { pendingKick = member }) { Text("Kick", color = Danger) }
                    }
                }
            }
        }
    }
    pendingKick?.let { member ->
        ConfirmDialog(
            title = "Kick ${member.displayName}?",
            body = "They lose the server until someone invites them back. Their old messages stay.",
            confirm = "Kick",
            danger = true,
            onConfirm = {
                viewModel.kick(member.userId)
                pendingKick = null
            },
            onDismiss = { pendingKick = null },
        )
    }
}
