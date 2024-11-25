package models

data class ConfigData(
    val localHost: String = "127.0.0.1",
    val localPort: String = "1984",
    val configText: String = "",
    val udp: Boolean = false,
    val key: String = ""
)