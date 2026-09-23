package com.qqbot.keywordbot.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * AI 人设（Persona）。
 *
 * 🔒 关键约束：ownerUserId 标识数据归属。
 * 查询时必须带 ownerUserId 参数，禁止跨用户读取。
 */
@Entity(
    tableName = "persona",
    foreignKeys = [
        ForeignKey(
            entity = AppUser::class,
            parentColumns = ["id"],
            childColumns = ["ownerUserId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("ownerUserId")]
)
data class Persona(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ownerUserId: Long,
    val name: String,
    val systemPrompt: String,
    val model: String = "gpt-4o-mini",
    val temperature: Float = 0.7f,
    val enabled: Boolean = true
)
