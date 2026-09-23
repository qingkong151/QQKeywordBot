package com.qqbot.keywordbot.ui.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qqbot.keywordbot.data.dao.PersonaDao
import com.qqbot.keywordbot.data.entity.Persona
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PersonaViewModel @Inject constructor(
    private val personaDao: PersonaDao
) : ViewModel() {

    // 当前激活用户（简化：默认 id=1）
    private val currentUserId = MutableStateFlow(1L)

    // 人设列表（按 ownerUserId 过滤，确保数据隔离）
    val personas: StateFlow<List<Persona>> = currentUserId
        .flatMapLatest { userId -> personaDao.observeByOwner(userId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _editingPersona = MutableStateFlow<Persona?>(null)
    val editingPersona: StateFlow<Persona?> = _editingPersona.asStateFlow()

    fun startCreate() {
        _editingPersona.value = Persona(
            ownerUserId = currentUserId.value,
            name = "",
            systemPrompt = "",
            model = "gpt-4o-mini",
            temperature = 0.7f,
            enabled = true
        )
    }

    fun startEdit(persona: Persona) {
        _editingPersona.value = persona
    }

    fun dismissEdit() {
        _editingPersona.value = null
    }

    fun savePersona(persona: Persona) {
        viewModelScope.launch {
            personaDao.insert(persona)
            _editingPersona.value = null
        }
    }

    fun deletePersona(id: Long) {
        viewModelScope.launch {
            personaDao.delete(id, currentUserId.value)
        }
    }

    fun toggleEnabled(persona: Persona) {
        viewModelScope.launch {
            personaDao.insert(persona.copy(enabled = !persona.enabled))
        }
    }
}
