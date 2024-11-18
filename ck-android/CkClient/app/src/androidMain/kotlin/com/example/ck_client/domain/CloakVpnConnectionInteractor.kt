@file:Suppress("IMPLICIT_CAST_TO_ANY")

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

class CloakVpnConnectionInteractor {

    private val isConnected = AtomicBoolean(false)

    // TODO idk why this exists, refactor later
    private val connectionsCounter = AtomicInteger(0)

    suspend fun connect(localHost: String, localPort: String, config: String): ConnectResult {
        if (config.isNotEmpty() && localHost.isNotEmpty() && localPort.isNotEmpty()) {
            return ConnectResult.ValidationError
        }
        return withContext(Dispatchers.IO) {
            if (isConnected.compareAndSet(false, true)) {
                runCatching {
                    if (connectionsCounter.incrementAndGet() == 1) {
                        Cloak_outline.startCloakClient(localHost, localPort, config, false)
                    } else {
                        Cloak_outline.startAgain()
                    }
                }.onSuccess { ConnectResult.Success }
                    .onFailure(ConnectResult::Error)

            } else {
                ConnectResult.AlreadyConnected
            } as ConnectResult
        }
    }

    suspend fun disconnect(): DisconnectResult {
        return withContext(Dispatchers.IO) {
            if (isConnected.compareAndSet(true, false)) {
                runCatching { Cloak_outline.stopCloak() }
                    .onSuccess { DisconnectResult.Success }
                    .onFailure(DisconnectResult::Error)
            } else {
                DisconnectResult.AlreadyDisconnected
            } as DisconnectResult
        }
    }
}
