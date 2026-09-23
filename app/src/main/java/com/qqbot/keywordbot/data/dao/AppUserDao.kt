package com.qqbot.keywordbot.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.qqbot.keywordbot.data.entity.AppUser
import kotlinx.coroutines.flow.Flow

@Dao
interface AppUserDao {
    @Insert
    suspend fun insert(user: AppUser): Long

    @Query("SELECT * FROM app_user WHERE id = :id")
    suspend fun getById(id: Long): AppUser?

    @Query("SELECT * FROM app_user ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<AppUser>>
}
