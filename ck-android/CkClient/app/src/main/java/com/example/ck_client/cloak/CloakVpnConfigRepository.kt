package com.example.ck_client.com.example.ck_client.cloak

import android.content.SharedPreferences
import org.json.JSONObject

interface CloakVpnConfigRepository {

    fun get(): CloakVpnConfig?

    fun save(config: CloakVpnConfig)
}

internal class CloakVpnConfigRepositoryImpl(
    private val prefs: SharedPreferences
) : CloakVpnConfigRepository {

    override fun get(): CloakVpnConfig? {
        val json = prefs.getString("vpnConfig", null)
        return json?.let {
            val jsonObject = JSONObject(it)
            CloakVpnConfig(
                config = jsonObject.optString("config", ""),
                localHost = jsonObject.optString("localHost", "127.0.0.1"),
                localPort = jsonObject.optString("localPort", "1984")
            )
        }
    }

    override fun save(config: CloakVpnConfig) {
        val json = JSONObject().apply {
            put("config", config.config)
            put("localHost", config.localHost)
            put("localPort", config.localPort)
        }.toString()

        with(prefs.edit()) {
            putString("vpnConfig", json)
            apply()
        }
    }
}
