package com.dobby.domain

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.firstOrNull
import java.util.concurrent.atomic.AtomicBoolean

object ConnectionStateRepository {

    private val connectionFlow = MutableSharedFlow<Boolean>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private val isInitialized = AtomicBoolean(false)

    fun init(value: Boolean) {
        if (isInitialized.compareAndSet(false, true)) {
            connectionFlow.tryEmit(value)
        }
    }


    fun update(isConnected: Boolean) {
        isInitialized.set(true)
        connectionFlow.tryEmit(isConnected)
    }

    fun observe(): Flow<Boolean> {
        return connectionFlow
    }

    suspend fun get(): Boolean {
        return connectionFlow.firstOrNull() ?: false
    }
}
