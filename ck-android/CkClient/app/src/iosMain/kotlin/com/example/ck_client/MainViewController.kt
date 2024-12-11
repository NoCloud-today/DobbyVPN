package com.example.ck_client

import KoinInitializer
import androidx.compose.ui.window.ComposeUIViewController
import com.dobby.ui.LogScreen

fun MainViewController() = ComposeUIViewController(
    configure = { KoinInitializer().init() }
) {
    LogScreen()
}
