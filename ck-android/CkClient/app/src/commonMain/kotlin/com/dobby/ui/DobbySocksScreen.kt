package com.dobby.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun DobbySocksScreen(
    modifier: Modifier = Modifier,
    isConnected: Boolean = false,
    initialConfig: String = "",
    initialKey: String = "",
    onConnectionButtonClick: (String?, String) -> Unit = { _, _ -> },
    onShowLogsClick: () -> Unit = {}
) {
    val scrollState = rememberScrollState()

    val isEnabled = remember { mutableStateOf(true) }
    var cloakJson by remember { mutableStateOf(initialConfig) }
    var apiKey by remember { mutableStateOf(initialKey) }
    val focusRequester1 = remember { FocusRequester() }

    // Conditionally show the TextField based on Switch state
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.Center
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()  // Ensure the row takes up the full width
                .padding(8.dp), // Padding around the entire Row
            verticalAlignment = Alignment.CenterVertically // Center items vertically in the Row
        ) {
            // Text "Status" at the start
            Text(
                text = "Status",
                fontSize = 24.sp,
                color = Color.Black,
                modifier = Modifier.padding(end = 8.dp)  // Space between Text and TagChip
            )

            // Spacer with weight to push the TagChip to the end of the row
            Spacer(modifier = Modifier.weight(1f))

            TagChip(
                tagText = if (isConnected) "connected" else "disconnected",
                color = if (isConnected) 0xFFDCFCE7 else 0xFFFEE2E2
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {

            Switch(
                modifier = Modifier
                    .size(44.dp, 24.dp),
                checked = isEnabled.value,
                onCheckedChange = { isEnabled.value = it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    uncheckedThumbColor = Color.White,
                    checkedTrackColor = Color.Black,
                    uncheckedTrackColor = Color(0xFFE2E8F0)
                )
            )
            Text(text = "Enable cloak?", modifier = Modifier.padding(horizontal = 8.dp))
        }
        TextField(
            value = cloakJson,
            onValueChange = { cloakJson = it },
            label = { Text("Enter Cloak JSON") },
            singleLine = false,
            colors = TextFieldDefaults.colors(
                unfocusedPlaceholderColor = Color(0xFF94A3B8),

                ),
            enabled = isEnabled.value,
            keyboardActions = KeyboardActions(
                onDone = {
                    // Move focus to the second TextField when Enter/Done is pressed
                    focusRequester1.requestFocus()
                }
            ),
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        TextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text("Enter outline config") },
            singleLine = false,
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .focusRequester(focusRequester1)
        )
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { onConnectionButtonClick(cloakJson, apiKey) },
            shape = RoundedCornerShape(6.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Black,
                contentColor = Color.White
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isConnected) "Disconnect" else "Connect")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onShowLogsClick,
            shape = RoundedCornerShape(6.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = Color.Black
            ),
            modifier = Modifier.fillMaxWidth()
                .border(1.dp, Color.Black, shape = RoundedCornerShape(6.dp)),
        ) {
            Text("Show Logs")
        }
    }
}

@Preview
@Composable
fun PreviewMyScreen() {
    DobbySocksScreen(
        onConnectionButtonClick = { _, _ -> },
        onShowLogsClick = {}
    )
}

