package com.dobby.domain

import android.content.SharedPreferences
import android.util.Log.i as AndroidLog

internal class DobbyConfigsRepositoryImpl(
    private val prefs: SharedPreferences
) : DobbyConfigsRepository {

    override fun getCloakConfig(): String {
        return (prefs.getString("cloakConfig", "") ?: "").also {
            AndroidLog("DOBBY_TAG", "getCloakConfig, size = ${it.length}")
        }
    }

    override fun setCloakConfig(newConfig: String) {
        prefs.edit().putString("cloakConfig", newConfig).apply().also {
            AndroidLog("DOBBY_TAG", "setCloakConfig, size = ${newConfig.length}")
        }
    }

    override fun getIsCloakEnabled(): Boolean {
        return prefs.getBoolean("isCloakEnabled", false).also {
            AndroidLog("DOBBY_TAG", "getIsCloakEnabled: $it")
        }
    }

    override fun setIsCloakEnabled(isCloakEnabled: Boolean) {
        prefs.edit().putBoolean("isCloakEnabled", isCloakEnabled).apply().also {
            AndroidLog("DOBBY_TAG", "setIsCloakEnabled: $isCloakEnabled")
        }
    }

    override fun getOutlineKey(): String {
        return (prefs.getString("outlineApiKey", "") ?: "").also {
            AndroidLog("DOBBY_TAG", "getOutlineKey, size = ${it.length}")
        }
    }

    override fun setOutlineKey(newOutlineKey: String) {
        prefs.edit().putString("outlineApiKey", newOutlineKey).apply().also {
            AndroidLog("DOBBY_TAG", "setOutlineKey, size = ${newOutlineKey.length}")
        }
    }

    override fun getIsOutlineEnabled(): Boolean {
        return prefs.getBoolean("isOutlineEnabled", false).also {
            AndroidLog("DOBBY_TAG", "getIsOutlineEnabled = $it")
        }
    }

    override fun setIsOutlineEnabled(isOutlineEnabled: Boolean) {
        prefs.edit().putBoolean("isOutlineEnabled", isOutlineEnabled).apply().also {
            AndroidLog("DOBBY_TAG", "setIsOutlineEnabled = $isOutlineEnabled")
        }
    }
}
