package io.github.kioskrelay.startup

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import io.github.kioskrelay.R

enum class StartupLaunchMode {
    DISABLED,
    DIRECT_ACTIVITY,
    NOTIFICATION_FALLBACK,
    ;

    companion object {
        fun resolve(apiLevel: Int, enabled: Boolean): StartupLaunchMode = when {
            !enabled -> DISABLED
            apiLevel <= Build.VERSION_CODES.P -> DIRECT_ACTIVITY
            else -> NOTIFICATION_FALLBACK
        }
    }
}

fun interface StartupFallbackNotifier {
    /**
     * Returns true when a user-visible fallback was posted.
     */
    fun show(context: Context, request: StartupRequest): Boolean
}

class StartupLauncher(
    private val fallbackNotifier: StartupFallbackNotifier,
) {
    fun launch(context: Context, request: StartupRequest): StartupLaunchMode {
        val mode = StartupLaunchMode.resolve(Build.VERSION.SDK_INT, request.enabled)
        when (mode) {
            StartupLaunchMode.DISABLED -> Unit
            StartupLaunchMode.DIRECT_ACTIVITY -> launchMainActivity(context, request)
            StartupLaunchMode.NOTIFICATION_FALLBACK ->
                fallbackNotifier.show(context.applicationContext, request)
        }
        return mode
    }

    private fun launchMainActivity(context: Context, request: StartupRequest) {
        val intent = mainLaunchIntent(context, request) ?: return
        runCatching { context.startActivity(intent) }
    }

    companion object {
        const val EXTRA_LAUNCHED_FROM_BOOT =
            "io.github.kioskrelay.extra.LAUNCHED_FROM_BOOT"
        const val EXTRA_BOOT_DELAY_SECONDS =
            "io.github.kioskrelay.extra.BOOT_DELAY_SECONDS"

        internal fun mainLaunchIntent(
            context: Context,
            request: StartupRequest,
        ): Intent? =
            context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(EXTRA_LAUNCHED_FROM_BOOT, true)
                putExtra(EXTRA_BOOT_DELAY_SECONDS, request.delaySeconds)
            }
    }
}

object SystemStartupFallbackNotifier : StartupFallbackNotifier {
    private const val CHANNEL_ID = "kiosk_startup_restore"
    private const val NOTIFICATION_ID = 1_041
    private const val PENDING_INTENT_REQUEST_CODE = 1_042

    override fun show(context: Context, request: StartupRequest): Boolean {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.startup_notification_channel),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
        }
        val launchIntent = StartupLauncher.mainLaunchIntent(context, request) ?: return false
        val pendingIntent = PendingIntent.getActivity(
            context,
            PENDING_INTENT_REQUEST_CODE,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val smallIcon = context.applicationInfo.icon
        if (smallIcon == 0) return false

        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }.setSmallIcon(smallIcon)
            .setContentTitle(context.applicationInfo.loadLabel(context.packageManager))
            .setContentText(context.getString(R.string.startup_notification_text))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()

        return runCatching {
            manager.notify(NOTIFICATION_ID, notification)
            true
        }.getOrDefault(false)
    }
}
