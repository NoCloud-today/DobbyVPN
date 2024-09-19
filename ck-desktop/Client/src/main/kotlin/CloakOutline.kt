import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sun.jna.Native
import javax.swing.*
import java.awt.FlowLayout
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.io.OutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets

data class ConfigData(
    val localHost: String = "127.0.0.1",
    val localPort: String = "1984",
    val configText: String = "",
    val udp: Boolean = false,
    val key: String = ""
)

class LogWindow : JFrame("Log Window") {
    private val textArea = JTextArea()
    private val maxMessages = 1000

    init {
        textArea.isEditable = false
        add(JScrollPane(textArea))
        setSize(600, 400)
        setLocationRelativeTo(null)
        defaultCloseOperation = DISPOSE_ON_CLOSE
    }

    fun updateLogs() {
        val file = File("logs.txt")
        if (file.exists()) {
            try {
                val content = file.readText(StandardCharsets.UTF_8)
                val lines = content.split("\n")
                textArea.text = lines.takeLast(maxMessages).joinToString("\n")
                textArea.caretPosition = textArea.document.length
                println("TextArea updated")
            } catch (e: Exception) {
                textArea.text = "Error reading log file: ${e.message}"
            }
        } else {
            textArea.text = "Log file does not exist."
        }
    }

    fun clearLogs() {
        val file = File("logs.txt")
        if (file.exists()) {
            try {
                file.writeText("")
                println("Log file cleared")
            } catch (e: Exception) {
                println("Failed to clear log file: ${e.message}")
            }
        }
    }
}

class LogOutputStream(private val logWindow: LogWindow) : OutputStream() {
    private val buffer = StringBuilder()
    private var lastLogUpdateTime = System.currentTimeMillis()

    override fun write(b: Int) {
        buffer.append(b.toChar())
        if (b.toChar() == '\n') {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastLogUpdateTime > 1000) {
                lastLogUpdateTime = currentTime
                SwingUtilities.invokeLater {
                    logWindow.updateLogs()
                }
            }
            buffer.setLength(0)
        }
    }
}

class CloakOutline : JFrame("Combined Control") {
    private val logWindow = LogWindow()

    val cloakLib = Native.loadLibrary(File(System.getProperty("user.dir"), "libs/cloak.so").absolutePath, CloakLibrary::class.java) as CloakLibrary
    private val device: Device = Native.loadLibrary(File(System.getProperty("user.dir"), "libs/outline.so").absolutePath, Device::class.java) as Device

    private val configFilePath = "config.json"
    private var counter = 0

    init {
        System.setOut(PrintStream(LogOutputStream(logWindow)))
        System.setErr(PrintStream(LogOutputStream(logWindow)))

        layout = FlowLayout(FlowLayout.LEFT, 10, 10)

        val localHostLabel = JLabel("Local Host:")
        val localHostField = JTextField(20)
        val localPortLabel = JLabel("Local Port:")
        val localPortField = JTextField(20)
        val configLabel = JLabel("Config:")
        val configField = JTextArea(5, 20)
        configField.lineWrap = true
        configField.wrapStyleWord = true
        val configScrollPane = JScrollPane(configField)
        val udpCheckbox = JCheckBox("UDP Mode")
        val keyField = JTextField(20)

        val connectButton = JButton("Connect")
        connectButton.addActionListener {
            val localHost = localHostField.text
            val localPort = localPortField.text
            val config = configField.text
            val udp = udpCheckbox.isSelected
            val key = keyField.text

            saveConfig(ConfigData(localHost, localPort, config, udp, key))

            object : SwingWorker<Void, Void>() {
                override fun doInBackground(): Void? {
                    try {
                        device.StartOutline(key)
                    } catch (e: Exception) {
                        JOptionPane.showMessageDialog(this@CloakOutline, "Failed to start Outline VPN: ${e.message}")
                        println("Failed to start Outline VPN: ${e.message}")
                    }
                    return null
                }

                override fun done() {
                    logWindow.updateLogs()
                    println("Outline VPN connected.")
                }
            }.execute()

            if (counter == 0) {
                object : SwingWorker<Void, Void>() {
                    override fun doInBackground(): Void? {
                        cloakLib.StartCloakClient(localHost, localPort, config, udp)
                        return null
                    }

                    override fun done() {
                        logWindow.updateLogs()
                        println("Cloak VPN connected.")
                    }
                }.execute()
            }
            counter += 1
        }

        val disconnectButton = JButton("Disconnect")
        disconnectButton.addActionListener {
            object : SwingWorker<Void, Void>() {
                override fun doInBackground(): Void? {
                    device.StopOutline()
                    return null
                }

                override fun done() {
                    logWindow.updateLogs()
                    println("VPNs stopped successfully.")
                }
            }.execute()
        }

        val logWindowButton = JButton("Log Window")
        logWindowButton.addActionListener {
            logWindow.updateLogs()
            logWindow.isVisible = true
        }

        add(localHostLabel)
        add(localHostField)
        add(localPortLabel)
        add(localPortField)
        add(configLabel)
        add(configScrollPane)
        add(udpCheckbox)
        add(keyField)
        add(connectButton)
        add(disconnectButton)
        add(logWindowButton)

        loadConfig()?.let { configData ->
            localHostField.text = configData.localHost
            localPortField.text = configData.localPort
            configField.text = configData.configText
            udpCheckbox.isSelected = configData.udp
            keyField.text = configData.key
        }

        addWindowListener(object : java.awt.event.WindowAdapter() {
            override fun windowClosing(e: java.awt.event.WindowEvent?) {
                logWindow.clearLogs()
                super.windowClosing(e)
            }
        })

        setSize(600, 400)
        defaultCloseOperation = EXIT_ON_CLOSE
        setLocationRelativeTo(null)
    }

    private fun saveConfig(configData: ConfigData) {
        try {
            val writer = FileWriter(configFilePath)
            val gson = Gson()
            gson.toJson(configData, writer)
            writer.close()
        } catch (e: Exception) {
            println("Failed to save config: ${e.message}")
        }
    }

    private fun loadConfig(): ConfigData? {
        return try {
            val reader = FileReader(configFilePath)
            val gson = Gson()
            val type = object : TypeToken<ConfigData>() {}.type
            gson.fromJson<ConfigData>(reader, type).also { reader.close() }
        } catch (e: Exception) {
            println("Failed to load config: ${e.message}")
            null
        }
    }
}

fun main() {
    val combinedWindow = CloakOutline()
    combinedWindow.isVisible = true
}