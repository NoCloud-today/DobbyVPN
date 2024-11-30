package com.dobby.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun LogScreen(
    modifier: Modifier = Modifier,
    logMessages: List<String>,
    onCopyToClipBoard: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        Button(
            onClick = { onCopyToClipBoard() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Copy logs to clipboard")
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(8.dp)
        ) {
            items(logMessages) { message ->
                Text(text = message, modifier = Modifier.padding(4.dp))
            }
        }
    }
}

