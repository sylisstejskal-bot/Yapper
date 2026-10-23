@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package app.yapper.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.yapper.data.BLURPLE
import app.yapper.data.DatabaseReport
import app.yapper.data.Outcome
import app.yapper.data.Presence
import app.yapper.data.PublicUser
import app.yapper.data.Relation
import app.yapper.data.YapperRepository
import app.yapper.ui.components.Avatar
import app.yapper.ui.components.EmptyHint
import app.yapper.ui.components.Notice
import app.yapper.ui.components.PrimaryButton
import app.yapper.ui.components.ScreenHeader
import app.yapper.ui.components.YapperField
import app.yapper.ui.components.statusLabel
import app.yapper.ui.formatMonth
import app.yapper.ui.theme.Body
import app.yapper.ui.theme.Blurple
import app.yapper.ui.theme.Chat
import app.yapper.ui.theme.Heading
import app.yapper.ui.theme.Muted
import app.yapper.ui.theme.Panel
import app.yapper.ui.theme.Raised
import app.yapper.ui.yapperViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val palette = listOf(
    BLURPLE,
    0xFF57F287,
    0xFFFEE75C,
    0xFFED4245,
    0xFFEB459E,
    0xFF00A8FC,
    0xFFF0B232,
    0xFF9B59B6,
    0xFFE67E22,
    0xFF1ABC9C,
)

class ProfileViewModel(private val repository: YapperRepository, private val userId: Long?) : ViewModel() {
    val me = repository.observeSession().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val profile = repository.observeSession().flatMapLatest { session ->
        val id = userId ?: session?.id
        if (id == null) flowOf(null) else repository.observeUser(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val relation = repository.observeSession().flatMapLatest { session ->
        if (session == null || userId == null || userId == session.id) flowOf(Relation.NONE)
        else repository.observeRelation(userId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Relation.NONE)

    private val _banner = MutableStateFlow<String?>(null)
    val banner = _banner.asStateFlow()
    private val _report = MutableStateFlow<DatabaseReport?>(null)
    val report = _report.asStateFlow()

    init {
        viewModelScope.launch { _report.value = repository.databaseReport() }
    }

    fun clearBanner() {
        _banner.value = null
    }

    fun save(displayName: String, about: String, customStatus: String, status: String, accent: Long) {
        viewModelScope.launch {
            val result = repository.updateProfile(displayName, about, customStatus, status, accent)
            _banner.value = if (result is Outcome.Err) result.message else "Profile saved"
        }
    }

    fun changePassword(current: String, next: String) {
        viewModelScope.launch {
            val result = repository.changePassword(current, next)
            _banner.value = if (result is Outcome.Err) result.message else "Password updated"
        }
    }

    fun addFriend() {
        val name = profile.value?.username ?: return
        viewModelScope.launch {
            val result = repository.requestFriend(name)
            if (result is Outcome.Err) _banner.value = result.message
        }
    }

    fun accept() {
        val id = userId ?: return
        act { repository.acceptFriend(id) }
    }

    fun remove() {
        val id = userId ?: return
        act { repository.declineOrCancel(id) }
    }

    fun message(onOpen: (Long) -> Unit) {
        val id = userId ?: return
        viewModelScope.launch {
            when (val result = repository.openDm(id)) {
                is Outcome.Ok -> onOpen(result.value)
                is Outcome.Err -> _banner.value = result.message
            }
        }
    }

    fun logout() {
        repository.logout()
    }

    private fun act(block: suspend () -> Outcome<Unit>) {
        viewModelScope.launch {
            val result = block()
            if (result is Outcome.Err) _banner.value = result.message
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(userId: Long?, onBack: (() -> Unit)?, onOpenChat: (Long) -> Unit) {
    val viewModel = yapperViewModel(key = "profile-${userId ?: "me"}") { ProfileViewModel(it, userId) }
    val me by viewModel.me.collectAsStateWithLifecycle()
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val relation by viewModel.relation.collectAsStateWithLifecycle()
    val banner by viewModel.banner.collectAsStateWithLifecycle()
    val report by viewModel.report.collectAsStateWithLifecycle()
    val person = profile
    val mine = person != null && person.id == me?.id

    Column(
        Modifier
            .fillMaxSize()
            .background(Chat)
            .imePadding()
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader(if (mine || userId == null) "You" else person?.displayName ?: "Profile", onBack = onBack) {
            if (mine) {
                TextButton(onClick = viewModel::logout) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Log out", tint = Heading)
                    Text(" Log out", color = Heading)
                }
            }
        }
        Notice(banner, viewModel::clearBanner)
        if (person == null) {
            EmptyHint("No profile", "This account isn't on the phone.")
            return@Column
        }
        ProfileCard(person)
        if (mine) {
            Editor(person, viewModel::save)
            PasswordBlock(viewModel::changePassword)
            Perks()
            report?.let { DatabaseCard(it) }
            Text(
                "Demo accounts share this database. Log out and open nova, pixel, moss, juniper, or rio. Password is yapper.",
                color = Muted,
                modifier = Modifier.padding(16.dp),
            )
        } else {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (relation) {
                    Relation.NONE -> TextButton(onClick = viewModel::addFriend) { Text("Add friend", color = Blurple) }
                    Relation.OUTGOING -> TextButton(onClick = viewModel::remove) { Text("Cancel request", color = Body) }
                    Relation.INCOMING -> {
                        TextButton(onClick = viewModel::accept) { Text("Accept", color = Blurple) }
                        TextButton(onClick = viewModel::remove) { Text("Decline", color = Body) }
                    }
                    Relation.FRIENDS -> TextButton(onClick = viewModel::remove) { Text("Remove friend", color = Body) }
                }
                TextButton(onClick = { viewModel.message(onOpenChat) }) { Text("Message", color = Blurple) }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ProfileCard(person: PublicUser) {
    val accent = Color(person.accentColor)
    Column {
        Box(Modifier.fillMaxWidth().height(108.dp)) {
            Box(Modifier.fillMaxWidth().height(84.dp).background(accent))
            Box(Modifier.padding(start = 16.dp, top = 48.dp)) {
                Avatar(person.displayName, accent, 72.dp, person.status, Chat)
            }
        }
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(person.displayName, color = Heading, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("@${person.username}", color = Muted)
            Text(statusLabel(person.status), color = Body, fontSize = 13.sp)
            if (person.customStatus.isNotBlank()) {
                Text(person.customStatus, color = Heading, modifier = Modifier.padding(top = 6.dp))
            }
            if (person.about.isNotBlank()) {
                Text(
                    person.about,
                    color = Body,
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Panel)
                        .padding(12.dp),
                )
            }
            Text(
                "Member since ${formatMonth(person.createdAt)}",
                color = Muted,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Editor(person: PublicUser, onSave: (String, String, String, String, Long) -> Unit) {
    var displayName by remember(person.id) { mutableStateOf(person.displayName) }
    var about by remember(person.id) { mutableStateOf(person.about) }
    var customStatus by remember(person.id) { mutableStateOf(person.customStatus) }
    var status by remember(person.id) { mutableStateOf(person.status) }
    var accent by remember(person.id) { mutableStateOf(person.accentColor) }
    LaunchedEffect(person.displayName, person.about, person.customStatus, person.status, person.accentColor) {
        displayName = person.displayName
        about = person.about
        customStatus = person.customStatus
        status = person.status
        accent = person.accentColor
    }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Profile", color = Heading, fontWeight = FontWeight.Bold)
        Text("Username stays @${person.username} so mentions keep working.", color = Muted, fontSize = 12.sp)
        YapperField(displayName, { displayName = it }, "Display name")
        YapperField(customStatus, { customStatus = it }, "Custom status")
        YapperField(about, { about = it }, "About me", singleLine = false, minLines = 3)
        Text("Status", color = Muted, fontSize = 12.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Presence.all.forEach { option ->
                val selected = status == option
                Text(
                    statusLabel(option),
                    color = if (selected) Heading else Muted,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (selected) Blurple else Raised)
                        .clickable { status = option }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
        Text("Profile color", color = Muted, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            palette.forEach { color ->
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(color))
                        .border(2.dp, if (accent == color) Heading else Color.Transparent, CircleShape)
                        .clickable { accent = color },
                )
            }
        }
        PrimaryButton("Save profile") { onSave(displayName, about, customStatus, status, accent) }
    }
}

@Composable
private fun PasswordBlock(onChange: (String, String) -> Unit) {
    var current by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Password", color = Heading, fontWeight = FontWeight.Bold)
        YapperField(current, { current = it }, "Current password", visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions())
        YapperField(next, { next = it }, "New password", visualTransformation = PasswordVisualTransformation())
        PrimaryButton("Update password") {
            onChange(current, next)
            current = ""
            next = ""
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Perks() {
    Column(Modifier.padding(16.dp)) {
        Text("No paid tier", color = Heading, fontWeight = FontWeight.Bold)
        Text("If Yapper has it, you can use it.", color = Muted, modifier = Modifier.padding(bottom = 8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Custom colors", "Custom status", "Role colors", "Invite codes", "8 MB attachments", "Reactions", "Voice lounges", "No ads").forEach { perk ->
                Text(perk, color = Heading, modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(Raised).padding(horizontal = 10.dp, vertical = 6.dp), fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun DatabaseCard(report: DatabaseReport) {
    Column(Modifier.padding(16.dp).clip(RoundedCornerShape(12.dp)).background(Panel).padding(14.dp)) {
        Text("Database on this phone", color = Heading, fontWeight = FontWeight.Bold)
        Text(
            "${report.users} users · ${report.servers} servers · ${report.channels} channels · ${report.messages} messages · ${report.friendships} friendships · ${report.attachments} attachments",
            color = Body,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(report.tables.joinToString("  "), color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        Text(
            "Good client schema. Not a multiplayer server: another phone cannot see this data.",
            color = Muted,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
