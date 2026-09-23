package com.qqbot.keywordbot.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.qqbot.keywordbot.data.dao.AppUserDao
import com.qqbot.keywordbot.data.dao.KeywordReplyDao
import com.qqbot.keywordbot.data.dao.PersonaDao
import com.qqbot.keywordbot.data.dao.QueryLogDao
import com.qqbot.keywordbot.data.entity.AppUser
import com.qqbot.keywordbot.data.entity.KeywordReply
import com.qqbot.keywordbot.data.entity.Persona
import com.qqbot.keywordbot.data.entity.QueryLog

@Database(
    entities = [
        AppUser::class,
        Persona::class,
        KeywordReply::class,
        QueryLog::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appUserDao(): AppUserDao
    abstract fun personaDao(): PersonaDao
    abstract fun keywordReplyDao(): KeywordReplyDao
    abstract fun queryLogDao(): QueryLogDao

    companion object {
        const val DB_NAME = "qq_keyword_bot.db"
    }
}
