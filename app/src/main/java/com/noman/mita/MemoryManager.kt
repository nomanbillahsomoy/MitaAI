package com.noman.mita

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class MemoryItem(
    val fact: String,
    val timestamp: Long = System.currentTimeMillis()
)

class MemoryManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("mita_memory", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val memoryKey = "friend_memories"

    init {
        // Initialize default memory if empty
        if (getAllMemories().isEmpty()) {
            saveFact("ব্যবহারকারীর নাম নোমান (Noman)")
            saveFact("মিতা এবং নোমান খুব ভালো বন্ধু। মিতা সবসময় নোমানকে 'তুমি' বলে সম্বোধন করে এবং আন্তরিকভাবে কথা বলে।")
            saveFact("মিতা নোমানের প্রিয় বিষয়, কাজের রুটিন ও পছন্দের যত্ন নেয়।")
        }
    }

    fun getAllMemories(): List<MemoryItem> {
        val json = prefs.getString(memoryKey, null) ?: return emptyList()
        val type = object : TypeToken<List<MemoryItem>>() {}.type
        return try {
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveFact(fact: String) {
        val list = getAllMemories().toMutableList()
        // Avoid duplicate exact facts
        if (!list.any { it.fact.equals(fact, ignoreCase = true) }) {
            list.add(MemoryItem(fact.trim()))
            prefs.edit().putString(memoryKey, gson.toJson(list)).apply()
        }
    }

    fun getMemorySummary(): String {
        val memories = getAllMemories()
        if (memories.isEmpty()) return "কোনো সংরক্ষিত তথ্য নেই।"
        val sb = StringBuilder()
        for (item in memories) {
            sb.append("- ").append(item.fact).append("\n")
        }
        return sb.toString()
    }

    fun clearMemories() {
        prefs.edit().remove(memoryKey).apply()
    }
}
