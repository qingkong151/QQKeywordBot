package com.qqbot.keywordbot.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.qqbot.keywordbot.data.entity.KeywordReply
import kotlinx.coroutines.flow.Flow

/**
 * 🔒 关键约束：除 getAllUnsafe（仅限系统级用途，禁止在业务层调用）外，
 * 所有查询方法都必须带 ownerUserId 参数，确保用户只能读取自己的关键词规则。
 */
@Dao
interface KeywordReplyDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: KeywordReply): Long

    /**
     * 读取指定用户的所有启用的关键词规则。
     * 必须传 ownerUserId，禁止跨用户读取。
     */
    @Query(
        "SELECT * FROM keyword_reply WHERE ownerUserId = :ownerUserId AND enabled = 1 ORDER BY id DESC"
    )
    suspend fun getEnabledByOwner(ownerUserId: Long): List<KeywordReply>

    /**
     * 读取指定用户的所有关键词规则（含禁用），用于管理界面。
     */
    @Query("SELECT * FROM keyword_reply WHERE ownerUserId = :ownerUserId ORDER BY id DESC")
    fun observeByOwner(ownerUserId: Long): Flow<List<KeywordReply>>

    @Query("DELETE FROM keyword_reply WHERE id = :id AND ownerUserId = :ownerUserId")
    suspend fun delete(id: Long, ownerUserId: Long): Int
}
