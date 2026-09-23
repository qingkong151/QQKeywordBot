package com.qqbot.keywordbot.ui.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qqbot.keywordbot.bot.BotService
import com.qqbot.keywordbot.data.dao.KeywordReplyDao
import com.qqbot.keywordbot.data.dao.PersonaDao
import com.qqbot.keywordbot.data.entity.KeywordReply
import com.qqbot.keywordbot.data.entity.MatchType
import com.qqbot.keywordbot.data.entity.Persona
import com.qqbot.keywordbot.data.entity.ReplyType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class KeywordViewModel @Inject constructor(
    private val keywordReplyDao: KeywordReplyDao,
    private val personaDao: PersonaDao,
    private val botService: BotService
) : ViewModel() {

    // 当前激活用户（简化：默认 id=1，实际应从用户选择中获取）
    private val currentUserId = MutableStateFlow(1L)

    // 规则列表（按 ownerUserId 过滤）
    val rules: StateFlow<List<KeywordReply>> = currentUserId
        .flatMapLatest { userId -> keywordReplyDao.observeByOwner(userId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 人设列表（用于 AI 回复时选择）
    val personas: StateFlow<List<Persona>> = currentUserId
        .flatMapLatest { userId -> personaDao.observeByOwner(userId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _editingRule = MutableStateFlow<KeywordReply?>(null)
    val editingRule: StateFlow<KeywordReply?> = _editingRule.asStateFlow()

    fun startCreate() {
        _editingRule.value = KeywordReply(
            ownerUserId = currentUserId.value,
            keyword = "",
            matchType = MatchType.CONTAINS,
            replyType = ReplyType.TEXT,
            replyText = "",
            replyImage = null,
            personaId = null,
            enabled = true
        )
    }

    fun startEdit(rule: KeywordReply) {
        _editingRule.value = rule
    }

    fun dismissEdit() {
        _editingRule.value = null
    }

    fun saveRule(rule: KeywordReply) {
        viewModelScope.launch {
            keywordReplyDao.insert(rule)
            botService.setCurrentUser(currentUserId.value)
            _editingRule.value = null
        }
    }

    fun deleteRule(id: Long) {
        viewModelScope.launch {
            keywordReplyDao.delete(id, currentUserId.value)
        }
    }

    fun toggleEnabled(rule: KeywordReply) {
        viewModelScope.launch {
            keywordReplyDao.insert(rule.copy(enabled = !rule.enabled))
        }
    }
}
