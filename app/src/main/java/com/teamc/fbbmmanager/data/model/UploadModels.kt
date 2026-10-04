package com.teamc.fbbmmanager.data.model

import android.net.Uri

/** One upload target: a page + the video to publish as a Reel on it. */
data class PageUploadTarget(
    val page: FbPage,
    val videoUri: Uri,
    val description: String
)

enum class UploadState { QUEUED, UPLOADING, DONE, FAILED }

data class UploadItemStatus(
    val pageId: String,
    val pageName: String,
    val state: UploadState = UploadState.QUEUED,
    /** 0..100 */
    val progress: Int = 0,
    val error: String? = null,
    /** Published video/Reel ID on success. */
    val publishedId: String? = null
)
