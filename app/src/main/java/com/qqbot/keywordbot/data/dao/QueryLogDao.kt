package com.qqbot.keywordbot.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.qqbot.keywordbot.data.entity.QueryLog
import kotlinx.coroutines.flow.Flow

@Dao
interface QueryLogDao {

    @Insert
    suspend fun insert(log: QueryLog): Long

    @Query("SELECT * FROM query_log ORDER BY ts DESC LIMIT :limit")
    fun observeRecent(limit: Int = 200): Flow<List<QueryLog>>

    @Query("SELECT * FROM query_log WHERE userId = :userId ORDER BY ts DESC LIMIT :limit")
    fun observeByUser(userId: Long, limit: Int = 100): Flow<List<QueryLog>>
}
