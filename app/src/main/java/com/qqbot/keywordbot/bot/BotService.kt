package com.qqbot.keywordbot.bot

import com.qqbot.keywordbot.ai.AiProvider
import com.qqbot.keywordbot.ai.AiRequest
import com.qqbot.keywordbot.data.dao.KeywordReplyDao
import com.qqbot.keywordbot.data.dao.PersonaDao
import com.qqbot.keywordbot.data.dao.QueryLogDao
import com.qqbot.keywordbot.data.entity.QueryLog
import com.qqbot.keywordbot.data.entity.ReplyType
import com.qqbot.keywordbot.napcat.OneBotClient
import com.qqbot.keywordbot.napcat.model.OneBotEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 机器人核心服务。
 *
 * 职责：
 *  1. 订阅 OneBot 消息事件
 *  2. 按 ownerUserId 加载该用户的关键词规则
 *  3. 匹配关键词，命中则构建回复并发送
 *  4. 未命中则静默（不回复）
 *  5. 记录查询日志
 *  6. 若规则配置了 AI 回复，则调用 AiProvider
 */
@Singleton
class BotService @Inject constructor(
    private val oneBotClient: OneBotClient,
    private val keywordReplyDao: KeywordReplyDao,
    private val personaDao: PersonaDao,
    private val queryLogDao: QueryLogDao,
    private val keywordMatcher: KeywordMatcher,
    private val replyBuilder: ReplyBuilder,
    private val aiProvider: AiProvider
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // 当前激活的 AppUser id（管理界面切换用户时设置）
    @Volatile
    private var currentOwnerUserId: Long = 0L

    fun setCurrentUser(ownerUserId: Long) {
        currentOwnerUserId = ownerUserId
    }

    fun start() {
        oneBotClient.events
            .onEach { event -> handleEvent(event) }
            .launchIn(scope)
    }

    fun stop() {
        scope.cancel()
    }

    private suspend fun handleEvent(event: OneBotEvent) {
        // 只处理消息事件
        if (event.postType != "message") return

        val text = event.rawMessage
        val isGroup = event.messageType == "group"
        val userId = event.userId
        val groupId = event.groupId

        if (currentOwnerUserId == 0L) return  // 尚未选择用户

        // 🔒 加载当前用户的关键词规则（带 ownerUserId 过滤）
        val rules = keywordReplyDao.getEnabledByOwner(currentOwnerUserId)
        val matched = keywordMatcher.match(text, rules)

        if (matched == null) {
            // 未命中：静默，仅记录日志
            writeLog(QueryLog(userId = userId, groupId = groupId, question = text, success = false))
            return
        }

        // 命中：构建回复
        val aiText: String? = if (matched.replyType == ReplyType.AI && matched.personaId != null) {
            callAi(matched.personaId!!, text)
        } else null

        val segments = replyBuilder.build(
            rule = matched,
            atUserId = userId,
            isGroup = isGroup,
            aiText = aiText
        )

        // 发送回复
        val sent = if (isGroup && groupId != null) {
            oneBotClient.sendGroupMessage(groupId, segments)
        } else {
            oneBotClient.sendPrivateMessage(userId, segments)
        }

        writeLog(
            QueryLog(
                userId = userId,
                groupId = groupId,
                question = text,
                matchedId = matched.id,
                matchedKw = matched.keyword,
                success = sent
            )
        )
    }

    private suspend fun callAi(personaId: Long, question: String): String? {
        // 🔒 读取人设时必须带 ownerUserId，确保只能使用自己的人设
        val persona = personaDao.getById(personaId, currentOwnerUserId) ?: return null
        if (!persona.enabled) return null

        return runCatching {
            aiProvider.chat(
                AiRequest(
                    systemPrompt = persona.systemPrompt,
                    userMessage = question,
                    model = persona.model,
                    temperature = persona.temperature
                )
            )
        }.getOrNull()
    }

    private suspend fun writeLog(log: QueryLog) {
        runCatching { queryLogDao.insert(log) }
    }
}
