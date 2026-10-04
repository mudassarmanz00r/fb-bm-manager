package com.teamc.fbbmmanager.data.repository

import com.teamc.fbbmmanager.data.model.EarningsInfo
import com.teamc.fbbmmanager.data.model.FbPage
import com.teamc.fbbmmanager.data.model.MonetizationStatus
import com.teamc.fbbmmanager.data.model.PageDetail
import com.teamc.fbbmmanager.data.model.PageInsights
import java.io.File

/**
 * Contract for all Facebook data operations. Implemented by
 * [com.teamc.fbbmmanager.data.repository.GraphPageRepository] on top of the
 * Graph API. UI/ViewModels depend only on this interface.
 */
interface PageRepository {

    /** Owned + client pages of a Business Manager. Needs business_management. */
    suspend fun getBusinessPages(businessId: String, userToken: String): Result<List<FbPage>>

    /** Page access token for publishing/reading as the page. */
    suspend fun getPageAccessToken(pageId: String, userToken: String): Result<String>

    /** Basic page detail incl. total uploaded video count. */
    suspend fun getPageDetail(page: FbPage, userToken: String): Result<PageDetail>

    /** Monetization eligibility/violations. May return dataAvailable=false. */
    suspend fun getMonetizationStatus(page: FbPage): Result<MonetizationStatus>

    /** Insights for the last 28 days. Needs read_insights. */
    suspend fun getInsights(page: FbPage): Result<PageInsights>

    /** Earnings. Usually dataAvailable=false (not exposed by public API). */
    suspend fun getEarnings(page: FbPage): Result<EarningsInfo>

    /**
     * Publishes [videoFile] as a Reel on [page] with [description].
     * @param onProgress 0..100 callback.
     * @return Result with the published video ID.
     */
    suspend fun publishReel(
        page: FbPage,
        videoFile: File,
        description: String,
        onProgress: (Int) -> Unit
    ): Result<String>
}
