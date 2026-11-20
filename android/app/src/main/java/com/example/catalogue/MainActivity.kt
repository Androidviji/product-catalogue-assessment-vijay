package com.example.catalogue

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.catalogue.presentation.navigation.CatalogueNavHost
import com.example.catalogue.presentation.theme.CatalogueTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as CatalogueApplication).container
        // Debug only: on Android 17+, ask for local-network access to reach the backend on 10.0.2.2.
        // In release this calls back immediately (see src/release/DevNetworkAccess.kt).
        DevNetworkAccess.ensure(this) {
            setContent {
                CatalogueTheme {
                    CatalogueNavHost(container)
                }
            }
        }
    }
}
