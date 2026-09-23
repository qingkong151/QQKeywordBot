package com.qqbot.keywordbot.bot

import com.qqbot.keywordbot.data.entity.KeywordReply
import com.qqbot.keywordbot.data.entity.ReplyType
import com.qqbot.keywordbot.napcat.model.MessageSegment
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 回复构建器。
 * 负责将 KeywordReply 规则 + 消息上下文 转换为 OneBot 消息段列表。
 *
 * 支持：
 *  - 纯文本回复
 *  - 图片回复
 *  - 文本+图片回复
 *  - @ 提问者（群聊时）
 */
@Singleton
class ReplyBuilder @Inject constructor() {

    /**
     * 构建回复消息段。
     *
     * @param rule 命中的关键词规则
     * @param atUserId 提问者 QQ 号，群聊时用于 @
     * @param isGroup 是否群聊
     * @param aiText 当 replyType == AI 时由 AI 生成的文本
     * @return OneBot 消息段列表
     */
    fun build(
        rule: KeywordReply,
        atUserId: Long,
        isGroup: Boolean,
        aiText: String? = null
    ): List<MessageSegment> {
        val segments = mutableListOf<MessageSegment>()

        // 群聊时 @ 提问者
        if (isGroup) {
            segments.add(atSegment(atUserId))
        }

        when (rule.replyType) {
            ReplyType.TEXT -> {
                rule.replyText?.takeIf { it.isNotBlank() }?.let {
                    segments.add(textSegment(it))
                }
            }
            ReplyType.IMAGE -> {
                rule.replyImage?.takeIf { it.isNotBlank() }?.let {
                    segments.add(imageSegment(it))
                }
            }
            ReplyType.TEXT_IMAGE -> {
                rule.replyText?.takeIf { it.isNotBlank() }?.let {
                    segments.add(textSegment(it))
                }
                rule.replyImage?.takeIf { it.isNotBlank() }?.let {
                    segments.add(imageSegment(it))
                }
            }
            ReplyType.AI -> {
                aiText?.takeIf { it.isNotBlank() }?.let {
                    segments.add(textSegment(it))
                }
                rule.replyImage?.takeIf { it.isNotBlank() }?.let {
                    segments.add(imageSegment(it))
                }
            }
        }

        return segments
    }

    private fun atSegment(userId: Long): MessageSegment =
        MessageSegment(type = "at", data = mapOf("qq" to userId.toString()))

    private fun textSegment(text: String): MessageSegment =
        MessageSegment(type = "text", data = mapOf("text" to text))

    private fun imageSegment(file: String): MessageSegment =
        MessageSegment(type = "image", data = mapOf("file" to file))
}
