package com.dobby.domain

import android.content.SharedPreferences

class OutlineKeyRepository(
    private val prefs: SharedPreferences
) {

    fun get(): String {
        return prefs.getString("outlineApiKey", "") ?: ""
    }

    fun save(apiKey: String) {
        prefs.edit().putString("outlineApiKey", apiKey).apply()
    }

    fun setDisconnectionFlag(shouldDisconnect: Boolean) {
        prefs.edit().putBoolean("disconnectionFlag", shouldDisconnect).apply()
    }

    fun checkDisconnectionFlag(): Boolean {
        return prefs.getBoolean("disconnectionFlag", false) ?: false
    }
}
