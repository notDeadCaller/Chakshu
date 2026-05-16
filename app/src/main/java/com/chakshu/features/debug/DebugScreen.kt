package com.chakshu.features.debug

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chakshu.R
import com.chakshu.core.platform.TriggerAccessibilityService
import com.chakshu.core.services.ChakshuForegroundService

@Composable
fun DebugScreen(
    modifier: Modifier = Modifier,
    vm: DebugViewModel = viewModel()
) {
    val context = LocalContext.current
    val serviceRunning by vm.serviceRunning.collectAsState()
    val accessibilityEnabled by vm.accessibilityEnabled.collectAsState()
    val incident by vm.activeIncident.collectAsState()
    val chunkCount by vm.chunkCount.collectAsState()
    val lastChunk by vm.lastChunk.collectAsState()
    val battery by vm.batteryPct.collectAsState()

    val accessibilitySettingsIntent: Intent = remember(context) {
        val serviceId = "${context.packageName}/${TriggerAccessibilityService::class.java.name}"
        val action = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            ChakshuForegroundService.ACTION_ACCESSIBILITY_DETAILS_SETTINGS
        else
            Settings.ACTION_ACCESSIBILITY_SETTINGS
        val intent = Intent(action)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.putExtra(ChakshuForegroundService.EXTRA_ACCESSIBILITY_SHORTCUT_TARGET, serviceId)
        }
        intent
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.debug_title),
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(Modifier.height(8.dp))

        Text(stringResource(R.string.debug_service_running, if (serviceRunning) "running" else "stopped"))
        Text(
            text = stringResource(
                if (accessibilityEnabled) R.string.debug_trigger_active
                else R.string.debug_trigger_disabled
            ),
            color = if (accessibilityEnabled) Color(0xFF2E7D32) else Color(0xFFD32F2F),
            modifier = if (!accessibilityEnabled)
                Modifier.clickable { context.startActivity(accessibilitySettingsIntent) }
            else
                Modifier
        )
        Text(stringResource(R.string.debug_incident_active, incident != null))
        Text(stringResource(R.string.debug_shake_sensitivity, vm.shakeThresholdG))
        Text(stringResource(R.string.debug_chunks_recorded, chunkCount))
        Text(stringResource(R.string.debug_last_hash, lastChunk?.sha256 ?: "none"))
        Text(stringResource(R.string.debug_battery, battery))

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { vm.simulateTrigger() },
            modifier = Modifier.fillMaxWidth(),
            enabled = incident == null
        ) {
            Text(stringResource(R.string.debug_simulate_trigger))
        }

        Button(
            onClick = { vm.stopIncident() },
            modifier = Modifier.fillMaxWidth(),
            enabled = incident != null,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
        ) {
            Text(stringResource(R.string.debug_stop_incident))
        }
    }
}
