package com.example.ck_client

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import cloak_outline.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.PrintStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.nio.ByteBuffer

class MyVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private lateinit var device: cloak_outline.OutlineDevice
    private val bufferSize = 65536
    private lateinit var inputStream: FileInputStream
    private lateinit var outputStream: FileOutputStream
    private var check = true
    private val reservedBypassSubnets = listOf(
        "1.0.0.0/8", "2.0.0.0/7", "4.0.0.0/6", "8.0.0.0/7", "11.0.0.0/8",
        "12.0.0.0/6", "16.0.0.0/4", "32.0.0.0/3", "64.0.0.0/3", "96.0.0.0/6",
        "100.0.0.0/10", "100.128.0.0/9", "101.0.0.0/8", "102.0.0.0/7", "104.0.0.0/5",
        "112.0.0.0/5", "120.0.0.0/6", "124.0.0.0/7", "126.0.0.0/8", "128.0.0.0/3",
        "160.0.0.0/5", "168.0.0.0/8", "169.0.0.0/9", "169.128.0.0/10", "169.192.0.0/11",
        "169.224.0.0/12", "169.240.0.0/13", "169.248.0.0/14", "169.252.0.0/15", "169.255.0.0/16",
        "170.0.0.0/7", "172.0.0.0/12", "172.32.0.0/11", "172.64.0.0/10", "172.128.0.0/9",
        "173.0.0.0/8", "174.0.0.0/7", "176.0.0.0/4", "192.0.1.0/24", "192.0.3.0/24",
        "192.0.4.0/22", "192.0.8.0/21", "192.0.16.0/20", "192.0.32.0/19", "192.0.64.0/18",
        "192.0.128.0/17", "192.1.0.0/16", "192.2.0.0/15", "192.4.0.0/14", "192.8.0.0/13",
        "192.16.0.0/13", "192.24.0.0/14", "192.28.0.0/15", "192.30.0.0/16", "192.31.0.0/17",
        "192.31.128.0/18", "192.31.192.0/22", "192.31.197.0/24", "192.31.198.0/23", "192.31.200.0/21",
        "192.31.208.0/20", "192.31.224.0/19", "192.32.0.0/12", "192.48.0.0/14", "192.52.0.0/17",
        "192.52.128.0/18", "192.52.192.0/24", "192.52.194.0/23", "192.52.196.0/22", "192.52.200.0/21",
        "192.52.208.0/20", "192.52.224.0/19", "192.53.0.0/16", "192.54.0.0/15", "192.56.0.0/13",
        "192.64.0.0/12", "192.80.0.0/13", "192.88.0.0/18", "192.88.64.0/19", "192.88.96.0/23",
        "192.88.98.0/24", "192.88.100.0/22", "192.88.104.0/21", "192.88.112.0/20", "192.88.128.0/17",
        "192.89.0.0/16", "192.90.0.0/15", "192.92.0.0/14", "192.96.0.0/11", "192.128.0.0/11",
        "192.160.0.0/13", "192.169.0.0/16", "192.170.0.0/15", "192.172.0.0/15", "192.174.0.0/16",
        "192.175.0.0/19", "192.175.32.0/20", "192.175.49.0/24", "192.175.50.0/23", "192.175.52.0/22",
        "192.175.56.0/21", "192.175.64.0/18", "192.175.128.0/17", "192.176.0.0/12", "192.192.0.0/10",
        "193.0.0.0/8", "194.0.0.0/7", "196.0.0.0/7", "198.0.0.0/12", "198.16.0.0/15",
        "198.20.0.0/14", "198.24.0.0/13", "198.32.0.0/12", "198.48.0.0/15", "198.50.0.0/16",
        "198.51.0.0/18", "198.51.64.0/19", "198.51.96.0/22", "198.51.101.0/24", "198.51.102.0/23",
        "198.51.104.0/21", "198.51.112.0/20", "198.51.128.0/17", "198.52.0.0/14", "198.56.0.0/13",
        "198.64.0.0/10", "198.128.0.0/9", "199.0.0.0/8", "200.0.0.0/7", "202.0.0.0/8",
        "203.0.0.0/18", "203.0.64.0/19", "203.0.96.0/20", "203.0.112.0/24", "203.0.114.0/23",
        "203.0.116.0/22", "203.0.120.0/21", "203.0.128.0/17", "203.1.0.0/16", "203.2.0.0/15",
        "203.4.0.0/14", "203.8.0.0/13", "203.16.0.0/12", "203.32.0.0/11", "203.64.0.0/10",
        "203.128.0.0/9", "204.0.0.0/6", "208.0.0.0/4"
    )

    companion object {
        const val VPN_KEY_EXTRA = "API_KEY"

        fun createIntent(context: Context): Intent {
            return Intent(context, MyVpnService::class.java)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (val vpnKey = intent?.getStringExtra(VPN_KEY_EXTRA)) {
            null -> {
                check = false
                LogHelper.log(this@MyVpnService, "MyVpnService: VPN key is missing")
                stopSelf()
                return START_NOT_STICKY
            }
            "Stop" -> {
                check = false
                vpnInterface?.close()
                stopSelf()
            }
            else -> {
                check = true
                device = Cloak_outline.newOutlineDevice(vpnKey)
            }
        }
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()

        redirectLogsToFile()

        LogHelper.log(this@MyVpnService, "MyVpnService: Start curl before connection")
        CoroutineScope(Dispatchers.IO).launch {
            val ipAddress = fetchIp()
            withContext(Dispatchers.Main) {
                if (ipAddress != null) {
                    LogHelper.log(this@MyVpnService, "MyVpnService: response from curl: $ipAddress")
                    setupVpn()
                    //checkServerAvailability(iqAddress)

                } else {
                    LogHelper.log(this@MyVpnService, "MyVpnService: Failed to fetch IP, cancelling VPN setup.")
                    stopSelf()
                }
            }
        }
    }

    private fun setupVpn() {
        LogHelper.log(this@MyVpnService, "MyVpnService: Start function setupVpn():")
        LogHelper.log(this@MyVpnService, "MyVpnService: Start function setupVpn():")
        LogHelper.log(this@MyVpnService, "MyVpnService: Create VPN Interface:")
        val builder = Builder()
        LogHelper.log(this@MyVpnService, "MyVpnService: Command: val builder = Builder()")
        val mtu = 1500
        LogHelper.log(this@MyVpnService, "MyVpnService: mtu: val mtu = 1500")
        builder.setSession("Outline")
        LogHelper.log(this@MyVpnService, "MyVpnService: Command: builder.setSession(Outline)")
        builder.setMtu(mtu)
        LogHelper.log(this@MyVpnService, "MyVpnService: Command: builder.setMtu(mtu)")
        builder.addAddress("10.111.222.1", 24)
        LogHelper.log(this@MyVpnService, "MyVpnService: Command: builder.addAddress(10.111.222.1, 24)")

        val dnsServers = getDnsServers(this)
        val dns_server = dnsServers.get(0)
        builder.addDnsServer(dns_server)
        //builder.setBlocking(true)
        builder.addDisallowedApplication(this.packageName)

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

        vpnInterface = builder.establish()

        for (dns in getDnsServers(this)) {
            Log.d("DNS_SERVERS", "DNS Server: $dns")
        }

        if (vpnInterface != null) {
            inputStream = FileInputStream(vpnInterface?.fileDescriptor)
            outputStream = FileOutputStream(vpnInterface?.fileDescriptor)

            LogHelper.log(this@MyVpnService, "MyVpnService: VPN Interface Created Successfully")

            CoroutineScope(Dispatchers.IO).launch {
                setupTunnel()
                LogHelper.log(this@MyVpnService, "MyVpnService: Start function startReadingPackets()")
                startReadingPackets()
                LogHelper.log(this@MyVpnService, "MyVpnService: Start function startWritingPackets()")
                startWritingPackets()

                logRoutingTable()

                LogHelper.log(this@MyVpnService, "MyVpnService: Start function resolveAndLogDomain(\"google.com\")")
                val ipAddress = resolveAndLogDomain("google.com")
                LogHelper.log(this@MyVpnService, "MyVpnService: Start function ping(\"1.1.1.1\")")
                ping("1.1.1.1").await()
                if (ipAddress != null) {
                    checkServerAvailability(ipAddress)

                } else {
                    LogHelper.log(this@MyVpnService, "MyVpnService: Unable to resolve IP for google.com")
                }

                LogHelper.log(this@MyVpnService, "MyVpnService: Start curl after connection")
                val response = fetchIp()
                LogHelper.log(this@MyVpnService, "MyVpnService: response from curl: $response")
            }
        } else {
            LogHelper.log(this, "MyVpnService: Failed to Create VPN Interface")
        }
    }

    private suspend fun setupTunnel() {
        withContext(Dispatchers.IO) {
            try {
                LogHelper.log(this@MyVpnService, "MyVpnService: Start function setupTunnel()")
                LogHelper.log(this@MyVpnService, "MyVpnService: End of function setupTunnel")
            } catch (e: Exception) {
                LogHelper.log(this@MyVpnService, "MyVpnService: Failed to setup tunnel: ${e.message}")
            }
        }
    }

    private fun logRoutingTable() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val processBuilder = ProcessBuilder("ip", "route")
                processBuilder.redirectErrorStream(true)
                val process = processBuilder.start()

                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val output = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    output.append(line).append("\n")
                }

                process.waitFor()

                LogHelper.log(this@MyVpnService, "MyVpnService: Routing Table:\n$output")

            } catch (e: Exception) {
                LogHelper.log(this@MyVpnService, "MyVpnService: Failed to retrieve routing table: ${e.message}")
            }
        }
    }

    private suspend fun testTimeout(): Boolean {
        return withContext(Dispatchers.IO) {
            val result = withTimeoutOrNull(7000L) {
                delay(9000L)
                true
            }
            result ?: run {
                LogHelper.log(this@MyVpnService, "MyVpnService: Timeout occurred in test")
                false
            }
        }
    }

    private suspend fun resolveAndLogDomain(domain: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                withTimeout(5000L) {
                    val address = InetAddress.getByName(domain)
                    val ipAddress = address.hostAddress
                    LogHelper.log(this@MyVpnService, "MyVpnService: Domain resolved successfully. Domain: $domain, IP: $ipAddress")
                    ipAddress
                }
            } catch (e: TimeoutCancellationException) {
                LogHelper.log(this@MyVpnService, "MyVpnService: Domain resolution timed out. Domain: $domain")
                null
            } catch (e: UnknownHostException) {
                LogHelper.log(this@MyVpnService, "MyVpnService: Failed to resolve domain. Domain: $domain: ${e.message}")
                null
            } catch (e: Exception) {
                LogHelper.log(this@MyVpnService, "MyVpnService: Exception during domain resolution. Domain: $domain, Error: ${e.message}")
                null
            }
        }
    }

    fun getDnsServers(context: Context): List<String> {
        val dnsServers = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork: Network? = connectivityManager.activeNetwork
            val networkCapabilities: NetworkCapabilities? = connectivityManager.getNetworkCapabilities(activeNetwork)

            if (networkCapabilities != null) {
                val linkProperties = connectivityManager.getLinkProperties(activeNetwork)
                if (linkProperties != null) {
                    val dnsAddresses = linkProperties.dnsServers
                    for (dns in dnsAddresses) {
                        dnsServers.add(dns.hostAddress)
                    }
                }
            }
        }

        return dnsServers
    }

    private fun redirectLogsToFile() {
        val logFile = File(filesDir, "cloak_logs.txt")
        try {
            val fileStream = PrintStream(logFile)
            System.setOut(fileStream)
            System.setErr(fileStream)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun ping(host: String): Deferred<Unit> {
        val deferred = CompletableDeferred<Unit>()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val processBuilder = ProcessBuilder("ping", "-c", "4", host)
                processBuilder.redirectErrorStream(true)
                val process = processBuilder.start()

                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val output = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    output.append(line).append("\n")
                }

                process.waitFor()
                LogHelper.log(this@MyVpnService, "MyVpnService: Ping output:\n$output")
                deferred.complete(Unit)
            } catch (e: Exception) {
                LogHelper.log(this@MyVpnService, "MyVpnService: Failed to execute ping command: ${e.message}")
                deferred.completeExceptionally(e)
            }
        }
        return deferred
    }

    private fun startReadingPackets() {
        CoroutineScope(Dispatchers.IO).launch {
            vpnInterface?.let { vpn ->
                val buffer = ByteBuffer.allocate(bufferSize)

                while (true) {
                    if (check == true) {
                        val length = inputStream.read(buffer.array())
                        if (length > 0) {
                            val packetData = buffer.array().copyOfRange(0, length)
                            try {
                                device?.write(packetData)
                                //val hexString = packetData.joinToString(separator = " ") { byte -> "%02x".format(byte) }
                                //LogHelper.log(this@MyVpnService, "MyVpnService: Packet Data Written (Hex): $hexString")
                            } catch (e: Exception) {
                                LogHelper.log(
                                    this@MyVpnService,
                                    "MyVpnService: Failed to write packet to Outline: ${e.message}"
                                )
                            }
                        }
                        buffer.clear()
                    }
                }
            }
        }
    }

    private suspend fun fetchIp(): String? {
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
                    LogHelper.log(this@MyVpnService, "MyVpnService: Timeout or empty response while fetching IP")
                }
                result
            } catch (e: Exception) {
                LogHelper.log(this@MyVpnService, "Error fetching IP: ${e.message}")
                null
            }
        }
    }

    private fun checkServerAvailability(host: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val socket = Socket()
                val socketAddress = InetSocketAddress(host, 443)

                socket.connect(socketAddress, 5000)

                if (socket.isConnected) {
//                    val writer = OutputStreamWriter(socket.getOutputStream(), "UTF-8")
//                    val reader = BufferedReader(InputStreamReader(socket.getInputStream(), "UTF-8"))
//
//                    val request = "GET / HTTP/1.1\r\nHost: $host\r\nConnection: close\r\n\r\n"
//                    writer.write(request)
//                    writer.flush()
//
//                    val response = StringBuilder()
//                    var line: String?
//                    while (reader.readLine().also { line = it } != null) {
//                        response.append(line).append("\n")
//                    }

                    LogHelper.log(this@MyVpnService, "MyVpnService: Successfully reached $host on port 443 via TCP")
                    //LogHelper.log(this@MyVpnService, "MyVpnService: Response from server:\n$response")

//                    writer.close()
//                    reader.close()
                    socket.close()
                } else {
                    LogHelper.log(this@MyVpnService, "MyVpnService: Failed to reach $host on port 443 via TCP")
                }
            } catch (e: SocketTimeoutException) {
                LogHelper.log(this@MyVpnService, "MyVpnService: Timeout error when connecting to $host on port 443 via TCP: ${e.message}")
            } catch (e: Exception) {
                LogHelper.log(this@MyVpnService, "MyVpnService: Error when connecting to $host on port 443 via TCP: ${e.message}")
            }
        }
    }

    private fun startWritingPackets() {
        CoroutineScope(Dispatchers.IO).launch {
            vpnInterface?.let { vpn ->
                val buffer = ByteBuffer.allocate(bufferSize)

                while (true) {
                    try {
                        //val length = tunnel.read(buffer)
                        //if (length > 0) {
                        //    outputStream.write(buffer.array(), 0, length)
                        //}
                        //buffer.clear()
                        //LogHelper.log(this@MyVpnService, "MyVpnService: read packet from tunnel")
                        if (check == true) {
                            val packetData = device?.read()
                            packetData?.let {
                                outputStream.write(it)
                                //val hexString = it.joinToString(separator = " ") { byte -> "%02x".format(byte) }
                                //LogHelper.log(this@MyVpnService, "MyVpnService: Packet Data Read (Hex): $hexString")
                            } ?: LogHelper.log(this@MyVpnService, "No data read from Outline")
                        }
                    } catch (e: Exception) {
                        LogHelper.log(this@MyVpnService, "MyVpnService: Failed to read packet from tunnel: ${e.message}")
                    }
                    buffer.clear()
                }
            }
        }
    }
}
