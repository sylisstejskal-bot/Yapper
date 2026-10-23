package app.yapper.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.yapper.data.DemoAccounts
import app.yapper.data.Outcome
import app.yapper.data.YapperRepository
import app.yapper.ui.components.Mark
import app.yapper.ui.components.Notice
import app.yapper.ui.components.PrimaryButton
import app.yapper.ui.components.YapperField
import app.yapper.ui.theme.Blurple
import app.yapper.ui.theme.Heading
import app.yapper.ui.theme.Ink
import app.yapper.ui.theme.Muted
import app.yapper.ui.theme.Raised
import app.yapper.ui.yapperViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel(private val repository: YapperRepository) : ViewModel() {
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()

    fun clearError() {
        _error.value = null
    }

    fun login(username: String, password: String) = submit { repository.login(username, password) }

    fun register(username: String, displayName: String, password: String) = submit {
        repository.register(username, displayName, password)
    }

    private fun submit(block: suspend () -> Outcome<Unit>) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            val result = block()
            if (result is Outcome.Err) _error.value = result.message
            _busy.value = false
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AuthScreen() {
    val viewModel = yapperViewModel { AuthViewModel(it) }
    val error by viewModel.error.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    var creating by rememberSaveable { mutableStateOf(false) }
    var username by rememberSaveable { mutableStateOf("") }
    var displayName by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Mark("Y", Blurple, 64.dp, corner = 18.dp)
        Text("Yapper", color = Heading, fontSize = 36.sp, fontWeight = FontWeight.Bold)
        Text("Servers, channels, and DMs. All of it free.", color = Muted, fontSize = 16.sp)
        Spacer(Modifier.height(8.dp))
        Notice(error, onDismiss = viewModel::clearError)
        if (!creating) {
            Text("Jump into a demo account", color = Muted, fontSize = 13.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DemoAccounts.usernames.forEach { name ->
                    DemoChip(name) { viewModel.login(name, DemoAccounts.PASSWORD) }
                }
            }
            Text("Password for every demo account is ${DemoAccounts.PASSWORD}.", color = Muted, fontSize = 12.sp)
        }
        YapperField(username, { username = it }, "Username")
        if (creating) YapperField(displayName, { displayName = it }, "Display name")
        YapperField(
            value = password,
            onValueChange = { password = it },
            label = "Password",
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        PrimaryButton(
            text = when {
                busy && creating -> "Creating…"
                busy -> "Logging in…"
                creating -> "Create account"
                else -> "Log in"
            },
            enabled = !busy,
            onClick = {
                if (creating) viewModel.register(username, displayName, password)
                else viewModel.login(username, password)
            },
        )
        TextButton(onClick = {
            creating = !creating
            viewModel.clearError()
        }) {
            Text(if (creating) "Have an account? Log in" else "New here? Create an account", color = Blurple)
        }
    }
}

@Composable
private fun DemoChip(name: String, onClick: () -> Unit) {
    Text(
        text = name,
        color = Heading,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Raised)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        fontWeight = FontWeight.SemiBold,
    )
}
