package com.teamc.fbbmmanager.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.teamc.fbbmmanager.FbBmApp
import com.teamc.fbbmmanager.data.PageCache
import com.teamc.fbbmmanager.data.model.FbPage
import com.teamc.fbbmmanager.data.repository.AuthExpiredException
import kotlinx.coroutines.launch

/** UI state for the pages list screen. */
sealed interface PagesUiState {
    data object Idle : PagesUiState
    data object Loading : PagesUiState
    data class Loaded(val pages: List<FbPage>, val bmId: String) : PagesUiState
    data class Error(val message: String, val isAuth: Boolean = false) : PagesUiState
}

/**
 * Loads pages for the currently selected BM and resolves per-page access
 * tokens. Activity-scoped. Switching BMs reloads from the network (or the
 * per-BM in-memory cache on instant switch-back).
 */
class PagesViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = (app as FbBmApp).repository
    private val sessionStore = (app as FbBmApp).sessionStore
    private val auth = app.authManager
    private val sessionVm get() = SessionHolder.sessionVm

    var uiState by mutableStateOf<PagesUiState>(PagesUiState.Idle)
        private set

    var tokenBusyIds by mutableStateOf<Set<String>>(emptySet())
        private set

    /** The BM these pages belong to (for the switcher UI). */
    var currentBmId by mutableStateOf<String?>(null)
        private set

    fun loadPages() {
        val bmId = sessionStore.selectedBusinessManagerId
        val token = auth.currentToken()?.token
        when {
            token.isNullOrBlank() -> {
                uiState = PagesUiState.Error("Not logged in.", isAuth = true)
                return
            }
            bmId.isNullOrBlank() -> {
                uiState = PagesUiState.Error("No Business Manager selected.")
                return
            }
        }
        // Instant switch-back: serve from the per-BM cache first.
        val cached = PageCache.pagesFor(bmId!!)
        if (cached.isNotEmpty()) {
            currentBmId = bmId
            uiState = PagesUiState.Loaded(cached, bmId)
            return
        }
        uiState = PagesUiState.Loading
        viewModelScope.launch {
            repository.getBusinessPages(bmId, token!!)
                .onSuccess { pages ->
                    currentBmId = bmId
                    PageCache.put(bmId, pages)
                    uiState = PagesUiState.Loaded(pages, bmId)
                }
                .onFailure {
                    uiState = if (it is AuthExpiredException) {
                        sessionVm?.onAuthExpired()
                        PagesUiState.Error(
                            "Facebook session expired — please log in again.",
                            isAuth = true
                        )
                    } else {
                        PagesUiState.Error(it.message ?: "Failed to load pages")
                    }
                }
        }
    }

    /** Called when the user picks another BM in the switcher. */
    fun switchBm(bmId: String) {
        if (bmId == currentBmId && uiState is PagesUiState.Loaded) return
        uiState = PagesUiState.Idle
        loadPages()
    }

    /**
     * Fetches the page access token for [page] and updates the cached copy.
     * Needed before upload/detail calls that act "as the page".
     */
    fun ensurePageToken(page: FbPage, onDone: (FbPage) -> Unit = {}) {
        if (!page.accessToken.isNullOrBlank()) {
            onDone(page)
            return
        }
        val token = auth.currentToken()?.token ?: return
        val bmId = currentBmId ?: return
        tokenBusyIds = tokenBusyIds + page.id
        viewModelScope.launch {
            repository.getPageAccessToken(page.id, token)
                .onSuccess { pageToken ->
                    val updated = page.copy(accessToken = pageToken)
                    PageCache.updatePage(bmId, updated)
                    refreshLoaded(updated, bmId)
                    onDone(updated)
                }
                .onFailure {
                    if (it is AuthExpiredException) sessionVm?.onAuthExpired()
                    // otherwise: caller surfaces the error via detail screen retry
                }
            tokenBusyIds = tokenBusyIds - page.id
        }
    }

    private fun refreshLoaded(updated: FbPage, bmId: String) {
        val current = uiState
        if (current is PagesUiState.Loaded && current.bmId == bmId) {
            uiState = PagesUiState.Loaded(
                current.pages.map { if (it.id == updated.id) updated else it },
                bmId
            )
        }
    }

    fun refresh() = loadPages()
}

/**
 * Tiny holder so PagesViewModel can signal the shared SessionViewModel about
 * auth expiry without a direct reference cycle. Wired in MainActivity.
 */
object SessionHolder {
    var sessionVm: SessionViewModel? = null
}
