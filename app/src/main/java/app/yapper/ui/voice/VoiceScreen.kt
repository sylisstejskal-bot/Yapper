package app.yapper.ui.voice

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.yapper.data.Outcome
import app.yapper.data.VoicePresence
import app.yapper.data.YapperRepository
import app.yapper.ui.components.Avatar
import app.yapper.ui.components.Notice
import app.yapper.ui.components.PrimaryButton
import app.yapper.ui.components.ScreenHeader
import app.yapper.ui.theme.Blurple
import app.yapper.ui.theme.Danger
import app.yapper.ui.theme.Heading
import app.yapper.ui.theme.Ink
import app.yapper.ui.theme.Muted
import app.yapper.ui.theme.Raised
import app.yapper.ui.yapperViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class VoiceViewModel(private val repository: YapperRepository, private val channelId: Long) : ViewModel() {
    val people = repository.observeVoice(channelId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val me = repository.observeSession().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val title = repository.observeChannelName(channelId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "Voice")
    private val _banner = MutableStateFlow<String?>(null)
    val banner = _banner.asStateFlow()

    fun join() {
        viewModelScope.launch {
            val result = repository.joinVoice(channelId)
            if (result is Outcome.Err) _banner.value = result.message
        }
    }

    fun leave() {
        viewModelScope.launch { repository.leaveVoice(channelId) }
    }

    fun mute() {
        viewModelScope.launch { repository.toggleMute(channelId) }
    }

    fun deafen() {
        viewModelScope.launch { repository.toggleDeafen(channelId) }
    }

    fun clearBanner() {
        _banner.value = null
    }
}

@Composable
fun VoiceScreen(channelId: Long, onBack: () -> Unit) {
    val viewModel = yapperViewModel(key = "voice-$channelId") { VoiceViewModel(it, channelId) }
    val people by viewModel.people.collectAsStateWithLifecycle()
    val me by viewModel.me.collectAsStateWithLifecycle()
    val title by viewModel.title.collectAsStateWithLifecycle()
    val banner by viewModel.banner.collectAsStateWithLifecycle()
    val mine = people.firstOrNull { it.userId == me?.id }

    Column(Modifier.fillMaxSize().background(Ink)) {
        ScreenHeader(title, "Voice lounge", onBack)
        Notice(banner, viewModel::clearBanner)
        Text(
            "This lounge tracks who is sitting here. It does not open the microphone. Mute and deafen are status, so a real call can be added later without changing the database.",
            color = Muted,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            textAlign = TextAlign.Center,
        )
        Column(
            modifier = Modifier.weight(1f).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (people.isEmpty()) {
                Text("Nobody is in here yet.", color = Heading, modifier = Modifier.padding(top = 24.dp))
            }
            people.forEach { person -> PersonTile(person) }
        }
        if (mine == null) {
            PrimaryButton("Join lounge", modifier = Modifier.padding(16.dp)) { viewModel.join() }
        } else {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundAction(
                    icon = if (mine.selfMuted || mine.selfDeafened) Icons.Filled.MicOff else Icons.Filled.Mic,
                    label = if (mine.selfMuted) "Unmute" else "Mute",
                    on = mine.selfMuted,
                    onClick = viewModel::mute,
                )
                RoundAction(
                    icon = if (mine.selfDeafened) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                    label = if (mine.selfDeafened) "Undeafen" else "Deafen",
                    on = mine.selfDeafened,
                    onClick = viewModel::deafen,
                )
                RoundAction(Icons.Filled.CallEnd, "Leave", on = true, danger = true, onClick = viewModel::leave)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun PersonTile(person: VoicePresence) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Avatar(person.displayName, Color(person.accentColor), 72.dp, person.status, Ink)
        Spacer(Modifier.height(8.dp))
        Text(person.displayName, color = Heading, fontWeight = FontWeight.SemiBold)
        val flags = buildList {
            if (person.selfDeafened) add("deafened")
            else if (person.selfMuted) add("muted")
        }
        if (flags.isNotEmpty()) Text(flags.joinToString(), color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun RoundAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    on: Boolean,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        androidx.compose.material3.IconButton(
            onClick = onClick,
            modifier = Modifier
                .clip(CircleShape)
                .background(when {
                    danger -> Danger
                    on -> Raised
                    else -> Blurple
                }),
        ) {
            Icon(icon, contentDescription = label, tint = Color.White)
        }
        Text(label, color = Muted, fontSize = 12.sp)
    }
}
