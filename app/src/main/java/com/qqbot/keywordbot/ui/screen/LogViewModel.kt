package com.qqbot.keywordbot.ui.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qqbot.keywordbot.data.dao.QueryLogDao
import com.qqbot.keywordbot.data.entity.QueryLog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class LogViewModel @Inject constructor(
    private val queryLogDao: QueryLogDao
) : ViewModel() {

    val logs: StateFlow<List<QueryLog>> =
        queryLogDao.observeRecent(200)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
