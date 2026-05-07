package com.deepseekPRO.radiodbtool

import androidx.activity.compose.setContent
import androidx.activity.ComponentActivity
import android.os.Bundle
import com.deepseekPRO.radiodbtool.ui.compose.MainScreen
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    MainScreen()
                }
            }
        }
    }
}