package com.example.ck_client.domain

import cloak_outline.Cloak_outline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

sealed interface ConnectResult {
    object ValidationError : ConnectResult
    object Success : ConnectResult
    object AlreadyConnected : ConnectResult
    class Error(val error: Throwable) : ConnectResult
}

sealed interface DisconnectResult {
    object Success : DisconnectResult
    object AlreadyDisconnected : DisconnectResult
    class Error(val error: Throwable) : DisconnectResult
}

object CloakConnectionInteractor {

    private val isConnected = AtomicBoolean(false)

    // TODO idk why this exists, refactor later
    private val connectionsCounter = AtomicInteger(0)

    suspend fun connect(
        config: String,
        localHost: String = "127.0.0.1",
        localPort: String = "1984",
    ): ConnectResult {
        android.util.Log.i("DOBBY_VPN_TAG", "connect(): ConnectResult")
        if (config.isEmpty() || localHost.isEmpty() || localPort.isEmpty()) {
            return ConnectResult.ValidationError
        }
        return withContext(Dispatchers.IO) {
            if (isConnected.compareAndSet(false, true)) {
                val result = runCatching {
                    if (connectionsCounter.incrementAndGet() == 1) {
                        Cloak_outline.startCloakClient(localHost, localPort, config, false)
                    } else {
                        Cloak_outline.startAgain()
                    }
                }
                if (result.isSuccess) {
                    ConnectResult.Success
                } else {
                    ConnectResult.Error(result.exceptionOrNull()!!)
                }
            } else {
                ConnectResult.AlreadyConnected
            }
        }
    }

    suspend fun disconnect(): DisconnectResult {
        android.util.Log.i("DOBBY_VPN_TAG", "disconnect(): DisconnectResult")
        return withContext(Dispatchers.IO) {
            if (isConnected.compareAndSet(true, false)) {
                val result = runCatching { Cloak_outline.stopCloak() }
                if (result.isSuccess) {
                    DisconnectResult.Success
                } else {
                    DisconnectResult.Error(result.exceptionOrNull()!!)
                }
            } else {
                DisconnectResult.AlreadyDisconnected
            }
        }
    }
}
