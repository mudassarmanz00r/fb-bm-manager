package com.teamc.fbbmmanager.data.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.gson.Gson
import com.teamc.fbbmmanager.FbBmApp
import com.teamc.fbbmmanager.data.model.FbPage
import com.teamc.fbbmmanager.data.model.PageSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/** One reel upload job: a page + a local video file + caption. */
data class ReelUploadJob(
    val pageId: String,
    val pageName: String,
    val pageToken: String,
    val filePath: String,
    val description: String
)

/**
 * Uploads reels sequentially (one page at a time) so progress is clear and
 * API rate limits are respected. Each page is retried up to 3 times with a
 * short backoff; failures are recorded per page and the worker continues
 * with the next page.
 *
 * Runs as a foreground service worker so uploads survive the app being
 * backgrounded.
 */
class UploadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val gson = Gson()

    data class JobResult(
        val pageId: String,
        val success: Boolean,
        val publishedId: String? = null,
        val error: String? = null
    )

    override suspend fun doWork(): Result {
        val jobsJson = inputData.getString(KEY_JOBS) ?: return Result.failure()
        val jobs = runCatching {
            gson.fromJson(jobsJson, Array<ReelUploadJob>::class.java).toList()
        }.getOrElse { return Result.failure() }

        setForeground(createForegroundInfo("Starting uploads…"))

        val repository = (applicationContext as FbBmApp).repository
        val results = mutableListOf<JobResult>()

        // setProgress() is suspend, but the uploader's onProgress callback is
        // a plain (non-suspend) lambda -- bridge progress updates through a
        // child scope, cancelled in the finally block below.
        val progressScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            jobs.forEachIndexed { index, job ->
                setForeground(createForegroundInfo("Uploading to ${job.pageName} (${index + 1}/${jobs.size})"))
                setProgress(workDataOf(PROGRESS_PAGE_ID to job.pageId, PROGRESS_PCT to 0, PROGRESS_STATE to "UPLOADING"))

                var publishedId: String? = null
                var lastError: String? = null
                var attempt = 0
                while (attempt < MAX_ATTEMPTS && publishedId == null) {
                    attempt++
                    try {
                        val page = FbPage(
                            id = job.pageId,
                            name = job.pageName,
                            followersCount = null,
                            pictureUrl = null,
                            accessToken = job.pageToken,
                            source = PageSource.OWNED
                        )
                        val result = repository.publishReel(
                            page = page,
                            videoFile = File(job.filePath),
                            description = job.description,
                            onProgress = { pct ->
                                progressScope.launch {
                                    setProgress(
                                        workDataOf(
                                            PROGRESS_PAGE_ID to job.pageId,
                                            PROGRESS_PCT to pct,
                                            PROGRESS_STATE to "UPLOADING"
                                        )
                                    )
                                }
                            }
                        )
                        result
                            .onSuccess { publishedId = it }
                            .onFailure {
                                lastError = it.message ?: "Upload failed"
                                if (attempt < MAX_ATTEMPTS) delay(RETRY_DELAY_MS)
                            }
                    } catch (e: Exception) {
                        lastError = e.message ?: "Upload failed"
                        if (attempt < MAX_ATTEMPTS) delay(RETRY_DELAY_MS)
                    }
                }

                if (publishedId != null) {
                    setProgress(
                        workDataOf(PROGRESS_PAGE_ID to job.pageId, PROGRESS_PCT to 100, PROGRESS_STATE to "DONE")
                    )
                    results.add(JobResult(job.pageId, true, publishedId = publishedId))
                } else {
                    setProgress(
                        workDataOf(PROGRESS_PAGE_ID to job.pageId, PROGRESS_PCT to 0, PROGRESS_STATE to "FAILED")
                    )
                    results.add(JobResult(job.pageId, false, error = lastError ?: "Upload failed"))
                }
            }
        } finally {
            progressScope.cancel()
        }

        return Result.success(workDataOf(OUTPUT_RESULTS to gson.toJson(results)))
    }

    private fun createForegroundInfo(text: String): ForegroundInfo {
        val channelId = CHANNEL_ID
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, "Reel uploads", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("FB BM Page Manager")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val UNIQUE_WORK = "reel_bulk_upload"
        const val TAG = "reel_upload"
        const val KEY_JOBS = "jobs_json"
        const val PROGRESS_PAGE_ID = "pageId"
        const val PROGRESS_PCT = "progress"
        const val PROGRESS_STATE = "state"
        const val OUTPUT_RESULTS = "results_json"

        private const val CHANNEL_ID = "reel_upload_channel"
        private const val NOTIFICATION_ID = 1001
        private const val MAX_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 5_000L
    }
}
