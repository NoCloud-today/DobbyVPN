package com.example.ck_client

import android.content.Context
import com.example.ck_client.MyVpnService.Companion.VPN_KEY_EXTRA

class MyVpnServiceInteractor {

    fun start(context: Context, apiKey: String) {
        MyVpnService.createIntent(context)
            .apply { putExtra(VPN_KEY_EXTRA, apiKey) }
            .let(context::startService)
    }

    fun stop(context: Context) {
        val vpnServiceIntent = MyVpnService.createIntent(context).apply {
            putExtra(VPN_KEY_EXTRA, "Stop")
        }
        context.startService(vpnServiceIntent)
        context.stopService(vpnServiceIntent)
    }
}
