package com.qqbot.keywordbot.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.qqbot.keywordbot.data.entity.Persona
import kotlinx.coroutines.flow.Flow

/**
 * 🔒 关键约束：除 getAllUnsafe（仅限系统级用途，禁止在业务层调用）外，
 * 所有查询方法都必须带 ownerUserId 参数，确保用户只能读取自己的人设。
 */
@Dao
interface PersonaDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(persona: Persona): Long

    /**
     * 读取指定用户的所有人设。
     * 必须传 ownerUserId，禁止跨用户读取。
     */
    @Query("SELECT * FROM persona WHERE ownerUserId = :ownerUserId ORDER BY id DESC")
    fun observeByOwner(ownerUserId: Long): Flow<List<Persona>>

    /**
     * 读取指定用户的某个人设。
     * 必须同时带 ownerUserId 和 id，防止越权读取。
     */
    @Query("SELECT * FROM persona WHERE id = :id AND ownerUserId = :ownerUserId")
    suspend fun getById(id: Long, ownerUserId: Long): Persona?

    @Query("DELETE FROM persona WHERE id = :id AND ownerUserId = :ownerUserId")
    suspend fun delete(id: Long, ownerUserId: Long): Int
}
