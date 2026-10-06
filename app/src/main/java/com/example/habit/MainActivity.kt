package com.example.habit

import android.os.Bundle
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.example.habit.ui.navigation.HabitNavHost
import com.example.habit.ui.theme.AppTheme

class MainActivity : ComponentActivity() {
    private val dates get() = (application as HabitApplication).container.dates
    private val timeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) { dates.refresh() }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(this, timeReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onResume() { super.onResume(); dates.refresh() }
    override fun onStop() { unregisterReceiver(timeReceiver); super.onStop() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Every screen draws under the system bars; each one applies its own insets.
        enableEdgeToEdge()
        setContent {
            AppTheme {
                HabitNavHost(modifier = Modifier.fillMaxSize())
            }
        }
    }
}
