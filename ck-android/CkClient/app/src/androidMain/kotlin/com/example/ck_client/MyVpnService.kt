package com.example.ck_client

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import cloak_outline.OutlineDevice
import cloak_outline.Cloak_outline
import com.dobby.vpn.DobbyVpnInterfaceFactory
import com.dobby.vpn.IpFetcher
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.PrintStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.ByteBuffer

class MyVpnService : VpnService() {

    companion object {
        const val VPN_KEY_EXTRA = "API_KEY"

        fun createIntent(context: Context): Intent {
            return Intent(context, MyVpnService::class.java)
        }
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var device: OutlineDevice? = null
    private val ipFetcher: IpFetcher = IpFetcher()
    private val vpnInterfaceFactory: DobbyVpnInterfaceFactory = DobbyVpnInterfaceFactory()

    private val bufferSize = 65536
    private lateinit var inputStream: FileInputStream
    private lateinit var outputStream: FileOutputStream
    private var check = true

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
            val ipAddress = ipFetcher.fetchIp(context = this@MyVpnService)
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

    override fun onDestroy() {
        super.onDestroy()
        runCatching { vpnInterface?.close() }
            .onFailure { it.printStackTrace() }
    }


    private fun setupVpn() {
        vpnInterface = vpnInterfaceFactory
            .create(context = this@MyVpnService, vpnService = this@MyVpnService)
            .establish()

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
                val response = ipFetcher.fetchIp(context = this@MyVpnService)
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
                            val packetData: ByteArray = buffer.array().copyOfRange(0, length)
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
                            val packetData: ByteArray? = device?.read()

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
