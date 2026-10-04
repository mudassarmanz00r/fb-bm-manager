package com.teamc.fbbmmanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.teamc.fbbmmanager.R
import com.teamc.fbbmmanager.data.model.EarningsInfo
import com.teamc.fbbmmanager.data.model.MonetizationStatus
import com.teamc.fbbmmanager.data.model.PageInsights
import com.teamc.fbbmmanager.ui.PageDetailViewModel
import com.teamc.fbbmmanager.ui.components.ErrorState
import com.teamc.fbbmmanager.ui.components.shimmerBrush

/** Page detail with Status / Stats / Earnings tabs. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageDetailScreen(
    pageId: String,
    vm: PageDetailViewModel,
    onBack: () -> Unit
) {
    LaunchedEffect(pageId) { vm.load(pageId) }
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        stringResource(R.string.detail_tab_status),
        stringResource(R.string.detail_tab_stats),
        stringResource(R.string.detail_tab_earnings)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(vm.page?.name ?: stringResource(R.string.pages_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.detail_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            PageHeader(vm)
            TabRow(selectedTabIndex = tab) {
                tabs.forEachIndexed { i, title ->
                    Tab(
                        selected = tab == i,
                        onClick = { tab = i },
                        text = { Text(title) }
                    )
                }
            }
            when {
                vm.loading -> DetailSkeleton()
                vm.authExpired -> ErrorState(
                    message = stringResource(R.string.pages_relogin_body),
                    onRetry = { vm.retry() },
                    retryLabel = stringResource(R.string.pages_retry),
                    modifier = Modifier.fillMaxSize()
                )
                vm.error != null -> ErrorState(
                    message = vm.error!!,
                    onRetry = { vm.retry() },
                    retryLabel = stringResource(R.string.pages_retry),
                    modifier = Modifier.fillMaxSize()
                )
                else -> when (tab) {
                    0 -> StatusTab(vm.monetization, vm.detail?.videoCount)
                    1 -> StatsTab(vm.insights)
                    2 -> EarningsTab(vm.earnings)
                }
            }
        }
    }
}

@Composable
private fun PageHeader(vm: PageDetailViewModel) {
    val page = vm.page ?: return
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = page.pictureUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(page.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(
                    (vm.detail?.page?.followersCount ?: page.followersCount)?.let {
                        "$it ${stringResource(R.string.pages_followers)}"
                    } ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                vm.detail?.link?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

/** Shimmer placeholder while the detail loads. */
@Composable
private fun DetailSkeleton() {
    val brush = shimmerBrush()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        repeat(4) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(brush)
            )
        }
    }
}

@Composable
private fun StatusTab(monetization: MonetizationStatus?, videoCount: Long?) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        InfoCard(
            title = stringResource(R.string.detail_videos),
            body = videoCount?.toString() ?: stringResource(R.string.detail_unavailable)
        )
        if (monetization == null) {
            InfoCard(
                title = stringResource(R.string.detail_monetization),
                body = stringResource(R.string.detail_loading)
            )
        } else {
            InfoCard(
                title = stringResource(R.string.detail_monetization),
                body = monetization.summary,
                ok = monetization.dataAvailable && monetization.violations.isEmpty()
            )
            if (monetization.violations.isNotEmpty()) {
                InfoCard(
                    title = "${stringResource(R.string.detail_violations)} (${monetization.violations.size})",
                    body = monetization.violations.joinToString("\n• ", prefix = "• "),
                    ok = false
                )
            }
        }
        Text(
            stringResource(R.string.detail_monet_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StatsTab(insights: PageInsights?) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (insights == null) {
            InfoCard(
                title = stringResource(R.string.detail_insights),
                body = stringResource(R.string.detail_loading)
            )
            return
        }
        Text(
            insights.periodLabel,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        StatRow(stringResource(R.string.detail_stat_views), insights.impressions)
        StatRow(stringResource(R.string.detail_stat_reach), insights.reach)
        StatRow(stringResource(R.string.detail_stat_engagements), insights.engagedUsers)
        StatRow(stringResource(R.string.detail_stat_follows), insights.newFollows)
        StatRow(stringResource(R.string.detail_stat_video_views), insights.videoViews)
        if (!insights.dataAvailable) {
            Text(
                stringResource(R.string.detail_insights_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatRow(label: String, value: Long?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label)
            Text(
                value?.toString() ?: stringResource(R.string.detail_dash),
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun EarningsTab(earnings: EarningsInfo?) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (earnings == null) {
            InfoCard(
                title = stringResource(R.string.detail_earnings),
                body = stringResource(R.string.detail_loading)
            )
            return
        }
        InfoCard(
            title = stringResource(R.string.detail_earnings),
            body = earnings.summary,
            ok = earnings.dataAvailable
        )
    }
}

@Composable
private fun InfoCard(title: String, body: String, ok: Boolean? = null) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            when (ok) {
                true -> Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                false -> Icon(
                    Icons.Default.Error,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                null -> Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(modifier = Modifier.padding(start = 10.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(body, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

// Local imports for background brush.
