package com.rynekryz.rynemaps

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.rynekryz.rynemaps.ui.MapViewModel
import com.rynekryz.rynemaps.ui.RyMapsApp
import com.rynekryz.rynemaps.ui.theme.RyMapsTheme
import org.maplibre.android.MapLibre

class MainActivity : ComponentActivity() {
    private val vm: MapViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        MapLibre.getInstance(this)
        if (savedInstanceState == null) handle(intent)
        setContent {
            RyMapsTheme {
                RyMapsApp(vm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) intent.data?.let { vm.openUri(it) }
    }
}
