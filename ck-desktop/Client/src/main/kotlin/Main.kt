import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sun.jna.Library
import com.sun.jna.Native
import java.awt.*
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import javax.swing.*

interface CloakLibrary : Library {
    fun StartCloakClient(localHost: String, localPort: String, config: String, udp: Boolean)
    fun StopCloak()
}

data class Config(
    val localHost: String = "127.0.0.1",
    val localPort: String = "1984",
    val configText: String = "",
    val udp: Boolean = false
)

fun main() {
    val configFile = File("cloak_config.json")
    val gson = Gson()

    fun loadConfig(): Config {
        return if (configFile.exists()) {
            try {
                val reader = FileReader(configFile)
                gson.fromJson(reader, Config::class.java).also {
                    reader.close()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Config()
            }
        } else {
            Config()
        }
    }

    fun saveConfig(config: Config) {
        try {
            val writer = FileWriter(configFile)
            gson.toJson(config, writer)
            writer.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    val cloakLib = Native.loadLibrary(File(System.getProperty("user.dir"), "libs/cloak.so").absolutePath, CloakLibrary::class.java) as CloakLibrary

    val mainFrame = JFrame("Cloak VPN Client")
    mainFrame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
    val layout = GridBagLayout()
    val gbc = GridBagConstraints()
    mainFrame.layout = layout

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

    val savedConfig = loadConfig()
    localHostField.text = savedConfig.localHost
    localPortField.text = savedConfig.localPort
    configField.text = savedConfig.configText
    udpCheckbox.isSelected = savedConfig.udp

    fun addComponent(component: Component, x: Int, y: Int, width: Int, height: Int) {
        gbc.gridx = x
        gbc.gridy = y
        gbc.gridwidth = width
        gbc.gridheight = height
        gbc.fill = GridBagConstraints.BOTH
        gbc.insets = Insets(5, 5, 5, 5)
        mainFrame.add(component, gbc)
    }

    addComponent(localHostLabel, 0, 0, 1, 1)
    addComponent(localHostField, 1, 0, 2, 1)

    addComponent(localPortLabel, 0, 1, 1, 1)
    addComponent(localPortField, 1, 1, 2, 1)

    addComponent(configLabel, 0, 2, 1, 1)
    addComponent(configScrollPane, 1, 2, 2, 1)

    addComponent(udpCheckbox, 0, 3, 1, 1)

    val connectButton = JButton("Connect")
    connectButton.addActionListener {
        val localHost = localHostField.text
        val localPort = localPortField.text
        val config = configField.text
        val udp = udpCheckbox.isSelected

        saveConfig(Config(localHost, localPort, config, udp))

        object : SwingWorker<Void, Void>() {
            override fun doInBackground(): Void? {
                cloakLib.StartCloakClient(localHost, localPort, config, udp)
                return null
            }

            override fun done() {
                JOptionPane.showMessageDialog(mainFrame, "Connected to Cloak VPN!")
            }
        }.execute()
    }

    val disconnectButton = JButton("Disconnect")
    disconnectButton.addActionListener {
        object : SwingWorker<Void, Void>() {
            override fun doInBackground(): Void? {
                cloakLib.StopCloak()
                return null
            }

            override fun done() {
                JOptionPane.showMessageDialog(mainFrame, "Disconnected from Cloak VPN!")
            }
        }.execute()
    }

    addComponent(connectButton, 0, 4, 1, 1)
    addComponent(disconnectButton, 1, 4, 1, 1)

    val openOutlineButton = JButton("Open Outline Control")
    openOutlineButton.addActionListener {
        val outlineWindow = OutlineWindow()
        outlineWindow.isVisible = true
    }

    addComponent(openOutlineButton, 0, 5, 2, 1)

    mainFrame.pack()
    mainFrame.setLocationRelativeTo(null) 
    mainFrame.isVisible = true
}