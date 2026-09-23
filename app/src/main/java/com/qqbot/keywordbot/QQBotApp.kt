package com.qqbot.keywordbot

import android.app.Application
import com.qqbot.keywordbot.bot.BotService
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class QQBotApp : Application() {

    @Inject
    lateinit var botService: BotService

    override fun onCreate() {
        super.onCreate()
        // 设置当前用户（默认用户 id=1，由数据库初始化时创建）
        botService.setCurrentUser(1L)
        // 启动机器人消息路由（OneBot WS 需在 NapCat 启动后连接）
        botService.start()
    }
}
