package com.teamc.fbbmmanager.data.local

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

/**
 * Local store for:
 * - the list of Business Manager IDs the user manages (multi-BM),
 * - which BM is currently selected,
 * - the dark-theme preference.
 *
 * Facebook access tokens themselves are managed by the Facebook SDK.
 */
class SessionStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** All saved Business Manager IDs, in the order they were added. */
    var businessManagerIds: List<String>
        get() = runCatching {
            val raw = prefs.getString(KEY_BM_IDS, null) ?: return emptyList()
            val arr = JSONArray(raw)
            List(arr.length()) { i -> arr.getString(i) }
        }.getOrDefault(emptyList())
        set(value) {
            val arr = JSONArray()
            value.forEach { arr.put(it) }
            prefs.edit().putString(KEY_BM_IDS, arr.toString()).apply()
        }

    /** Currently selected BM ID (null when none saved/selected). */
    var selectedBusinessManagerId: String?
        get() = prefs.getString(KEY_SELECTED_BM_ID, null)
        set(value) {
            prefs.edit().putString(KEY_SELECTED_BM_ID, value).apply()
        }

    var darkTheme: Boolean
        get() = prefs.getBoolean(KEY_DARK_THEME, true)
        set(value) {
            prefs.edit().putBoolean(KEY_DARK_THEME, value).apply()
        }

    fun clear() {
        prefs.edit().clear().apply()
    }

    /**
     * On the very first launch, pre-fill the user's own Business Manager
     * (Paulo's Studio) so they don't have to type it. Runs exactly once —
     * the user can still add, remove, reorder, or switch BMs afterwards, and
     * a deliberately removed BM is never re-added.
     */
    fun ensureDefaultBmSeeded() {
        if (prefs.getBoolean(KEY_DEFAULT_BM_SEEDED, false)) return
        val ids = businessManagerIds.toMutableList()
        if (DEFAULT_BM_ID !in ids) ids.add(0, DEFAULT_BM_ID)
        businessManagerIds = ids
        if (selectedBusinessManagerId == null && ids.isNotEmpty()) {
            selectedBusinessManagerId = ids.first()
        }
        prefs.edit().putBoolean(KEY_DEFAULT_BM_SEEDED, true).apply()
    }

    companion object {
        private const val PREFS = "fb_bm_manager_prefs"
        private const val KEY_BM_IDS = "business_manager_ids"
        private const val KEY_SELECTED_BM_ID = "selected_business_manager_id"
        private const val KEY_DARK_THEME = "dark_theme"
        private const val KEY_DEFAULT_BM_SEEDED = "default_bm_seeded"

        /** The user's Business Manager, pre-filled on first launch. */
        const val DEFAULT_BM_ID = "2114106279493502"
        const val DEFAULT_BM_NAME = "Paulo's Studio"

        /** Friendly label for a BM: the known name, otherwise the raw ID. */
        fun displayName(bmId: String): String =
            if (bmId == DEFAULT_BM_ID) DEFAULT_BM_NAME else bmId
    }
}
