package com.teamc.fbbmmanager.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.AndroidViewModel
import com.teamc.fbbmmanager.FbBmApp
import com.teamc.fbbmmanager.data.PageCache

/**
 * Owns login state, the multi-BM list, the selected BM, the theme, and the
 * global "session expired" event. Activity-scoped (shared across screens).
 */
class SessionViewModel(app: Application) : AndroidViewModel(app) {

    private val auth = (app as FbBmApp).authManager
    private val session = (app as FbBmApp).sessionStore

    var isLoggedIn by mutableStateOf(auth.isLoggedIn())
        private set

    var loginBusy by mutableStateOf(false)
        private set

    var loginError by mutableStateOf<String?>(null)
        private set

    // ------------------------------------------------------------------
    // Multi-BM state
    // ------------------------------------------------------------------

    var bmIds by mutableStateOf(session.businessManagerIds)
        private set

    var selectedBmId by mutableStateOf<String?>(session.selectedBusinessManagerId)
        private set

    var bmInput by mutableStateOf("")
        private set

    var bmInputError by mutableStateOf<String?>(null)
        private set

    /** True when every saved BM has been removed / none saved yet. */
    val hasBm: Boolean get() = selectedBmId != null

    init {
        // First launch: pre-fill the user's own BM (Paulo's Studio) so they
        // don't have to type it; afterwards the saved list is authoritative.
        session.ensureDefaultBmSeeded()
        bmIds = session.businessManagerIds
        selectedBmId = session.selectedBusinessManagerId
    }

    var darkTheme by mutableStateOf(session.darkTheme)
        private set

    /**
     * Set when a Graph call reports an expired/invalid token. The nav graph
     * observes this, forces a logout, and routes to Login; the flag is then
     * cleared via [authExpiredConsumed].
     */
    var authExpiredEvent by mutableStateOf(false)
        private set

    fun onBmInputChange(value: String) {
        bmInput = value.filter { it.isDigit() }.take(MAX_BM_ID_LEN)
        bmInputError = null
    }

    /** Validates + adds the typed BM ID, then selects it. */
    fun addBm(): Boolean {
        val id = bmInput.trim()
        val error = validateBmId(id)
        if (error != null) {
            bmInputError = error
            return false
        }
        if (id in bmIds) {
            selectedBmId = id
            session.selectedBusinessManagerId = id
            bmInput = ""
            return true
        }
        val updated = bmIds + id
        bmIds = updated
        session.businessManagerIds = updated
        selectedBmId = id
        session.selectedBusinessManagerId = id
        bmInput = ""
        return true
    }

    fun selectBm(id: String) {
        if (id !in bmIds) return
        selectedBmId = id
        session.selectedBusinessManagerId = id
    }

    fun removeBm(id: String) {
        val updated = bmIds - id
        bmIds = updated
        session.businessManagerIds = updated
        PageCache.clearBm(id)
        if (selectedBmId == id) {
            val next = updated.firstOrNull()
            selectedBmId = next
            session.selectedBusinessManagerId = next
        }
    }

    fun toggleTheme() {
        darkTheme = !darkTheme
        session.darkTheme = darkTheme
    }

    fun login(activity: FragmentActivity) {
        loginBusy = true
        loginError = null
        auth.login(activity) { result ->
            loginBusy = false
            result
                .onSuccess {
                    isLoggedIn = true
                    authExpiredEvent = false
                }
                .onFailure {
                    loginError = it.message ?: "Login failed"
                }
        }
    }

    /** Called by child VMs when the API reports error code 190. */
    fun onAuthExpired() {
        authExpiredEvent = true
    }

    fun authExpiredConsumed() {
        authExpiredEvent = false
    }

    fun logout() {
        auth.logout()
        PageCache.clear()
        // BM list is kept (it is not secret) so the user doesn't re-enter it;
        // only the in-memory selection state is reset on the next login flow.
        bmInput = ""
        bmInputError = null
        authExpiredEvent = false
        isLoggedIn = false
    }

    fun userAccessToken(): String? = auth.currentToken()?.token

    companion object {
        private const val MAX_BM_ID_LEN = 20

        /** BM IDs are numeric; Meta business IDs are typically 15-16 digits. */
        fun validateBmId(id: String): String? = when {
            id.isBlank() -> "Enter a Business Manager ID."
            id.length < 5 -> "That ID looks too short — BM IDs are numeric and much longer."
            id.length > MAX_BM_ID_LEN -> "That ID looks too long."
            else -> null
        }
    }
}
