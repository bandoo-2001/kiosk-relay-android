package io.github.kioskrelay.feature.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.kioskrelay.R
import io.github.kioskrelay.diagnostics.DiagnosticEvent
import java.text.DateFormat
import java.util.Date

data class DeviceDiagnostics(
    val appVersion: String,
    val androidVersion: String,
    val apiLevel: Int,
    val device: String,
    val webViewVersion: String,
)

@Composable
fun DiagnosticsScreen(
    device: DeviceDiagnostics,
    events: List<DiagnosticEvent>,
    onBack: () -> Unit,
    onExport: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.diagnostics_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = onClear) {
                    Text(stringResource(R.string.clear_diagnostics))
                }
                Button(onClick = onExport) {
                    Text(stringResource(R.string.export_diagnostics))
                }
                OutlinedButton(onClick = onBack) {
                    Text(stringResource(R.string.back_to_settings))
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                DeviceCard(device)
            }
            item {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.recent_events),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            if (events.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.no_diagnostic_events),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(
                    items = events.asReversed(),
                    key = { "${it.timestampEpochMillis}:${it.category}:${it.message}" },
                ) { event ->
                    EventCard(event)
                }
            }
            item { Spacer(Modifier.height(28.dp)) }
        }
    }
}

@Composable
private fun DeviceCard(device: DeviceDiagnostics) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.system_information),
                fontWeight = FontWeight.Bold,
            )
            Text(stringResource(R.string.app_version, device.appVersion))
            Text(
                stringResource(
                    R.string.android_version,
                    device.androidVersion,
                    device.apiLevel,
                ),
            )
            Text(stringResource(R.string.device_model, device.device))
            Text(stringResource(R.string.webview_version, device.webViewVersion))
        }
    }
}

@Composable
private fun EventCard(event: DiagnosticEvent) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(Modifier.fillMaxWidth()) {
                Text(
                    event.level.name,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    DateFormat.getDateTimeInstance().format(Date(event.timestampEpochMillis)),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(event.category, color = MaterialTheme.colorScheme.primary)
            Text(event.message, fontFamily = FontFamily.Monospace)
        }
    }
}
