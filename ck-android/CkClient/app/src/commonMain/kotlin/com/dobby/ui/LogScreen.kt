package com.dobby.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dobby.logs.LogsViewModel
import org.koin.compose.KoinContext
import org.koin.compose.currentKoinScope

@Composable
fun LogScreen(
    modifier: Modifier = Modifier
) {
    KoinContext {
        val viewModel: LogsViewModel = koinViewModel()
        val uiState by viewModel.uiState.collectAsState()

        MaterialTheme {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                content = {
                    Column(
                        modifier = modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        Button(
                            onClick = { viewModel.copyLogsToClipBoard() },
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Black,
                                contentColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Copy logs to clipboard")
                        }

                        Button(
                            onClick = { viewModel.clearLogs() },
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color.Black
                            ),
                            modifier = Modifier.fillMaxWidth()
                                .border(1.dp, Color.Black, shape = RoundedCornerShape(6.dp)),
                        ) {
                            Text("Clear Logs")
                        }

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(8.dp)
                        ) {
                            items(uiState.logMessages) { message ->
                                // some important logs contain this
                                val isBald = message.contains("!!!")
                                Text(
                                    fontWeight = FontWeight(if (isBald) 700 else 400),
                                    text = message,
                                    modifier = Modifier.padding(4.dp)
                                )
                            }
                        }
                    }
                }
            )
        }
    }
}

@Composable
inline fun <reified T : ViewModel> koinViewModel(): T {
    val scope = currentKoinScope()
    return viewModel {
        scope.get<T>()
    }
}
