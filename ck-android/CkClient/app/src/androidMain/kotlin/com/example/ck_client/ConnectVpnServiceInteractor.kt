package com.example.ck_client

import android.content.Context

class ConnectVpnServiceInteractor {

    fun start(context: Context) {
        MyVpnService
            .createIntent(context)
            .let(context::startService)
    }

    fun stop(context: Context) {
        val vpnServiceIntent = MyVpnService.createIntent(context)
        context.startService(vpnServiceIntent)
        context.stopService(vpnServiceIntent)
    }
}
