package com.example.catalogue

import androidx.activity.ComponentActivity

/** Release build: talks only to a public HTTPS backend, so no local-network permission is needed. */
object DevNetworkAccess {
    fun ensure(activity: ComponentActivity, onReady: () -> Unit) = onReady()
}
