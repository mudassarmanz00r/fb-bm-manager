package com.teamc.fbbmmanager.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.teamc.fbbmmanager.R
import com.teamc.fbbmmanager.data.local.SessionStore
import com.teamc.fbbmmanager.data.model.FbPage
import com.teamc.fbbmmanager.data.model.PageSource
import com.teamc.fbbmmanager.ui.PagesUiState
import com.teamc.fbbmmanager.ui.PagesViewModel
import com.teamc.fbbmmanager.ui.SessionViewModel
import com.teamc.fbbmmanager.ui.components.EmptyState
import com.teamc.fbbmmanager.ui.components.ErrorState
import com.teamc.fbbmmanager.ui.components.NoPagesState
import com.teamc.fbbmmanager.ui.components.PageSkeletonRow

/**
 * All pages of the selected BM: picture, name, followers, owned/client badge.
 * Top bar has a BM switcher (when several BMs are saved), an overflow menu
 * (theme toggle, manage BMs, log out) and quick actions (upload, reload).
 * Wide screens (tablets/landscape) get an adaptive grid.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PagesListScreen(
    pagesVm: PagesViewModel,
    sessionVm: SessionViewModel,
    onPageClick: (FbPage) -> Unit,
    onUploadClick: () -> Unit,
    onManageBms: () -> Unit,
    onLogout: () -> Unit,
    onRelogin: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    var switcherOpen by remember { mutableStateOf(false) }

    LaunchedEffect(sessionVm.selectedBmId) {
        // (Re)load whenever the selected BM changes.
        if (pagesVm.uiState is PagesUiState.Idle) pagesVm.loadPages()
        else pagesVm.switchBm(sessionVm.selectedBmId ?: return@LaunchedEffect)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (sessionVm.bmIds.size > 1) {
                        // BM switcher as the title.
                        Row(
                            modifier = Modifier.clickable { switcherOpen = true },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Business,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = sessionVm.selectedBmId?.let { SessionStore.displayName(it) }
                                    ?: stringResource(R.string.pages_title),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        DropdownMenu(
                            expanded = switcherOpen,
                            onDismissRequest = { switcherOpen = false }
                        ) {
                            Text(
                                stringResource(R.string.pages_switch_bm),
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                            sessionVm.bmIds.forEach { bmId ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            SessionStore.displayName(bmId),
                                            fontWeight = if (bmId == sessionVm.selectedBmId)
                                                FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        switcherOpen = false
                                        sessionVm.selectBm(bmId)
                                    }
                                )
                            }
                        }
                    } else {
                        Text(stringResource(R.string.pages_title))
                    }
                },
                actions = {
                    IconButton(onClick = onUploadClick) {
                        Icon(Icons.Default.CloudUpload, contentDescription = stringResource(R.string.pages_upload))
                    }
                    IconButton(onClick = { pagesVm.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.pages_reload))
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = null)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.pages_toggle_theme)) },
                            leadingIcon = {
                                Icon(
                                    if (sessionVm.darkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                    contentDescription = null
                                )
                            },
                            onClick = { menuOpen = false; sessionVm.toggleTheme() }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.pages_manage_bms)) },
                            leadingIcon = { Icon(Icons.Default.Business, contentDescription = null) },
                            onClick = { menuOpen = false; onManageBms() }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.pages_logout)) },
                            onClick = {
                                menuOpen = false
                                sessionVm.logout()
                                onLogout()
                            }
                        )
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (val state = pagesVm.uiState) {
                is PagesUiState.Idle, is PagesUiState.Loading -> {
                    // Shimmer skeleton while loading.
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(6) { PageSkeletonRow() }
                    }
                }
                is PagesUiState.Error -> {
                    if (state.isAuth) {
                        EmptyState(
                            icon = Icons.Default.Refresh,
                            title = stringResource(R.string.pages_relogin),
                            body = stringResource(R.string.pages_relogin_body),
                            actionLabel = stringResource(R.string.pages_relogin),
                            onAction = {
                                sessionVm.logout()
                                onRelogin()
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        ErrorState(
                            message = state.message,
                            onRetry = { pagesVm.refresh() },
                            retryLabel = stringResource(R.string.pages_retry),
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                is PagesUiState.Loaded -> {
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        if (state.pages.isEmpty()) {
                            NoPagesState(
                                onManageBms = onManageBms,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            PagesGrid(
                                pages = state.pages,
                                tokenBusyIds = pagesVm.tokenBusyIds,
                                onPageClick = { page ->
                                    pagesVm.ensurePageToken(page) { onPageClick(it) }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** List on phones, adaptive grid on tablets / landscape. */
@Composable
private fun PagesGrid(
    pages: List<FbPage>,
    tokenBusyIds: Set<String>,
    onPageClick: (FbPage) -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wide = maxWidth > 600.dp
        if (wide) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 300.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(pages, key = { it.id }) { page ->
                    PageRow(
                        page = page,
                        tokenLoading = page.id in tokenBusyIds,
                        onClick = { onPageClick(page) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(pages, key = { it.id }) { page ->
                    PageRow(
                        page = page,
                        tokenLoading = page.id in tokenBusyIds,
                        onClick = { onPageClick(page) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PageRow(
    page: FbPage,
    tokenLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = page.pictureUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    page.name,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    page.followersCount?.let {
                        Text(
                            "${formatCount(it)} ${stringResource(R.string.pages_followers)} · ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    SourceChip(page.source)
                }
                if (!page.accessToken.isNullOrBlank()) {
                    Text(
                        stringResource(R.string.pages_token_ready),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            if (tokenLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        }
    }
}

@Composable
private fun SourceChip(source: PageSource) {
    FilterChip(
        selected = false,
        onClick = {},
        label = {
            Text(
                stringResource(if (source == PageSource.OWNED) R.string.pages_owned else R.string.pages_client),
                style = MaterialTheme.typography.labelSmall
            )
        },
        modifier = Modifier.padding(top = 2.dp)
    )
}

private fun formatCount(n: Long): String = when {
    n >= 1_000_000 -> String.format("%.1fM", n / 1_000_000.0)
    n >= 1_000 -> String.format("%.1fK", n / 1_000.0)
    else -> n.toString()
}
