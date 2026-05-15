package com.chakshu.features.debug

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chakshu.core.db.daos.ChunkDao
import com.chakshu.core.db.daos.IncidentDao
import com.chakshu.core.db.entities.ChunkEntity
import com.chakshu.core.db.entities.IncidentEntity
import com.chakshu.core.services.IncidentManager
import com.chakshu.core.utils.BatteryUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DebugViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val incidentDao: IncidentDao,
    private val chunkDao: ChunkDao,
    private val incidentManager: IncidentManager
) : ViewModel() {

    val activeIncident: StateFlow<IncidentEntity?> = incidentDao.getActiveFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val chunkCount: StateFlow<Int> = activeIncident
        .flatMapLatest { incident ->
            if (incident != null) chunkDao.getCountFlowForIncident(incident.id)
            else flowOf(0)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val lastChunk: StateFlow<ChunkEntity?> = activeIncident
        .flatMapLatest { incident ->
            if (incident != null) chunkDao.getLastChunkFlow(incident.id)
            else flowOf(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val batteryPct: StateFlow<Int> = MutableStateFlow(BatteryUtils.getBatteryPct(context))

    fun simulateTrigger() {
        viewModelScope.launch {
            incidentManager.startIncident()
        }
    }

    fun stopIncident() {
        viewModelScope.launch {
            val id = incidentManager.getActiveIncidentId() ?: activeIncident.value?.id ?: return@launch
            incidentManager.endIncident(id)
        }
    }
}
