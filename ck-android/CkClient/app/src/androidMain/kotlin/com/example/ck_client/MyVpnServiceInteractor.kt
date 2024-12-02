package com.example.ck_client

import android.content.Context
import com.dobby.domain.OutlineKeyRepository

class MyVpnServiceInteractor(
    private val outlineKeyRepository: OutlineKeyRepository
) {

    fun start(context: Context, apiKey: String) {
        outlineKeyRepository.save(apiKey)
        outlineKeyRepository.setDisconnectionFlag(shouldDisconnect = false)
        MyVpnService
            .createIntent(context)
            .let(context::startService)
    }

    fun stop(context: Context) {
        outlineKeyRepository.setDisconnectionFlag(shouldDisconnect = true)
        val vpnServiceIntent = MyVpnService.createIntent(context)
        context.startService(vpnServiceIntent)
        context.stopService(vpnServiceIntent)
    }
}
