package interop

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Platform
import com.sun.jna.NativeLibrary
import java.io.File

interface OutlineLibrary : Library {
    fun StartOutline(key: String)
    fun StopOutline()
}

object OutlineLib {
    private var INSTANCE: OutlineLibrary? = null

    init {
        try {
            val libName = when {
                Platform.isMac() -> "outline"
                Platform.isLinux() -> "outline"
                Platform.isWindows() -> "outline"
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

            println("Attempting to load library from path: $libPath")
            val nativeLibrary = NativeLibrary.getInstance(libPath)
            INSTANCE = Native.load(libPath, OutlineLibrary::class.java)

            println("Library loaded successfully.")
        } catch (e: Exception) {
            println("Failed to load library: ${e.message}")
            e.printStackTrace()
        }
    }

    fun startOutline(key: String) {
        try {
            if (INSTANCE == null) {
                println("Library not loaded. Cannot call StartOutline.")
                return
            }
            INSTANCE!!.StartOutline(key)
            println("StartOutline called successfully.")
        } catch (e: UnsatisfiedLinkError) {
            println("Failed to call StartOutline: ${e.message}")
            e.printStackTrace()
        } catch (e: Exception) {
            println("An error occurred while calling StartOutline: ${e.message}")
            e.printStackTrace()
        }
    }

    fun stopOutline() {
        try {
            if (INSTANCE == null) {
                println("Library not loaded. Cannot call StopOutline.")
                return
            }
            INSTANCE!!.StopOutline()
            println("StopOutline called successfully.")
        } catch (e: UnsatisfiedLinkError) {
            println("Failed to call StopOutline: ${e.message}")
            e.printStackTrace()
        } catch (e: Exception) {
            println("An error occurred while calling StopOutline: ${e.message}")
            e.printStackTrace()
        }
    }
}