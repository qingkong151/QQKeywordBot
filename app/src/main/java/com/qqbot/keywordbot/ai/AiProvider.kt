package com.qqbot.keywordbot.ai

/**
 * AI 请求参数。
 *
 * 注意：这里不携带 ownerUserId，因为数据隔离在更上层完成。
 * BotService 在调用 AiProvider 之前，已经通过 PersonaDao.getById(id, ownerUserId)
 * 确保只加载了当前用户的人设，再把 systemPrompt 传进来。
 */
data class AiRequest(
    val systemPrompt: String,
    val userMessage: String,
    val model: String = "gpt-4o-mini",
    val temperature: Float = 0.7f
)

data class AiResponse(
    val content: String
)

/**
 * AI 提供者接口。
 *
 * 🔒 数据隔离说明：
 * 人设(Persona)的 ownerUserId 隔离由数据层（PersonaDao）保证，
 * AiProvider 本身是无状态的，只负责根据传入的 systemPrompt 生成回复。
 * 这样即使更换 AI 实现，也不会破坏数据隔离。
 */
interface AiProvider {
    suspend fun chat(request: AiRequest): String?
}
