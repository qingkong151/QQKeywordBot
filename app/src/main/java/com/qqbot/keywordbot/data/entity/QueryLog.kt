package com.qqbot.keywordbot.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 查询日志：记录每次消息触发情况。
 */
@Entity(tableName = "query_log")
data class QueryLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ts: Long = System.currentTimeMillis(),
    val userId: Long,          // 提问者 QQ 号
    val groupId: Long? = null, // 群号（私聊为 null）
    val question: String,
    val matchedId: Long? = null,    // 命中的 KeywordReply.id
    val matchedKw: String? = null,  // 命中的关键词
    val success: Boolean = false
)
