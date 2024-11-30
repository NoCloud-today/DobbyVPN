package com.dobby.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.VpnService.Builder
import android.os.Build
import android.util.Log
import com.dobby.consts.reservedBypassSubnets
import com.example.ck_client.LogHelper
import com.example.ck_client.MyVpnService

class DobbyVpnInterfaceFactory {

    fun create(context: Context, vpnService: MyVpnService): Builder {
//        LogHelper.log(this@MyVpnService, "MyVpnService: Start function setupVpn():")
//        LogHelper.log(this@MyVpnService, "MyVpnService: Start function setupVpn():")
        LogHelper.log(context, "MyVpnService: Creating VPN Interface:")
        val builder = vpnService.Builder()
            .setSession("Outline")
            .setMtu(1500)
            .addAddress("10.111.222.1", 24)
            .addDnsServer("1.1.1.1")
            .addDisallowedApplication(context.packageName)

//        LogHelper.log(this@MyVpnService, "MyVpnService: Command: val builder = Builder()")
//        val mtu = 1500
//        LogHelper.log(this@MyVpnService, "MyVpnService: mtu: val mtu = 1500")
//        builder.
//        LogHelper.log(this@MyVpnService, "MyVpnService: Command: builder.setSession(Outline)")
//        builder.setMtu(mtu)
//        LogHelper.log(this@MyVpnService, "MyVpnService: Command: builder.setMtu(mtu)")
//        builder.addAddress("10.111.222.1", 24)
//        LogHelper.log(this@MyVpnService, "MyVpnService: Command: builder.addAddress(10.111.222.1, 24)")

        val dnsServers = getDnsServers(context)
        val dns_server = dnsServers.get(0)
        builder.addDnsServer(dns_server)
        reservedBypassSubnets.forEach { subnet ->
            try {
                val parts = subnet.split("/")
                val address = parts[0]
                val prefixLength = parts[1].toInt()
                builder.addRoute(address, prefixLength)
            } catch (e: Exception) {
                Log.e("MyVpnService", "Error: $subnet", e)
            }
        }
        return builder
    }

    private fun getDnsServers(context: Context): List<String> {
        val dnsServers = mutableListOf<String>()

        // TODO add minSdk for the app if necessary
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork: Network? = connectivityManager.activeNetwork

            connectivityManager.getNetworkCapabilities(activeNetwork)?.let {
                val linkProperties = connectivityManager.getLinkProperties(activeNetwork)
                if (linkProperties != null) {
                    val dnsAddresses = linkProperties.dnsServers
                    dnsAddresses.forEach {
                        dnsServers.add(it.hostAddress)
                    }
                }
            }
        }
        return dnsServers
    }
}
