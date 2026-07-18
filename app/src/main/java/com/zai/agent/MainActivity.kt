package com.zai.agent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.zai.agent.ui.screens.ChatListScreen
import com.zai.agent.ui.screens.ChatScreen
import com.zai.agent.ui.screens.LoginScreen
import com.zai.agent.ui.theme.ZaiAgentTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        )
        setContent {
            ZaiAgentTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavGraph()
                }
            }
        }
    }
}

@Composable
private fun AppNavGraph() {
    val navController = rememberNavController()
    val initial = if (ZaiApplication.instance.sessionStore.isLoggedIn()) Routes.CHATS else Routes.LOGIN

    NavHost(
        navController = navController,
        startDestination = initial,
        enterTransition = { fadeIn(animationSpec = tween(200)) + scaleIn(initialScale = 0.98f) },
        exitTransition = { fadeOut(animationSpec = tween(150)) + scaleOut(targetScale = 1.02f) },
        popEnterTransition = { fadeIn(animationSpec = tween(200)) },
        popExitTransition = { fadeOut(animationSpec = tween(150)) }
    ) {
        composable(Routes.LOGIN) {
            LoginScreen(
                onLoggedIn = {
                    navController.navigate(Routes.CHATS) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.CHATS) {
            ChatListScreen(
                onOpenChat = { id ->
                    navController.navigate(Routes.chat(id))
                }
            )
        }
        composable(
            route = Routes.CHAT,
            arguments = listOf(navArgument("conversationId") { type = NavType.StringType })
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("conversationId") ?: return@composable
            ChatScreen(
                conversationId = id,
                onBack = { navController.popBackStack() }
            )
        }
    }
}

object Routes {
    const val LOGIN = "login"
    const val CHATS = "chats"
    const val CHAT = "chat/{conversationId}"
    fun chat(id: String) = "chat/$id"
}
