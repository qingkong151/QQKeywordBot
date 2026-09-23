package com.qqbot.keywordbot.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 底部导航目的地
 */
sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object Home : Screen("home", "首页", Icons.Default.Home)
    data object Keywords : Screen("keywords", "关键词", Icons.Default.Chat)
    data object Personas : Screen("personas", "人设", Icons.Default.Person)
    data object Logs : Screen("logs", "日志", Icons.Default.ReceiptLong)
}

val bottomNavItems = listOf(Screen.Home, Screen.Keywords, Screen.Personas, Screen.Logs)
