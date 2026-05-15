package com.chakshu.core.services

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.chakshu.core.db.daos.ChunkDao
import com.chakshu.core.db.entities.ChunkEntity
import com.chakshu.core.utils.CryptoUtils
import com.chakshu.core.workers.UploadChunkWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecordingManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val chunkDao: ChunkDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var rotationJob: Job? = null

    private var recorder: MediaRecorder? = null
    private var currentIncidentId: String? = null
    private var chunkIndex = 0
    private var chunkStartTime = 0L
    private var currentFilePath: String? = null

    fun startRecording(incidentId: String) {
        if (rotationJob?.isActive == true) return
        currentIncidentId = incidentId
        chunkIndex = 0
        startNextChunk()
        rotationJob = scope.launch {
            while (isActive) {
                delay(CHUNK_DURATION_MS)
                rotateChunk()
            }
        }
    }

    private fun startNextChunk() {
        val incidentId = currentIncidentId ?: return
        val dir = File(context.filesDir, "incidents/$incidentId").also { it.mkdirs() }
        val filePath = "${dir.absolutePath}/chunk_$chunkIndex.aac"
        currentFilePath = filePath
        chunkStartTime = System.currentTimeMillis()

        val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        try {
            rec.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(8_000)
                setAudioChannels(1)
                setAudioEncodingBitRate(32_000)
                setOutputFile(filePath)
                prepare()
                start()
            }
            recorder = rec
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recorder for chunk $chunkIndex", e)
            rec.release()
        }
    }

    private suspend fun rotateChunk() {
        val incidentId = currentIncidentId ?: return
        val filePath = currentFilePath ?: return
        val duration = System.currentTimeMillis() - chunkStartTime
        val index = chunkIndex

        try {
            recorder?.stop()
            recorder?.release()
            recorder = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recorder at chunk $index", e)
        }

        val file = File(filePath)
        val sha256 = if (file.exists()) CryptoUtils.computeSha256(file) else null
        val chunkId = UUID.randomUUID().toString()

        chunkDao.insert(
            ChunkEntity(
                id = chunkId,
                incidentId = incidentId,
                chunkIndex = index,
                filePath = filePath,
                durationMs = duration,
                sha256 = sha256,
                ipfsCid = null,
                otsProof = null,
                lat = null,
                lon = null,
                recordedAt = chunkStartTime,
                uploadedAt = null,
                status = "PENDING"
            )
        )

        WorkManager.getInstance(context).enqueue(
            OneTimeWorkRequestBuilder<UploadChunkWorker>()
                .setInputData(workDataOf(UploadChunkWorker.KEY_CHUNK_ID to chunkId))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
        )

        chunkIndex++
        startNextChunk()
    }

    fun stopRecording() {
        rotationJob?.cancel()
        rotationJob = null

        val incidentId = currentIncidentId ?: return
        val filePath = currentFilePath ?: return
        val duration = System.currentTimeMillis() - chunkStartTime
        val index = chunkIndex

        try {
            recorder?.stop()
            recorder?.release()
            recorder = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recorder on stopRecording()", e)
        }

        // Partial chunk: write to Room so evidence is not lost even if partial
        scope.launch {
            val file = File(filePath)
            val sha256 = if (file.exists()) CryptoUtils.computeSha256(file) else null
            chunkDao.insert(
                ChunkEntity(
                    id = UUID.randomUUID().toString(),
                    incidentId = incidentId,
                    chunkIndex = index,
                    filePath = filePath,
                    durationMs = duration,
                    sha256 = sha256,
                    ipfsCid = null,
                    otsProof = null,
                    lat = null,
                    lon = null,
                    recordedAt = chunkStartTime,
                    uploadedAt = null,
                    status = "PENDING"
                )
            )
        }

        currentIncidentId = null
        currentFilePath = null
    }

    companion object {
        private const val TAG = "RecordingManager"
        private const val CHUNK_DURATION_MS = 30_000L
    }
}
