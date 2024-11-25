package ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun LogWindow(onClose: () -> Unit) {
    val scrollState = rememberScrollState()
    var logContent by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        coroutineScope.launch(Dispatchers.IO) {
            val logFile = File("logs.txt")
            while (true) {
                if (logFile.exists()) {
                    logContent = logFile.readText()
                } else {
                    logContent = "Log file does not exist."
                }
                delay(1000) // ббновление каждые 1 секунду
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFFCDCDCD)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text(
                "Logs",
                style = MaterialTheme.typography.h6.copy(color = Color.White),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
            ) {
                Text(
                    text = logContent,
                    style = TextStyle(color = Color.White, fontFamily = FontFamily.Monospace),
                    color = Color(0xFF194E2E),
                    modifier = Modifier.align(Alignment.TopStart)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(onClick = {
                    val logFile = File("logs.txt")
                    if (logFile.exists()) {
                        logFile.writeText("")
                        logContent = ""
                    }
                }) {
                    Text("Clear Logs")
                }
                Button(onClick = onClose) {
                    Text("Close")
                }
            }
        }
    }
}