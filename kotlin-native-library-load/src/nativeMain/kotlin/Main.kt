import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

import kotlinx.cinterop.*
import platform.windows.*

@OptIn(ExperimentalForeignApi::class)
fun main() {
    val library = LoadLibraryA("path to liboutline_windows.dll")
    if (library == null) {
        error("Failed to load library: ${GetLastError()}")
    }
    println("Press enter to end!")
    val startOutlineFunc = GetProcAddress(library, "StartOutline")
    val startOutline = startOutlineFunc?.reinterpret<CFunction<(CValuesRef<ByteVar>?) -> Unit>>()
    try {
        if (startOutline != null) {
            startOutline("your ss key".cstr)
        } else {
            println("Failed to load library")
        }
        println("StartOutline called successfully.")
    } catch (e: Exception) {
        println("An error occurred while calling StartOutline: ${e.message}")
        e.printStackTrace()
    }
    val name = readln()
//    StopOutline()
}