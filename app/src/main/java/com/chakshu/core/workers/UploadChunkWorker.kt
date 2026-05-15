package com.chakshu.core.workers

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class UploadChunkWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val chunkId = inputData.getString(KEY_CHUNK_ID) ?: return Result.failure()
        // Phase 3: upload to web3.storage, stamp with OpenTimestamps, sync to Supabase
        Log.d(TAG, "would upload $chunkId")
        return Result.success()
    }

    companion object {
        const val KEY_CHUNK_ID = "chunk_id"
        private const val TAG = "UploadChunkWorker"
    }
}
