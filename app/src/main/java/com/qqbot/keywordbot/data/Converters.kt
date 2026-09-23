package com.qqbot.keywordbot.data

import androidx.room.TypeConverter
import com.qqbot.keywordbot.data.entity.MatchType
import com.qqbot.keywordbot.data.entity.ReplyType

class Converters {
    @TypeConverter
    fun fromMatchType(value: MatchType): String = value.name

    @TypeConverter
    fun toMatchType(value: String): MatchType = MatchType.valueOf(value)

    @TypeConverter
    fun fromReplyType(value: ReplyType): String = value.name

    @TypeConverter
    fun toReplyType(value: String): ReplyType = ReplyType.valueOf(value)
}
