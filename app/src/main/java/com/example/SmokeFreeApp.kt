package com.example

import android.app.Application
import androidx.room.Room
import com.example.data.*

class SmokeFreeApp : Application() {
    lateinit var repository: LocalRepository
        private set

    override fun onCreate() {
        super.onCreate()
        
        val database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "smokefree_database"
        )
        .fallbackToDestructiveMigration() // ensures safety during rapid prototyping / schema changes
        .build()

        val appPreferences = AppPreferences(applicationContext)
        repository = LocalRepositoryImpl(database.dailyLogDao(), database.chatMessageDao(), appPreferences)
    }
}
