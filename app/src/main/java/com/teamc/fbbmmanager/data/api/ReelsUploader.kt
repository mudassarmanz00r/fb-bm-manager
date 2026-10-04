package com.teamc.fbbmmanager.data.api

import com.google.gson.Gson
import com.teamc.fbbmmanager.data.api.dto.GraphErrorResponse
import com.teamc.fbbmmanager.data.api.dto.ReelsTransferDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException

/**
 * Implements the Reels resumable upload protocol exactly as documented:
 * https://developers.facebook.com/documentation/video-api/guides/reels-publishing
 *
 * 1. POST /{page-id}/video_reels {upload_phase=start} → video_id + upload_url
 * 2. POST {upload_url} (rupload.facebook.com) with headers
 *    `Authorization: OAuth <page_token>`, `offset`, `file_size`, raw bytes
 * 3. POST /{page-id}/video_reels {upload_phase=finish, video_id,
 *    video_state=PUBLISHED, description}
 *
 * Note the API rate limit: 30 API-published reels per rolling 24 hours.
 */
class ReelsUploader(
    private val service: GraphApiService,
    private val http: OkHttpClient,
    private val gson: Gson = Gson()
) {

    /**
     * Uploads [videoFile] as a Reel on the page and publishes it.
     * @param onProgress 0..100
     * @return Result with the published video_id.
     */
    suspend fun uploadReel(
        pageId: String,
        pageAccessToken: String,
        videoFile: File,
        description: String,
        onProgress: (Int) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val fileSize = videoFile.length()
            require(fileSize > 0) { "Video file is empty: ${videoFile.name}" }

            // --- Step 1: start session -----------------------------------
            onProgress(2)
            val start = service.reelsStart(
                pageId = pageId,
                accessToken = pageAccessToken
            )
            val videoId = start.videoId
                ?: throw IOException("Reels start failed: no video_id returned")
            val uploadUrl = start.uploadUrl
                ?: throw IOException("Reels start failed: no upload_url returned")

            // --- Step 2: transfer bytes (chunked, resumable) -------------
            transferBytes(uploadUrl, pageAccessToken, videoFile, fileSize) { fraction ->
                onProgress((5 + fraction * 85).toInt().coerceIn(0, 95))
            }

            // --- Step 3: finish & publish --------------------------------
            onProgress(97)
            val finish = service.reelsFinish(
                pageId = pageId,
                videoId = videoId,
                description = description,
                accessToken = pageAccessToken
            )
            if (!finish.success) {
                throw IOException("Reels finish call did not return success")
            }
            onProgress(100)
            videoId
        }
    }

    /**
     * Chunked byte transfer to the rupload host. Each chunk POST carries
     * `offset` and `file_size` headers plus `Authorization: OAuth <token>`.
     */
    private fun transferBytes(
        uploadUrl: String,
        pageAccessToken: String,
        videoFile: File,
        fileSize: Long,
        onFraction: (Float) -> Unit
    ) {
        var offset = 0L
        videoFile.inputStream().use { input ->
            val buffer = ByteArray(CHUNK_SIZE)
            while (offset < fileSize) {
                val read = input.read(buffer)
                if (read <= 0) break
                val chunk = if (read == buffer.size) buffer else buffer.copyOf(read)

                val request = Request.Builder()
                    .url(uploadUrl)
                    .post(chunk.toRequestBody("application/octet-stream".toMediaType()))
                    .header("Authorization", "OAuth $pageAccessToken")
                    .header("offset", offset.toString())
                    .header("file_size", fileSize.toString())
                    .build()

                http.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        throw IOException(
                            "Chunk upload failed at offset $offset " +
                                "(HTTP ${response.code}): ${graphErrorMessage(body)}"
                        )
                    }
                    val parsed = runCatching {
                        gson.fromJson(body, ReelsTransferDto::class.java)
                    }.getOrNull()
                    if (parsed?.success != true) {
                        throw IOException(
                            "Chunk upload not acknowledged at offset $offset: " +
                                graphErrorMessage(body).ifBlank { body.take(300) }
                        )
                    }
                }
                offset += read
                onFraction(offset.toFloat() / fileSize.toFloat())
            }
        }
        if (offset < fileSize) {
            throw IOException("Upload ended early: sent $offset of $fileSize bytes")
        }
    }

    private fun graphErrorMessage(body: String): String =
        runCatching {
            gson.fromJson(body, GraphErrorResponse::class.java)
                ?.error?.message.orEmpty()
        }.getOrDefault("")

    companion object {
        /** 4 MB chunks — a balance between progress granularity and request count. */
        private const val CHUNK_SIZE = 4 * 1024 * 1024
    }
}
