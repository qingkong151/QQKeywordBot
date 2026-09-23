package com.qqbot.keywordbot.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.qqbot.keywordbot.ui.navigation.AppNavGraph
import com.qqbot.keywordbot.ui.theme.QQKeywordBotTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            QQKeywordBotTheme {
                AppNavGraph()
            }
        }
    }
}
