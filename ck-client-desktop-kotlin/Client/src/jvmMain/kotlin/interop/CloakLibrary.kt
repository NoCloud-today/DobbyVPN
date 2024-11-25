package interop

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Platform
import com.sun.jna.NativeLibrary
import java.io.File

interface CloakLibrary : Library {
    fun StartCloakClient(localHost: String, localPort: String, config: String, udp: Boolean)
    fun StopCloakClient()
}

object CloakLib {
    private var INSTANCE: CloakLibrary? = null

    init {
        try {
            val libName = when {
                Platform.isMac() -> "cloak"
                Platform.isLinux() -> "cloak"
                Platform.isWindows() -> "cloak"
                else -> throw UnsupportedOperationException("Unsupported OS")
            }

            val libExtension = when {
                Platform.isMac() -> ".dylib"
                Platform.isLinux() -> ".so"
                Platform.isWindows() -> ".dll"
                else -> ""
            }

            val architecture = System.getProperty("os.arch")
            val libFileName = when {
                Platform.isMac() && architecture.contains("aarch64") -> "lib${libName}_arm64$libExtension"
                Platform.isMac() && architecture.contains("x86_64") -> "lib${libName}_x86_64$libExtension"
                Platform.isLinux() -> "lib${libName}_linux$libExtension"
                Platform.isWindows() -> "lib${libName}_windows$libExtension"
                else -> throw UnsupportedOperationException("Unsupported architecture")
            }

            val libPath = File(System.getProperty("user.dir"), "libs/$libFileName").absolutePath

            println("Attempting to load Cloak library from path: $libPath")
            val nativeLibrary = NativeLibrary.getInstance(libPath)
            INSTANCE = Native.load(libPath, CloakLibrary::class.java)

            println("Cloak library loaded successfully.")
        } catch (e: Exception) {
            println("Failed to load Cloak library: ${e.message}")
            e.printStackTrace()
        }
    }

    fun startCloakClient(localHost: String, localPort: String, config: String, udp: Boolean) {
        try {
            if (INSTANCE == null) {
                println("Cloak library not loaded. Cannot call StartCloakClient.")
                return
            }
            println("Calling StartCloakClient with parameters: localHost=$localHost, localPort=$localPort, udp=$udp")
            INSTANCE!!.StartCloakClient(localHost, localPort, config, udp)
            println("StartCloakClient called successfully.")
        } catch (e: UnsatisfiedLinkError) {
            println("Failed to call StartCloakClient: ${e.message}")
            e.printStackTrace()
        } catch (e: Exception) {
            println("An error occurred while calling StartCloakClient: ${e.message}")
            e.printStackTrace()
        }
    }

    fun stopCloakClient() {
        try {
            if (INSTANCE == null) {
                println("Cloak library not loaded. Cannot call StopCloakClient.")
                return
            }
            println("Calling StopCloakClient")
            INSTANCE!!.StopCloakClient()
            println("StopCloakClient called successfully.")
        } catch (e: UnsatisfiedLinkError) {
            println("Failed to call StopCloakClient: ${e.message}")
            e.printStackTrace()
        } catch (e: Exception) {
            println("An error occurred while calling StopCloakClient: ${e.message}")
            e.printStackTrace()
        }
    }
}