package utils

import com.google.gson.Gson
import models.ConfigData
import java.io.File

object ConfigUtils {
    private val configFile = File("config.json")
    private val gson = Gson()

    fun saveConfig(configData: ConfigData) {
        configFile.writeText(gson.toJson(configData))
    }

    fun loadConfig(): ConfigData {
        return if (configFile.exists()) {
            gson.fromJson(configFile.readText(), ConfigData::class.java)
        } else {
            ConfigData()
        }
    }
}