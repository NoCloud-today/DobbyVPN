package com.example.ck_client.domain

data class CloakOutlineVpnConfig(
    val apiKey: String,
    val config: String,
    val localHost: String,
    val localPort: String,
    val isVpnRunning: Boolean
)
