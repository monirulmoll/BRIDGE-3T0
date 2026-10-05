package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "captured_items")
data class CapturedItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: String, // "CHATGPT_CODE" or "TERMUX_OUTPUT"
    val title: String,
    val content: String,
    val languageOrTag: String = "plaintext",
    val sourcePackage: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isAutoRelayed: Boolean = false
) {
    companion object {
        const val TYPE_CHATGPT_CODE = "CHATGPT_CODE"
        const val TYPE_TERMUX_OUTPUT = "TERMUX_OUTPUT"
    }
}
