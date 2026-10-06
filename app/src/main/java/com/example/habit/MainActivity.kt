package com.example.habit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.example.habit.ui.navigation.HabitNavHost
import com.example.habit.ui.theme.HabitTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Every screen draws under the system bars; each one applies its own insets.
        enableEdgeToEdge()
        setContent {
            HabitTheme {
                HabitNavHost(modifier = Modifier.fillMaxSize())
            }
        }
    }
}
