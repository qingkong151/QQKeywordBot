package com.qqbot.keywordbot.napcat.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * OneBot V11 协议相关数据模型。
 * 参考：https://github.com/botuniverse/onebot-11
 */

/** 收到的消息事件 */
@JsonClass(generateAdapter = true)
data class OneBotEvent(
    @Json(name = "post_type") val postType: String,
    @Json(name = "message_type") val messageType: String? = null, // private / group
    @Json(name = "sub_type") val subType: String? = null,
    @Json(name = "user_id") val userId: Long = 0,            // 发送者 QQ 号
    @Json(name = "group_id") val groupId: Long? = null,       // 群号
    @Json(name = "message_id") val messageId: Long = 0,
    @Json(name = "raw_message") val rawMessage: String = "",
    @Json(name = "message") val message: List<MessageSegment> = emptyList(),
    @Json(name = "sender") val sender: Sender? = null,
    @Json(name = "self_id") val selfId: Long = 0,
    @Json(name = "time") val time: Long = 0
)

@JsonClass(generateAdapter = true)
data class Sender(
    @Json(name = "user_id") val userId: Long = 0,
    @Json(name = "nickname") val nickname: String = "",
    @Json(name = "card") val card: String? = null
)

/** 消息段（CQ 码的结构化形式） */
@JsonClass(generateAdapter = true)
data class MessageSegment(
    @Json(name = "type") val type: String,        // text / at / image / face ...
    @Json(name = "data") val data: Map<String, String> = emptyMap()
)

/** 发送消息请求 */
@JsonClass(generateAdapter = true)
data class SendMsgRequest(
    @Json(name = "action") val action: String,    // send_private_msg / send_group_msg
    @Json(name = "params") val params: SendMsgParams,
    @Json(name = "echo") val echo: String? = null
)

@JsonClass(generateAdapter = true)
data class SendMsgParams(
    @Json(name = "user_id") val userId: Long? = null,
    @Json(name = "group_id") val groupId: Long? = null,
    @Json(name = "message") val message: List<MessageSegment>
)

/** API 响应 */
@JsonClass(generateAdapter = true)
data class OneBotApiResponse(
    @Json(name = "status") val status: String,
    @Json(name = "retcode") val retcode: Int,
    @Json(name = "data") val data: Any? = null,
    @Json(name = "echo") val echo: String? = null
)
