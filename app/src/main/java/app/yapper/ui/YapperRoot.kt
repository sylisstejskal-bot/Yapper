package app.yapper.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.yapper.YapperApp
import app.yapper.data.ChannelKind
import app.yapper.data.PublicUser
import app.yapper.ui.auth.AuthScreen
import app.yapper.ui.chat.ChatScreen
import app.yapper.ui.components.Mark
import app.yapper.ui.dms.DmsScreen
import app.yapper.ui.friends.FriendsScreen
import app.yapper.ui.profile.ProfileScreen
import app.yapper.ui.server.MembersScreen
import app.yapper.ui.server.ServerScreen
import app.yapper.ui.servers.ServersScreen
import app.yapper.ui.theme.Blurple
import app.yapper.ui.theme.Chat
import app.yapper.ui.theme.Heading
import app.yapper.ui.theme.Ink
import app.yapper.ui.theme.Muted
import app.yapper.ui.voice.VoiceScreen

private val topRoutes = setOf("servers", "dms", "friends", "you")

@Composable
fun YapperRoot() {
    val app = LocalContext.current.applicationContext as YapperApp
    val ready by app.ready.collectAsStateWithLifecycle()
    val bootError by app.bootError.collectAsStateWithLifecycle()
    var user by remember { mutableStateOf<PublicUser?>(null) }
    var sessionLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(ready) {
        if (!ready) return@LaunchedEffect
        app.repository.observeSession().collect {
            user = it
            sessionLoaded = true
        }
    }

    when {
        bootError != null -> BootError(bootError!!)
        !ready || !sessionLoaded -> Splash()
        user == null -> AuthScreen()
        else -> Home(user!!)
    }
}

@Composable
private fun Splash() {
    Box(Modifier.fillMaxSize().background(Ink), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Mark("Y", Blurple, 72.dp, corner = 20.dp)
            Text("Yapper", color = Heading, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
        }
    }
}

@Composable
private fun BootError(message: String) {
    Column(Modifier.fillMaxSize().background(Ink).verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("Yapper couldn't open its database", color = Heading, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        Text(message, color = Muted, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun Home(user: PublicUser) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showBar = route in topRoutes
    Scaffold(
        containerColor = Chat,
        bottomBar = { if (showBar) YapperBar(route, nav) },
    ) { padding ->
        NavHost(nav, startDestination = "servers", modifier = Modifier.padding(padding)) {
            composable("servers") { ServersScreen(onOpen = { id -> nav.navigate("server/$id") }) }
            composable("dms") { DmsScreen(onOpen = { id -> nav.navigate("chat/$id") }) }
            composable("friends") {
                FriendsScreen(
                    onOpenChat = { id -> nav.navigate("chat/$id") },
                    onOpenProfile = { id -> nav.navigate("user/$id") },
                )
            }
            composable("you") { ProfileScreen(userId = null, onBack = null, onOpenChat = { id -> nav.navigate("chat/$id") }) }
            composable(
                "server/{serverId}",
                arguments = listOf(navArgument("serverId") { type = NavType.LongType }),
            ) { back ->
                val serverId = back.arguments?.getLong("serverId") ?: return@composable
                ServerScreen(
                    serverId = serverId,
                    onBack = { nav.popBackStack() },
                    onChannel = { channelId, kind ->
                        if (kind == ChannelKind.VOICE) nav.navigate("voice/$channelId")
                        else nav.navigate("chat/$channelId")
                    },
                    onMembers = { nav.navigate("members/$serverId") },
                )
            }
            composable(
                "members/{serverId}",
                arguments = listOf(navArgument("serverId") { type = NavType.LongType }),
            ) { back ->
                val serverId = back.arguments?.getLong("serverId") ?: return@composable
                MembersScreen(serverId, onBack = { nav.popBackStack() }, onOpenProfile = { id -> nav.navigate("user/$id") })
            }
            composable(
                "chat/{channelId}",
                arguments = listOf(navArgument("channelId") { type = NavType.LongType }),
            ) { back ->
                val channelId = back.arguments?.getLong("channelId") ?: return@composable
                ChatScreen(channelId, onBack = { nav.popBackStack() }, onOpenProfile = { id -> nav.navigate("user/$id") })
            }
            composable(
                "voice/{channelId}",
                arguments = listOf(navArgument("channelId") { type = NavType.LongType }),
            ) { back ->
                val channelId = back.arguments?.getLong("channelId") ?: return@composable
                VoiceScreen(channelId, onBack = { nav.popBackStack() })
            }
            composable(
                "user/{userId}",
                arguments = listOf(navArgument("userId") { type = NavType.LongType }),
            ) { back ->
                val userId = back.arguments?.getLong("userId") ?: return@composable
                ProfileScreen(
                    userId = userId,
                    onBack = { nav.popBackStack() },
                    onOpenChat = { id -> nav.navigate("chat/$id") },
                )
            }
        }
    }
}

@Composable
private fun YapperBar(current: String?, nav: NavHostController) {
    val app = LocalContext.current.applicationContext as YapperApp
    val servers by app.repository.observeServerUnread().collectAsStateWithLifecycle(0)
    val dms by app.repository.observeDmUnread().collectAsStateWithLifecycle(0)
    val pending by app.repository.observePendingCount().collectAsStateWithLifecycle(0)
    NavigationBar(containerColor = Ink) {
        BarItem("servers", "Servers", Icons.Filled.Tag, servers, current, nav)
        BarItem("dms", "Messages", Icons.Filled.Forum, dms, current, nav)
        BarItem("friends", "Friends", Icons.Filled.AlternateEmail, pending, current, nav)
        BarItem("you", "You", Icons.Filled.Person, 0, current, nav)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.BarItem(
    route: String,
    label: String,
    icon: ImageVector,
    badge: Int,
    current: String?,
    nav: NavHostController,
) {
    NavigationBarItem(
        selected = current == route,
        onClick = {
            nav.navigate(route) {
                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        },
        icon = {
            Box {
                Icon(icon, contentDescription = label)
                if (badge > 0) {
                    Text(
                        text = if (badge > 9) "9+" else badge.toString(),
                        color = Heading,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.TopEnd).padding(start = 14.dp),
                    )
                }
            }
        },
        label = { Text(label) },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = Heading,
            selectedTextColor = Heading,
            unselectedIconColor = Muted,
            unselectedTextColor = Muted,
            indicatorColor = Blurple,
        ),
    )
}
