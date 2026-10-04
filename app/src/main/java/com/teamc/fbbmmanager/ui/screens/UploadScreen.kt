package com.teamc.fbbmmanager.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.teamc.fbbmmanager.R
import com.teamc.fbbmmanager.data.PageCache
import com.teamc.fbbmmanager.data.model.FbPage
import com.teamc.fbbmmanager.data.model.UploadState
import com.teamc.fbbmmanager.ui.PagesUiState
import com.teamc.fbbmmanager.ui.PagesViewModel
import com.teamc.fbbmmanager.ui.UploadMode
import com.teamc.fbbmmanager.ui.UploadViewModel

/**
 * Upload flow:
 * - Mode toggle: BULK (one video → all selected pages) or PER_PAGE (map a
 *   different video to each page).
 * - Page selector with select-all.
 * - Caption/description field.
 * - Per-page progress list while WorkManager uploads in background, with
 *   individual retry for failed pages.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadScreen(
    uploadVm: UploadViewModel,
    pagesVm: PagesViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val bmId = pagesVm.currentBmId
    val pages = (pagesVm.uiState as? PagesUiState.Loaded)?.pages
        ?: bmId?.let { PageCache.pagesFor(it) }.orEmpty()

    val bulkPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uploadVm.setBulkVideo(context, uri) }

    var perPageTarget by remember { mutableStateOf<String?>(null) }
    val perPagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        val target = perPageTarget
        if (uri != null && target != null) uploadVm.setVideoForPage(context, target, uri)
        perPageTarget = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.upload_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.upload_back))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(stringResource(R.string.upload_mode_label), fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = uploadVm.mode == UploadMode.BULK,
                        onClick = { uploadVm.mode = UploadMode.BULK },
                        label = { Text(stringResource(R.string.upload_mode_bulk)) }
                    )
                    FilterChip(
                        selected = uploadVm.mode == UploadMode.PER_PAGE,
                        onClick = { uploadVm.mode = UploadMode.PER_PAGE },
                        label = { Text(stringResource(R.string.upload_mode_per_page)) }
                    )
                }
            }

            if (uploadVm.mode == UploadMode.BULK) {
                item {
                    VideoPickRow(
                        label = stringResource(R.string.upload_bulk_video_label),
                        uri = uploadVm.bulkVideoUri,
                        warning = uploadVm.sizeWarning(uploadVm.bulkVideoSizeBytes),
                        onPick = { bulkPicker.launch("video/*") }
                    )
                }
            }

            item {
                OutlinedTextField(
                    value = uploadVm.description,
                    onValueChange = uploadVm::onDescriptionChange,
                    label = { Text(stringResource(R.string.upload_caption_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.upload_pages_label, uploadVm.selectedPageIds.value.size),
                        fontWeight = FontWeight.SemiBold
                    )
                    Row {
                        OutlinedButton(onClick = { uploadVm.selectAll(pages) }) {
                            Text(stringResource(R.string.upload_select_all))
                        }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = { uploadVm.clearSelection() }) {
                            Text(stringResource(R.string.upload_select_none))
                        }
                    }
                }
                Text(
                    stringResource(R.string.upload_limit_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.upload_spec_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            items(pages, key = { it.id }) { page ->
                PageUploadRow(
                    page = page,
                    checked = uploadVm.isSelected(page.id),
                    onToggle = { uploadVm.togglePage(page.id) },
                    showPerPagePicker = uploadVm.mode == UploadMode.PER_PAGE,
                    videoUri = uploadVm.videoForPage(page.id),
                    warning = uploadVm.sizeWarning(uploadVm.videoSizeForPage(page.id)),
                    onPickVideo = {
                        perPageTarget = page.id
                        perPagePicker.launch("video/*")
                    },
                    onEnsureToken = { pagesVm.ensurePageToken(page) }
                )
            }

            item {
                val ready = uploadVm.readyTargets(pages).size
                Button(
                    onClick = { uploadVm.startUpload(context, pages) },
                    enabled = !uploadVm.busy && ready > 0,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (uploadVm.busy) stringResource(R.string.upload_preparing)
                        else stringResource(R.string.upload_publish_button, ready)
                    )
                }
                uploadVm.error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
                Text(
                    stringResource(R.string.upload_background_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (uploadVm.statuses.isNotEmpty()) {
                item {
                    Text(stringResource(R.string.upload_progress_title), fontWeight = FontWeight.SemiBold)
                }
                items(uploadVm.statuses.values.toList(), key = { it.pageId }) { status ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    status.pageName,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                                when (status.state) {
                                    UploadState.DONE -> Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    UploadState.FAILED -> {
                                        Icon(
                                            Icons.Default.Error,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        OutlinedButton(
                                            onClick = { uploadVm.retryPage(context, pages, status.pageId) }
                                        ) {
                                            Icon(
                                                Icons.Default.Refresh,
                                                contentDescription = null,
                                                modifier = Modifier.padding(end = 4.dp)
                                            )
                                            Text(stringResource(R.string.upload_retry_page))
                                        }
                                    }
                                    UploadState.UPLOADING -> CircularProgressIndicator(
                                        modifier = Modifier.width(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                    else -> {}
                                }
                            }
                            if (status.state == UploadState.UPLOADING) {
                                Spacer(Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { status.progress / 100f },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text("${status.progress}%", style = MaterialTheme.typography.bodySmall)
                            }
                            Text(
                                when (status.state) {
                                    UploadState.QUEUED -> stringResource(R.string.upload_state_queued)
                                    UploadState.UPLOADING -> stringResource(R.string.upload_state_uploading)
                                    UploadState.DONE -> stringResource(
                                        R.string.upload_state_done,
                                        status.publishedId ?: "?"
                                    )
                                    UploadState.FAILED -> stringResource(
                                        R.string.upload_state_failed,
                                        status.error ?: "unknown error"
                                    )
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (status.state == UploadState.FAILED)
                                    MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoPickRow(
    label: String,
    uri: Uri?,
    warning: String?,
    onPick: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onPick)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.VideoFile, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(label, fontWeight = FontWeight.SemiBold)
                    Text(
                        uri?.lastPathSegment ?: stringResource(R.string.upload_pick_video),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (warning != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        warning,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun PageUploadRow(
    page: FbPage,
    checked: Boolean,
    onToggle: () -> Unit,
    showPerPagePicker: Boolean,
    videoUri: Uri?,
    warning: String?,
    onPickVideo: () -> Unit,
    onEnsureToken: () -> Unit
) {
    // Fetch the page token lazily the first time the page is selected.
    androidx.compose.runtime.LaunchedEffect(checked) {
        if (checked && page.accessToken.isNullOrBlank()) onEnsureToken()
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Checkbox(checked = checked, onCheckedChange = { onToggle() })
                Text(
                    page.name,
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onToggle),
                    fontWeight = FontWeight.Medium
                )
                if (page.accessToken.isNullOrBlank()) {
                    Text(
                        stringResource(R.string.upload_no_token),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            if (showPerPagePicker && checked) {
                VideoPickRow(
                    label = stringResource(R.string.upload_per_page_label, page.name),
                    uri = videoUri,
                    warning = warning,
                    onPick = onPickVideo
                )
            }
        }
    }
}
