package com.teamc.fbbmmanager.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Observer
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.gson.Gson
import com.teamc.fbbmmanager.FbBmApp
import com.teamc.fbbmmanager.data.model.FbPage
import com.teamc.fbbmmanager.data.model.UploadItemStatus
import com.teamc.fbbmmanager.data.model.UploadState
import com.teamc.fbbmmanager.data.work.ReelUploadJob
import com.teamc.fbbmmanager.data.work.UploadWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Bulk mode vs per-page video mapping mode. */
enum class UploadMode { BULK, PER_PAGE }

/**
 * Owns the upload flow: video picking, page selection / per-page mapping,
 * enqueueing the WorkManager job and observing per-page progress.
 * Activity-scoped so state survives navigation.
 */
class UploadViewModel(app: Application) : AndroidViewModel(app) {

    private val gson = Gson()

    var mode by mutableStateOf(UploadMode.BULK)

    /** Bulk mode: one video for all selected pages. */
    var bulkVideoUri by mutableStateOf<Uri?>(null)
        private set

    /** Bulk video size, for pre-upload validation messaging. */
    var bulkVideoSizeBytes by mutableStateOf(0L)
        private set

    /** Per-page mode: pageId -> video Uri. */
    private val perPageVideos = mutableStateMapOf<String, Uri>()
    private val perPageSizes = mutableStateMapOf<String, Long>()

    var description by mutableStateOf("")
        private set

    var selectedPageIds = mutableStateOf<Set<String>>(emptySet())
        private set

    /** pageId -> live upload status (from WorkManager progress). */
    val statuses = mutableStateMapOf<String, UploadItemStatus>()

    var busy by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    var workId by mutableStateOf<UUID?>(null)
        private set

    private var workObserver: Observer<List<WorkInfo>>? = null

    fun onDescriptionChange(v: String) {
        description = v
    }

    fun setBulkVideo(context: Context, uri: Uri?) {
        bulkVideoUri = uri
        bulkVideoSizeBytes = uri?.let { sizeOf(context, it) } ?: 0L
    }

    fun setVideoForPage(context: Context, pageId: String, uri: Uri) {
        perPageVideos[pageId] = uri
        perPageSizes[pageId] = sizeOf(context, uri)
    }

    fun videoForPage(pageId: String): Uri? =
        if (mode == UploadMode.BULK) bulkVideoUri else perPageVideos[pageId]

    fun videoSizeForPage(pageId: String): Long =
        if (mode == UploadMode.BULK) bulkVideoSizeBytes else perPageSizes[pageId] ?: 0L

    fun togglePage(pageId: String) {
        selectedPageIds.value =
            if (pageId in selectedPageIds.value) selectedPageIds.value - pageId
            else selectedPageIds.value + pageId
    }

    fun selectAll(pages: List<FbPage>) {
        selectedPageIds.value = pages.map { it.id }.toSet()
    }

    fun clearSelection() {
        selectedPageIds.value = emptySet()
    }

    fun isSelected(pageId: String): Boolean = pageId in selectedPageIds.value

    /** Targets that are actually ready to upload (page + resolved video). */
    fun readyTargets(pages: List<FbPage>): List<Triple<FbPage, Uri, String>> =
        pages.filter { it.id in selectedPageIds.value }
            .mapNotNull { page ->
                val uri = videoForPage(page.id) ?: return@mapNotNull null
                val token = page.accessToken ?: return@mapNotNull null
                Triple(page, uri, token)
            }

    /** Human warning for oversized videos (Meta Reels are max 90s; huge files fail). */
    fun sizeWarning(bytes: Long): String? = when {
        bytes <= 0L -> null
        bytes > HARD_SIZE_LIMIT -> "Video is too large (${formatBytes(bytes)}). Reels uploads above ${formatBytes(HARD_SIZE_LIMIT)} are not supported — pick a smaller file."
        bytes > WARN_SIZE_LIMIT -> "Large file (${formatBytes(bytes)}) — upload may take a while on mobile data."
        else -> null
    }

    /**
     * Copies picked videos into app cache (workers can't rely on persisted
     * Uri grants across process restarts) and enqueues the upload work.
     */
    fun startUpload(context: Context, pages: List<FbPage>) {
        val targets = readyTargets(pages)
        if (targets.isEmpty()) {
            error = "Select at least one page with a video and a page access token."
            return
        }
        error = null
        busy = true
        viewModelScope.launch {
            val jobs = withContext(Dispatchers.IO) {
                targets.mapNotNull { (page, uri, token) ->
                    val file = copyToCache(context, uri) ?: return@mapNotNull null
                    if (file.length() > HARD_SIZE_LIMIT) {
                        file.delete()
                        return@mapNotNull null
                    }
                    ReelUploadJob(
                        pageId = page.id,
                        pageName = page.name,
                        pageToken = token,
                        filePath = file.absolutePath,
                        description = description
                    )
                }
            }
            if (jobs.isEmpty()) {
                busy = false
                error = "Could not read the selected video file(s), or a file exceeds the size limit."
                return@launch
            }
            enqueueJobs(context, jobs)
            busy = false
        }
    }

    /** Re-enqueues a single failed page (individual retry button). */
    fun retryPage(context: Context, pages: List<FbPage>, pageId: String) {
        val page = pages.find { it.id == pageId } ?: return
        val uri = videoForPage(pageId) ?: return
        val token = page.accessToken ?: return
        busy = true
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) { copyToCache(context, uri) }
            busy = false
            if (file == null) {
                error = "Could not read the video file for retry."
                return@launch
            }
            statuses[pageId] = UploadItemStatus(pageId, page.name, UploadState.QUEUED, 0)
            enqueueJobs(
                context,
                listOf(
                    ReelUploadJob(pageId, page.name, token, file.absolutePath, description)
                )
            )
        }
    }

    private fun enqueueJobs(context: Context, jobs: List<ReelUploadJob>) {
        statuses.clear()
        jobs.forEach { job ->
            statuses[job.pageId] =
                UploadItemStatus(job.pageId, job.pageName, UploadState.QUEUED, 0)
        }
        val input = Data.Builder()
            .putString(UploadWorker.KEY_JOBS, gson.toJson(jobs))
            .build()
        val request = OneTimeWorkRequestBuilder<UploadWorker>()
            .setInputData(input)
            .addTag(UploadWorker.TAG)
            .build()
        workId = request.id
        WorkManager.getInstance(context).enqueueUniqueWork(
            UploadWorker.UNIQUE_WORK,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request
        )
        observeWork(context)
    }

    private fun observeWork(context: Context) {
        // Remove any previous observer first (no leaks across re-enqueues).
        detachObserver(context)
        val wm = WorkManager.getInstance(context)
        val observer = Observer<List<WorkInfo>> { infos ->
            val info = infos.maxByOrNull { it.runAttemptCount } ?: return@Observer
            applyProgress(info.progress)
            if (info.state.isFinished) {
                applyFinalResults(info.outputData)
                // Finished — detach so we don't leak the observer forever.
                detachObserver(context)
            }
        }
        workObserver = observer
        wm.getWorkInfosForUniqueWorkLiveData(UploadWorker.UNIQUE_WORK)
            .observeForever(observer)
    }

    private fun detachObserver(context: Context) {
        val observer = workObserver ?: return
        runCatching {
            WorkManager.getInstance(context)
                .getWorkInfosForUniqueWorkLiveData(UploadWorker.UNIQUE_WORK)
                .removeObserver(observer)
        }
        workObserver = null
    }

    override fun onCleared() {
        super.onCleared()
        runCatching {
            workObserver?.let { observer ->
                // Application context is safe to hold here.
                WorkManager.getInstance(getApplication())
                    .getWorkInfosForUniqueWorkLiveData(UploadWorker.UNIQUE_WORK)
                    .removeObserver(observer)
            }
        }
        workObserver = null
    }

    private fun applyProgress(progress: Data) {
        val pageId = progress.getString(UploadWorker.PROGRESS_PAGE_ID) ?: return
        val pct = progress.getInt(UploadWorker.PROGRESS_PCT, 0)
        val stateName = progress.getString(UploadWorker.PROGRESS_STATE) ?: "UPLOADING"
        val current = statuses[pageId] ?: return
        statuses[pageId] = current.copy(
            state = runCatching { UploadState.valueOf(stateName) }.getOrDefault(UploadState.UPLOADING),
            progress = pct
        )
    }

    private fun applyFinalResults(output: Data) {
        val json = output.getString(UploadWorker.OUTPUT_RESULTS) ?: return
        runCatching {
            gson.fromJson(json, Array<UploadWorker.JobResult>::class.java).forEach { r ->
                val current = statuses[r.pageId]
                if (current != null) {
                    statuses[r.pageId] = current.copy(
                        state = if (r.success) UploadState.DONE else UploadState.FAILED,
                        progress = if (r.success) 100 else current.progress,
                        error = r.error,
                        publishedId = r.publishedId
                    )
                }
            }
        }
    }

    private fun copyToCache(context: Context, uri: Uri): File? = runCatching {
        val input = context.contentResolver.openInputStream(uri) ?: return null
        val out = File(context.cacheDir, "upload_${System.currentTimeMillis()}_${(0..9999).random()}.mp4")
        input.use { ins -> out.outputStream().use { outs -> ins.copyTo(outs) } }
        out
    }.getOrNull()

    companion object {
        /** Soft warning threshold for video files. */
        const val WARN_SIZE_LIMIT = 500L * 1024 * 1024

        /** Hard limit: Meta Reels uploads above ~2 GB are rejected in practice. */
        const val HARD_SIZE_LIMIT = 2L * 1024 * 1024 * 1024

        fun sizeOf(context: Context, uri: Uri): Long = runCatching {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize }
                ?: -1L
        }.getOrDefault(-1L).let { if (it < 0) 0L else it }

        fun formatBytes(bytes: Long): String = when {
            bytes >= 1_000_000_000 -> String.format("%.1f GB", bytes / 1_000_000_000.0)
            bytes >= 1_000_000 -> String.format("%.0f MB", bytes / 1_000_000.0)
            bytes >= 1_000 -> String.format("%.0f KB", bytes / 1_000.0)
            else -> "$bytes B"
        }
    }
}
