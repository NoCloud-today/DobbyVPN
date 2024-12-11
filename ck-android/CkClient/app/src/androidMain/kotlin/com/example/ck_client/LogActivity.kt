package com.example.ck_client

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.dobby.ui.LogScreen

class LogActivity : ComponentActivity() {

    companion object {

        fun createIntent(context: Context): Intent {
            return Intent(context, LogActivity::class.java)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LogScreen() }
    }
}
