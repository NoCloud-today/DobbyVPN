import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import ui.MainScreen

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Combined VPN Client") {
        MainScreen()
    }
}