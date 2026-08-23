package com.kmjs.virtualcamera

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.ui.theme.MyApplicationTheme
import com.kmjs.virtualcamera.ui.KmjsMainScreen
import com.kmjs.virtualcamera.ui.MainViewModel
import com.kmjs.virtualcamera.util.KmjsLog

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        KmjsLog.i(KmjsLog.TAG_GENERAL, "KMJS MainActivity onCreate")

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    KmjsMainScreen(viewModel = viewModel)
                }
            }
        }
    }
}
