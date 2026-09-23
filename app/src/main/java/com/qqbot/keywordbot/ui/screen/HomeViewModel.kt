package com.qqbot.keywordbot.ui.screen

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qqbot.keywordbot.data.SettingsRepository
import com.qqbot.keywordbot.napcat.NapCatManager
import com.qqbot.keywordbot.napcat.OneBotClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val napCatManager: NapCatManager,
    private val oneBotClient: OneBotClient,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val napCatState: StateFlow<NapCatManager.NapCatState> =
        napCatManager.state.stateIn(viewModelScope, SharingStarted.Eagerly, NapCatManager.NapCatState.STOPPED)

    val wsState: StateFlow<OneBotClient.ConnectionState> =
        oneBotClient.connectionState.stateIn(viewModelScope, SharingStarted.Eagerly, OneBotClient.ConnectionState.DISCONNECTED)

    val logs: StateFlow<List<String>> =
        napCatManager.logs.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val qrCodeUrl: StateFlow<String?> =
        napCatManager.qrCodeUrl.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val openAiApiKey: StateFlow<String> =
        settingsRepository.openAiApiKey.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val qqDebReady: StateFlow<Boolean> =
        napCatManager.qqDebReady.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        // 启动时刷新 QQ.deb 状态
        napCatManager.refreshQqDebStatus()
    }

    fun startNapCat() = napCatManager.start()
    fun stopNapCat() = napCatManager.stop()
    fun connectOneBot() = oneBotClient.connect(host = "127.0.0.1", port = 3001)
    fun clearQrCode() = napCatManager.clearQrCode()

    fun saveOpenAiSettings(apiKey: String, baseUrl: String) {
        viewModelScope.launch {
            settingsRepository.setOpenAiApiKey(apiKey)
            settingsRepository.setOpenAiBaseUrl(baseUrl)
        }
    }

    /** 导入用户选择的 QQ.deb 文件 */
    fun importQqDeb(uri: Uri): Boolean = napCatManager.importQqDeb(uri)

    /** 删除已导入的 QQ.deb */
    fun deleteQqDeb() = napCatManager.deleteQqDeb()
}
