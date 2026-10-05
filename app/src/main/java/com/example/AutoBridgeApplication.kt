package com.example

import android.app.Application
import androidx.room.Room
import com.example.data.BridgeDatabase
import com.example.data.BridgePreferences
import com.example.data.BridgeRepository

class AutoBridgeApplication : Application() {

    lateinit var database: BridgeDatabase
        private set

    lateinit var repository: BridgeRepository
        private set

    lateinit var preferences: BridgePreferences
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = Room.databaseBuilder(
            applicationContext,
            BridgeDatabase::class.java,
            "autobridge_database"
        ).fallbackToDestructiveMigration().build()

        preferences = BridgePreferences(this)
        repository = BridgeRepository(database.capturedItemDao(), preferences)
    }

    companion object {
        lateinit var instance: AutoBridgeApplication
            private set
    }
}
