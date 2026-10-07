package br.com.estacaoqr.offline.kiosk

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController

object KioskController {
    fun hideSystemUi(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.window.setDecorFitsSystemWindows(false)
            activity.window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            activity.window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }
    }

    fun isDeviceOwner(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP &&
            (context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager)
                .isDeviceOwnerApp(context.packageName)

    fun configureDeviceOwner(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return
        val manager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        if (manager.isDeviceOwnerApp(context.packageName)) {
            manager.setLockTaskPackages(
                ComponentName(context, QrDeviceAdminReceiver::class.java),
                arrayOf(context.packageName)
            )
        }
    }

    fun start(activity: Activity): Boolean = runCatching {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return@runCatching false
        configureDeviceOwner(activity)
        activity.startLockTask()
        true
    }.getOrDefault(false)

    fun stop(activity: Activity): Boolean = runCatching {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return@runCatching false
        activity.stopLockTask()
        true
    }.getOrDefault(false)
}
