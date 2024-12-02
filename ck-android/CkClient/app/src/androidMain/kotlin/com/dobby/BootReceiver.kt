package com.dobby

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService.MODE_PRIVATE
import com.dobby.domain.OutlineKeyRepository
import com.example.ck_client.MyVpnService

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {

            android.util.Log.i("VPN_TAG", "got ACTION_BOOT_COMPLETED intent")
            // just in case
            val sharedPreferences = context.getSharedPreferences("DobbyPrefs", MODE_PRIVATE)
            val outlineKeyRepository = OutlineKeyRepository(sharedPreferences)
            outlineKeyRepository.setDisconnectionFlag(shouldDisconnect = false)

            MyVpnService
                .createIntent(context)
                .run(context::startService)
        }
    }
}
