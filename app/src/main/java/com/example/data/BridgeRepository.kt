package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

class BridgeRepository(
    private val capturedItemDao: CapturedItemDao,
    private val preferences: BridgePreferences
) {
    val allItems: Flow<List<CapturedItem>> = capturedItemDao.getAllItems()
    val totalCount: Flow<Int> = capturedItemDao.getTotalCount()
    val gptCodeCount: Flow<Int> = capturedItemDao.getGptCodeCount()
    val termuxOutputCount: Flow<Int> = capturedItemDao.getTermuxOutputCount()
    val settings: StateFlow<BridgeSettings> = preferences.settingsFlow

    fun getItemsByType(type: String): Flow<List<CapturedItem>> {
        return capturedItemDao.getItemsByType(type)
    }

    suspend fun insertItem(item: CapturedItem): Long {
        return capturedItemDao.insertItem(item)
    }

    suspend fun deleteItem(id: Long) {
        capturedItemDao.deleteItemById(id)
    }

    suspend fun clearHistory() {
        capturedItemDao.clearAll()
    }

    fun updateSettings(newSettings: BridgeSettings) {
        preferences.updateSettings(newSettings)
    }

    fun currentSettings(): BridgeSettings {
        return preferences.currentSettings()
    }
}
