package io.github.kioskrelay.startup

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import io.github.kioskrelay.R

/** Shared by onboarding and runtime settings; the grant stays under system control. */
@Composable
fun StartupPermissionSettings() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
    val context = LocalContext.current
    var granted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var settingsUnavailable by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        granted = Settings.canDrawOverlays(context)
    }
    Text(stringResource(if (granted) R.string.startup_access_granted else R.string.startup_access_needed))
    Text(stringResource(R.string.startup_access_hint))
    OutlinedButton(onClick = {
        val intents = listOf(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")),
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION),
            Intent(Settings.ACTION_SETTINGS),
        )
        settingsUnavailable = !intents.any { intent ->
            runCatching { context.startActivity(intent) }.isSuccess
        }
    }) {
        Text(stringResource(R.string.startup_access_open))
    }
    if (settingsUnavailable) Text(stringResource(R.string.startup_settings_unavailable))
}
