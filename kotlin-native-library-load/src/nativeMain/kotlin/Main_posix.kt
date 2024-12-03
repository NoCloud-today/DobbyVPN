//import kotlinx.cinterop.*
//import platform.posix.*
//
//@OptIn(ExperimentalForeignApi::class)
//fun main() {
//    val libraryPath = "path to liboutline_linux.so or liboutline_arm64.dylib"
//    val library = dlopen(libraryPath, RTLD_LAZY)
//    if (library == null) {
//        perror("Failed to load library")
//        return
//    }
//    println("Press enter to end!")
//
//    val startOutlineFunc = dlsym(library, "StartOutline")
//    val startOutline = startOutlineFunc?.reinterpret<CFunction<(CValuesRef<ByteVar>?) -> Unit>>()
//
//    if (startOutline != null) {
//        try {
//            startOutline("your ss key".cstr)
//            println("StartOutline called successfully.")
//        } catch (e: Exception) {
//            println("An error occurred while calling StartOutline: ${e.message}")
//            e.printStackTrace()
//        }
//    } else {
//        println("Failed to load StartOutline function")
//    }
//    val name = readln()
//    dlclose(library)
//}
