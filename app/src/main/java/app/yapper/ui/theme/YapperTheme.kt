package app.yapper.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Ink = Color(0xFF1E1F22)
val Panel = Color(0xFF2B2D31)
val Chat = Color(0xFF313338)
val Raised = Color(0xFF383A40)
val Line = Color(0xFF3F4147)
val Blurple = Color(0xFF5865F2)
val Body = Color(0xFFDBDEE1)
val Muted = Color(0xFF949BA4)
val Heading = Color(0xFFF2F3F5)
val Online = Color(0xFF23A559)
val Idle = Color(0xFFF0B232)
val Dnd = Color(0xFFF23F43)
val Offline = Color(0xFF80848E)
val Danger = Color(0xFFF23F43)
val LinkBlue = Color(0xFF00A8FC)

private val scheme = darkColorScheme(
    primary = Blurple,
    onPrimary = Color.White,
    secondary = Blurple,
    background = Chat,
    surface = Panel,
    onBackground = Body,
    onSurface = Body,
    error = Danger,
    outline = Line,
)

@Composable
fun YapperTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
}
