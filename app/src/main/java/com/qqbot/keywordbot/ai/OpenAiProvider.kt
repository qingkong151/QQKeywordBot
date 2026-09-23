package com.qqbot.keywordbot.ai

import com.qqbot.keywordbot.data.SettingsRepository
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OpenAI（兼容协议）AI 提供者实现。
 *
 * API Key 和 Base URL 从 DataStore 读取，用户在设置页配置。
 */
@Singleton
class OpenAiProvider @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val settingsRepository: SettingsRepository
) : AiProvider {

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val requestAdapter = moshi.adapter(OpenAiChatRequest::class.java)
    private val responseAdapter = moshi.adapter(OpenAiChatResponse::class.java)

    override suspend fun chat(request: AiRequest): String? = withContext(Dispatchers.IO) {
        val apiKey = settingsRepository.openAiApiKey.first()
        val baseUrl = settingsRepository.openAiBaseUrl.first().trimEnd('/')

        if (apiKey.isBlank()) {
            return@withContext null  // 未配置 API Key，返回 null
        }

        val payload = OpenAiChatRequest(
            model = request.model,
            messages = listOf(
                ChatMessage(role = "system", content = request.systemPrompt),
                ChatMessage(role = "user", content = request.userMessage)
            ),
            temperature = request.temperature.toDouble()
        )

        val httpRequest = Request.Builder()
            .url("$baseUrl/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(requestAdapter.toJson(payload).toRequestBody(JSON_MEDIA_TYPE))
            .build()

        runCatching {
            okHttpClient.newCall(httpRequest).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string() ?: return@use null
                val parsed = responseAdapter.fromJson(body)
                parsed?.choices?.firstOrNull()?.message?.content
            }
        }.getOrNull()
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

@JsonClass(generateAdapter = true)
data class OpenAiChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.7
)

@JsonClass(generateAdapter = true)
data class ChatMessage(
    val role: String,
    val content: String
)

@JsonClass(generateAdapter = true)
data class OpenAiChatResponse(
    val choices: List<Choice> = emptyList()
) {
    @JsonClass(generateAdapter = true)
    data class Choice(
        val message: ChatMessage
    )
}
