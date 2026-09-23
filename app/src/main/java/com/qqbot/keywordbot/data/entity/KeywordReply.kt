package com.qqbot.keywordbot.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 匹配类型：精确匹配 / 包含匹配 / 正则匹配
 */
enum class MatchType { EXACT, CONTAINS, REGEX }

/**
 * 回复类型：纯文本 / 图片 / 文本+图片 / AI 生成
 */
enum class ReplyType { TEXT, IMAGE, TEXT_IMAGE, AI }

/**
 * 关键词回复规则。
 *
 * 🔒 关键约束：ownerUserId 标识数据归属。
 * 查询时必须带 ownerUserId 参数，禁止跨用户读取。
 */
@Entity(
    tableName = "keyword_reply",
    foreignKeys = [
        ForeignKey(
            entity = AppUser::class,
            parentColumns = ["id"],
            childColumns = ["ownerUserId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Persona::class,
            parentColumns = ["id"],
            childColumns = ["personaId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("ownerUserId"), Index("personaId")]
)
data class KeywordReply(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ownerUserId: Long,
    val keyword: String,
    val matchType: MatchType = MatchType.CONTAINS,
    val replyType: ReplyType = ReplyType.TEXT,
    val replyText: String? = null,
    val replyImage: String? = null,   // 本地图片路径或 URL
    val personaId: Long? = null,       // 当 replyType == AI 时使用的人设
    val enabled: Boolean = true
)
