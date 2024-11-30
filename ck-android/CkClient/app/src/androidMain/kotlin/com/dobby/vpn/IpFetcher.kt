package com.dobby.vpn

import android.content.Context
import com.example.ck_client.LogHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection
import java.net.URL

class IpFetcher {

    suspend fun fetchIp(context: Context): String? {
        return withContext(Dispatchers.IO) {
            try {
                val result = withTimeoutOrNull(7000L) {
                    val url = URL("https://api.ipify.org")
                    val connection = url.openConnection() as HttpURLConnection

                    connection.connectTimeout = 5000
                    connection.readTimeout = 5000

                    connection.inputStream.bufferedReader().use { reader ->
                        reader.readText()
                    }.also { ipAddress ->
                        if (ipAddress.isNotEmpty()) {
                            return@withTimeoutOrNull ipAddress
                        } else {
                            return@withTimeoutOrNull null
                        }
                    }
                }

                if (result == null) {
                    LogHelper.log(context, "MyVpnService: Timeout or empty response while fetching IP")
                }
                result
            } catch (e: Exception) {
                LogHelper.log(context, "Error fetching IP: ${e.message}")
                null
            }
        }
    }
}