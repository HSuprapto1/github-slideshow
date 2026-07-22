package com.hardian.phonetracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import com.hardian.phonetracker.ui.PhoneTrackerRoot
import com.hardian.phonetracker.ui.theme.PhoneTrackerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            PhoneTrackerTheme {
                PhoneTrackerRoot()
            }
        }
    }
}
