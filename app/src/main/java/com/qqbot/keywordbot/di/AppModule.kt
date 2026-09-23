package com.qqbot.keywordbot.di

import android.content.Context
import androidx.room.Room
import com.qqbot.keywordbot.ai.AiProvider
import com.qqbot.keywordbot.ai.OpenAiProvider
import com.qqbot.keywordbot.data.AppDatabase
import com.qqbot.keywordbot.data.dao.AppUserDao
import com.qqbot.keywordbot.data.dao.KeywordReplyDao
import com.qqbot.keywordbot.data.dao.PersonaDao
import com.qqbot.keywordbot.data.dao.QueryLogDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DB_NAME)
            .fallbackToDestructiveMigration()
            .addCallback(object : androidx.room.RoomDatabase.Callback() {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    super.onCreate(db)
                    // 首次创建数据库时插入默认用户，作为 ownerUserId 的初始值
                    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                        db.execSQL(
                            "INSERT INTO app_user (name, createdAt) VALUES ('默认用户', ${System.currentTimeMillis()})"
                        )
                    }
                }
            })
            .build()

    @Provides
    fun provideAppUserDao(db: AppDatabase): AppUserDao = db.appUserDao()

    @Provides
    fun providePersonaDao(db: AppDatabase): PersonaDao = db.personaDao()

    @Provides
    fun provideKeywordReplyDao(db: AppDatabase): KeywordReplyDao = db.keywordReplyDao()

    @Provides
    fun provideQueryLogDao(db: AppDatabase): QueryLogDao = db.queryLogDao()

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

    /**
     * 绑定 AI 提供者实现。
     * 切换其他 AI 服务商时，只需替换这里的绑定即可。
     */
    @Provides
    @Singleton
    fun provideAiProvider(openAiProvider: OpenAiProvider): AiProvider = openAiProvider
}
