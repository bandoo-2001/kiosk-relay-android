package io.github.kioskrelay.startup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        val applicationContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val request = runCatching {
                    StartupRuntime.provider(applicationContext).load()
                }.getOrDefault(StartupRequest.Disabled)
                StartupLauncher(StartupRuntime.notifier()).launch(applicationContext, request)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
