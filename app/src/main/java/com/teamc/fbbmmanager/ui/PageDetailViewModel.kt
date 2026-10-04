package com.teamc.fbbmmanager.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.teamc.fbbmmanager.FbBmApp
import com.teamc.fbbmmanager.data.PageCache
import com.teamc.fbbmmanager.data.model.EarningsInfo
import com.teamc.fbbmmanager.data.model.FbPage
import com.teamc.fbbmmanager.data.model.MonetizationStatus
import com.teamc.fbbmmanager.data.model.PageDetail
import com.teamc.fbbmmanager.data.model.PageInsights
import com.teamc.fbbmmanager.data.repository.AuthExpiredException
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

/** Detail screen state: page info, monetization, insights, earnings. */
class PageDetailViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = (app as FbBmApp).repository
    private val auth = (app as FbBmApp).authManager
    private val sessionVm get() = SessionHolder.sessionVm

    var page by mutableStateOf<FbPage?>(null)
        private set
    var detail by mutableStateOf<PageDetail?>(null)
        private set
    var monetization by mutableStateOf<MonetizationStatus?>(null)
        private set
    var insights by mutableStateOf<PageInsights?>(null)
        private set
    var earnings by mutableStateOf<EarningsInfo?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var authExpired by mutableStateOf(false)
        private set

    private var lastPageId: String? = null

    fun load(pageId: String) {
        lastPageId = pageId
        val bmId = (getApplication<FbBmApp>()).sessionStore.selectedBusinessManagerId
        val fbPage = bmId?.let { PageCache.get(it, pageId) } ?: return
        page = fbPage
        val token = auth.currentToken()?.token
        if (token.isNullOrBlank()) {
            authExpired = true
            error = "Not logged in"
            return
        }
        loading = true
        error = null
        authExpired = false
        viewModelScope.launch {
            // Ensure we have a page token first (needed for page-scoped calls).
            val withToken = if (fbPage.accessToken.isNullOrBlank()) {
                repository.getPageAccessToken(fbPage.id, token).getOrNull()
                    ?.let { fbPage.copy(accessToken = it) } ?: fbPage
            } else fbPage
            page = withToken
            if (bmId != null) PageCache.updatePage(bmId, withToken)

            val detailJob = async { repository.getPageDetail(withToken, token) }
            val monetJob = async { repository.getMonetizationStatus(withToken) }
            val insightsJob = async { repository.getInsights(withToken) }
            val earningsJob = async { repository.getEarnings(withToken) }

            detailJob.await()
                .onSuccess { detail = it }
                .onFailure { e ->
                    if (e is AuthExpiredException) onAuthExpired()
                    else error = e.message
                }
            monetJob.await().onSuccess { monetization = it }
            insightsJob.await().onSuccess { insights = it }
            earningsJob.await().onSuccess { earnings = it }
            loading = false
        }
    }

    fun retry() {
        lastPageId?.let { load(it) }
    }

    private fun onAuthExpired() {
        authExpired = true
        sessionVm?.onAuthExpired()
    }
}
