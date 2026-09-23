package com.qqbot.keywordbot.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * App 内部用户（不是 QQ 用户，是使用本 App 管理关键词的人）。
 * 每个 AppUser 拥有自己私有的 Persona / KeywordReply，互不可见。
 */
@Entity(tableName = "app_user")
data class AppUser(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)
