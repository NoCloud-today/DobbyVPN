package com.example.ck_client.domain

import android.content.SharedPreferences

class CloakOutlineConfigRepository(
    private val prefs: SharedPreferences
) {

    fun get(): CloakOutlineVpnConfig {
        return CloakOutlineVpnConfig(
            apiKey = prefs.getString("apiKey", "") ?: "",
            config = prefs.getString("config", "") ?: "",
            localHost = prefs.getString("localHost", "127.0.0.1") ?: "127.0.0.1",
            localPort = prefs.getString("localPort", "1984") ?: "1984",
            isVpnRunning = prefs.getBoolean("isVpnRunning", false)
        )
    }

    fun save(vpnConfig: CloakOutlineVpnConfig) {
        with(prefs.edit()) {
            putString("apiKey", vpnConfig.apiKey)
            putString("config", vpnConfig.config)
            putString("localHost", vpnConfig.localHost)
            putString("localPort", vpnConfig.localPort)
            putBoolean("isVpnRunning", vpnConfig.isVpnRunning)
            apply()
        }
    }
}
