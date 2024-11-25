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
fun LogWindow() {
    val scrollState = rememberScrollState()
    var logContent by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var isUserScrolling by remember { mutableStateOf(false) }
    var lastScrollPosition by remember { mutableStateOf(0) }

    // Обновление логов
    LaunchedEffect(Unit) {
        coroutineScope.launch(Dispatchers.IO) {
            val logFile = File("logs.txt")
            while (true) {
                if (logFile.exists()) {
                    val newLogContent = logFile.readText()
                    if (newLogContent != logContent) {
                        logContent = newLogContent
                    }
                } else {
                    logContent = "Log file does not exist."
                }
                delay(1000) // Обновление каждые 1 секунду
            }
        }
    }

    // Автопрокрутка вниз при обновлении логов
    LaunchedEffect(logContent) {
        if (!isUserScrolling) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    // Отслеживание скроллинга пользователя
    LaunchedEffect(scrollState.value) {
        val maxScroll = scrollState.maxValue
        val currentScroll = scrollState.value

        // Если пользователь прокрутил вверх, то отмечаем это
        isUserScrolling = currentScroll < maxScroll

        // Если пользователь прокрутил вниз до конца, включаем автопрокрутку
        if (currentScroll >= maxScroll) {
            isUserScrolling = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            "Logs",
            style = MaterialTheme.typography.h6.copy(color = Color.White),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        TextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Search") },
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.textFieldColors(
                textColor = Color.White,
                backgroundColor = Color.Transparent,
                cursorColor = Color.White,
                focusedIndicatorColor = Color.White,
                unfocusedIndicatorColor = Color.Gray
            )
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState)
        ) {
            val filteredLogContent = if (searchQuery.isEmpty()) {
                logContent
            } else {
                logContent.lines().filter { it.contains(searchQuery, ignoreCase = true) }.joinToString("\n")
            }

            Text(
                text = filteredLogContent,
                style = TextStyle(color = Color.White, fontFamily = FontFamily.Monospace),
                modifier = Modifier.align(Alignment.TopStart)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = {
            val logFile = File("logs.txt")
            if (logFile.exists()) {
                logFile.writeText("")
                logContent = ""
            }
        }) {
            Text("Clear Logs")
        }
    }
}