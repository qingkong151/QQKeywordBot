package com.qqbot.keywordbot.napcat

import com.qqbot.keywordbot.napcat.model.MessageSegment
import com.qqbot.keywordbot.napcat.model.OneBotApiResponse
import com.qqbot.keywordbot.napcat.model.OneBotEvent
import com.qqbot.keywordbot.napcat.model.SendMsgParams
import com.qqbot.keywordbot.napcat.model.SendMsgRequest
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OneBot V11 正向 WebSocket 客户端。
 * 连接到 NapCat 暴露的 WS 服务（默认 ws://127.0.0.1:3001）。
 *
 * 职责：
 *  - 建立/维护 WebSocket 连接
 *  - 解析收到的 OneBot 事件并以 Flow 形式分发
 *  - 提供发送消息的 API
 */
@Singleton
class OneBotClient @Inject constructor() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val eventAdapter = moshi.adapter(OneBotEvent::class.java)
    private val sendRequestAdapter = moshi.adapter(SendMsgRequest::class.java)
    private val responseAdapter = moshi.adapter(OneBotApiResponse::class.java)

    private var webSocket: WebSocket? = null
    private var heartbeatJob: Job? = null

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _events = MutableSharedFlow<OneBotEvent>(extraBufferCapacity = 128)
    val events: SharedFlow<OneBotEvent> = _events.asSharedFlow()

    private val client = OkHttpClient.Builder()
        .pingInterval(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    fun connect(host: String = "127.0.0.1", port: Int = 3001, accessToken: String? = null) {
        val url = buildString {
            append("ws://$host:$port")
            if (!accessToken.isNullOrBlank()) append("?access_token=$accessToken")
        }
        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, listener)
        _connectionState.value = ConnectionState.CONNECTING
    }

    fun disconnect() {
        heartbeatJob?.cancel()
        webSocket?.close(1000, "user disconnect")
        webSocket = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    // ---------- 发送消息 API ----------

    /** 发送群消息 */
    suspend fun sendGroupMessage(groupId: Long, segments: List<MessageSegment>): Boolean {
        return send(
            SendMsgRequest(
                action = "send_group_msg",
                params = SendMsgParams(groupId = groupId, message = segments),
                echo = UUID.randomUUID().toString()
            )
        )
    }

    /** 发送私聊消息 */
    suspend fun sendPrivateMessage(userId: Long, segments: List<MessageSegment>): Boolean {
        return send(
            SendMsgRequest(
                action = "send_private_msg",
                params = SendMsgParams(userId = userId, message = segments),
                echo = UUID.randomUUID().toString()
            )
        )
    }

    private suspend fun send(request: SendMsgRequest): Boolean {
        val json = sendRequestAdapter.toJson(request)
        val ws = webSocket ?: return false
        return ws.send(json)
    }

    // ---------- WebSocket 监听 ----------

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            _connectionState.value = ConnectionState.CONNECTED
            startHeartbeat()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            scope.launch {
                // 尝试解析为事件；也可能是 API 响应（含 echo）
                val event = runCatching { eventAdapter.fromJson(text) }.getOrNull()
                if (event != null && event.postType.isNotBlank()) {
                    _events.emit(event)
                } else {
                    // API 响应，目前仅打印日志
                    runCatching { responseAdapter.fromJson(text) }.getOrNull()
                }
            }
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            onMessage(webSocket, bytes.utf8())
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            _connectionState.value = ConnectionState.FAILED
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            _connectionState.value = ConnectionState.DISCONNECTED
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            // NapCat 一般不需要应用层心跳，OkHttp ping 已足够；
            // 这里保留占位，可按需扩展。
        }
    }

    enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, FAILED }

    fun destroy() {
        disconnect()
        scope.cancel()
    }
}
