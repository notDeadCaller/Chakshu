package com.chakshu

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chakshu.features.debug.DebugViewModel
import com.chakshu.ui.theme.ChakshuTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ChakshuTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    DebugScreen(modifier = Modifier.padding(padding))
                }
            }
        }
    }
}

@Composable
fun DebugScreen(
    modifier: Modifier = Modifier,
    vm: DebugViewModel = viewModel()
) {
    val incident by vm.activeIncident.collectAsState()
    val chunkCount by vm.chunkCount.collectAsState()
    val lastChunk by vm.lastChunk.collectAsState()
    val battery by vm.batteryPct.collectAsState()

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

        Text(stringResource(R.string.debug_incident_active, incident != null))
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
