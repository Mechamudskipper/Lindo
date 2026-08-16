package com.example.flashcards.data

import android.content.Context
import android.content.SharedPreferences
import com.example.flashcards.model.User

class UserRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)

    fun getUser(): User {
        val name = prefs.getString("user_name", "Lindo User") ?: "Lindo User"
        val lastColl = prefs.getString("last_collection", "Default") ?: "Default"
        return User(name, lastColl)
    }

    fun saveLastCollection(collectionName: String) {
        prefs.edit().putString("last_collection", collectionName).apply()
    }
}
