package com.dobby.domain

import android.content.SharedPreferences

class CloakConfigRepository(
    private val prefs: SharedPreferences
) {

    fun get(): String {
        return prefs.getString("config", "") ?: ""
    }

    fun save(config: String) {
        prefs.edit().putString("config", config).apply()
    }
}
