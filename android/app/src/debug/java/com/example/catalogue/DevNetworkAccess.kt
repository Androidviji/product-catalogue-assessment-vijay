package com.example.catalogue

import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

/**
 * DEBUG BUILD ONLY (src/debug). The release build has a no-op version in src/release.
 *
 * The debug app talks to the backend on the development computer through 10.0.2.2, which is a
 * local-network address. From Android 17 (API 37) an app needs the ACCESS_LOCAL_NETWORK runtime
 * permission for that; without it connections are silently dropped and time out.
 * So the debug build asks for it once, before showing content. A production backend is a public
 * HTTPS host, which needs no such permission.
 */
object DevNetworkAccess {

    private const val ACCESS_LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"
    private const val API_ANDROID_17 = 37

    /** Calls [onReady] once local-network access is granted, denied, or not needed. Call from onCreate. */
    fun ensure(activity: ComponentActivity, onReady: () -> Unit) {
        val needed = Build.VERSION.SDK_INT >= API_ANDROID_17 &&
            ContextCompat.checkSelfPermission(activity, ACCESS_LOCAL_NETWORK) != PackageManager.PERMISSION_GRANTED
        if (!needed) {
            onReady()
            return
        }
        // If denied, the app still opens; requests then fail and show the normal error + Retry.
        activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { onReady() }
            .launch(ACCESS_LOCAL_NETWORK)
    }
}
