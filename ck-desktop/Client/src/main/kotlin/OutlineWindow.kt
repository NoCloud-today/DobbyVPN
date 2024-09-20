import com.google.gson.Gson
import com.sun.jna.Library
import com.sun.jna.Native
import javax.swing.JButton
import javax.swing.JFrame
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.SwingWorker
import java.io.File
import java.io.FileReader
import java.io.FileWriter

data class OutlineConfig(val key: String)

interface Device : Library {
    fun StartOutline(key: String)
    fun StopOutline()
}

class OutlineWindow : JFrame("Outline Control") {
    private val device: Device = Native.loadLibrary(File(System.getProperty("user.dir"), "libs/outline.so").absolutePath, Device::class.java) as Device
    private val gson = Gson()
    private val configFile = File(System.getProperty("user.dir"), "outline_config.json")

    init {
        val keyField = JTextField(20)

        val savedKey = loadKey()
        if (savedKey != null) {
            keyField.text = savedKey
        }

        val startButton = JButton("Start Outline VPN")
        val stopButton = JButton("Stop Outline VPN")
        val openCombinedButton = JButton("Open Combined Control")

        startButton.addActionListener {
            val key = keyField.text
            object : SwingWorker<Void, Void>() {
                override fun doInBackground(): Void? {
                    try {
                        device.StartOutline(key)
                        saveKey(key)
                    } catch (e: Exception) {
                        JOptionPane.showMessageDialog(this@OutlineWindow, "Failed to start Outline VPN: ${e.message}")
                    }
                    return null
                }

                override fun done() {
                    JOptionPane.showMessageDialog(this@OutlineWindow, "Outline VPN started successfully.")
                }
            }.execute()
        }

        stopButton.addActionListener {
            object : SwingWorker<Void, Void>() {
                override fun doInBackground(): Void? {
                    try {
                        device.StopOutline()
                    } catch (e: Exception) {
                        JOptionPane.showMessageDialog(this@OutlineWindow, "Failed to stop Outline VPN: ${e.message}")
                    }
                    return null
                }

                override fun done() {
                    JOptionPane.showMessageDialog(this@OutlineWindow, "Outline VPN stopped successfully.")
                }
            }.execute()
        }

        openCombinedButton.addActionListener {
            val combinedWindow = CloakOutline()
            combinedWindow.isVisible = true
        }

        val panel = JPanel()
        panel.add(keyField)
        panel.add(startButton)
        panel.add(stopButton)
        panel.add(openCombinedButton)
        add(panel)

        setSize(400, 200)
        defaultCloseOperation = EXIT_ON_CLOSE
        setLocationRelativeTo(null)
    }

    private fun saveKey(key: String) {
        try {
            val config = OutlineConfig(key)
            val writer = FileWriter(configFile)
            gson.toJson(config, writer)
            writer.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadKey(): String? {
        return if (configFile.exists()) {
            try {
                val reader = FileReader(configFile)
                val config = gson.fromJson(reader, OutlineConfig::class.java)
                reader.close()
                config?.key
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        } else {
            null
        }
    }
}

fun main() {
    val outlineWindow = OutlineWindow()
    outlineWindow.isVisible = true
}