package com.teamc.fbbmmanager.data

import com.teamc.fbbmmanager.data.model.FbPage

/**
 * In-memory cache of loaded page lists, keyed by Business Manager ID, so the
 * detail screen can resolve a pageId without another network round-trip and
 * switching BMs doesn't refetch everything.
 */
object PageCache {

    private val byBm: MutableMap<String, List<FbPage>> = mutableMapOf()

    fun put(bmId: String, list: List<FbPage>) {
        byBm[bmId] = list
    }

    fun get(bmId: String, pageId: String): FbPage? = byBm[bmId]?.find { it.id == pageId }

    fun pagesFor(bmId: String): List<FbPage> = byBm[bmId] ?: emptyList()

    fun updatePage(bmId: String, page: FbPage) {
        byBm[bmId] = byBm[bmId]?.map { if (it.id == page.id) page else it } ?: listOf(page)
    }

    fun clearBm(bmId: String) {
        byBm.remove(bmId)
    }

    fun clear() {
        byBm.clear()
    }
}
