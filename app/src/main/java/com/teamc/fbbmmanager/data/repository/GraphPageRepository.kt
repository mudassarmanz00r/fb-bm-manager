package com.teamc.fbbmmanager.data.repository

import com.teamc.fbbmmanager.data.api.GraphApiService
import com.teamc.fbbmmanager.data.api.GraphClient
import com.teamc.fbbmmanager.data.api.ReelsUploader
import com.teamc.fbbmmanager.data.api.dto.PageDto
import com.teamc.fbbmmanager.data.model.EarningsInfo
import com.teamc.fbbmmanager.data.model.FbPage
import com.teamc.fbbmmanager.data.model.MonetizationStatus
import com.teamc.fbbmmanager.data.model.PageDetail
import com.teamc.fbbmmanager.data.model.PageInsights
import com.teamc.fbbmmanager.data.model.PageSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.File

/**
 * [PageRepository] backed by the Meta Graph API.
 *
 * Honest-API policy: where Meta exposes no public endpoint (page earnings,
 * Content Monetization eligibility), the repository returns
 * `dataAvailable=false` with an explanatory message instead of fabricated
 * data. See README "API availability" for details.
 */
class GraphPageRepository(
    private val service: GraphApiService = GraphClient.service,
    private val uploader: ReelsUploader = ReelsUploader(service, GraphClient.rawClient())
) : PageRepository {

    override suspend fun getBusinessPages(
        businessId: String,
        userToken: String
    ): Result<List<FbPage>> = withContext(Dispatchers.IO) {
        runCatching {
            val owned = service.ownedPages(businessId, accessToken = userToken).data
                .map { it.toFbPage(PageSource.OWNED) }
            val client = service.clientPages(businessId, accessToken = userToken).data
                .map { it.toFbPage(PageSource.CLIENT) }
            // De-duplicate by id, owned wins.
            (owned + client).distinctBy { it.id }
        }.mapFailure(::friendlyError)
    }

    override suspend fun getPageAccessToken(
        pageId: String,
        userToken: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            service.pageAccessToken(pageId, accessToken = userToken).accessToken
                ?: throw IllegalStateException(
                    "No page access token returned. The logged-in user needs a " +
                        "role on this Page (and pages_show_list permission)."
                )
        }.mapFailure(::friendlyError)
    }

    override suspend fun getPageDetail(
        page: FbPage,
        userToken: String
    ): Result<PageDetail> = withContext(Dispatchers.IO) {
        runCatching {
            val token = page.accessToken ?: userToken
            val dto = service.pageDetail(page.id, accessToken = token)
            val videoCount = runCatching {
                service.videosSummary(page.id, accessToken = token).summary?.totalCount
            }.getOrNull()
            PageDetail(
                page = page.copy(
                    followersCount = dto.followersCount ?: page.followersCount,
                    pictureUrl = dto.picture?.data?.url ?: page.pictureUrl
                ),
                about = dto.about,
                link = dto.link,
                videoCount = videoCount
            )
        }.mapFailure(::friendlyError)
    }

    /**
     * Monetization signal via the Page Integrity API
     * (GET /{page-id}/page_status) + the Page field
     * `is_eligible_for_branded_content`.
     *
     * There is NO public Graph API for Content Monetization / in-stream ads /
     * Ads on Reels eligibility — that must be checked in Meta Business Suite.
     * This method is explicit about that limitation.
     */
    override suspend fun getMonetizationStatus(
        page: FbPage
    ): Result<MonetizationStatus> = withContext(Dispatchers.IO) {
        val pageToken = page.accessToken
            ?: return@withContext Result.success(
                MonetizationStatus(
                    pageId = page.id,
                    summary = "Page access token missing — fetch it from the pages list first.",
                    dataAvailable = false
                )
            )
        runCatching {
            val status = service.pageStatus(page.id, accessToken = pageToken)
            val branded = runCatching {
                service.pageDetail(
                    page.id,
                    fields = "is_eligible_for_branded_content",
                    accessToken = pageToken
                ).brandedContentEligible
            }.getOrNull()

            val violations = status.violations.mapNotNull {
                listOfNotNull(it.type, it.description).joinToString(": ").ifBlank { null }
            }
            val restrictions = status.restrictions
                .filter { it.status == "RESTRICTED" }
                .mapNotNull { it.feature }

            val summary = buildString {
                append("Page integrity: ${status.status ?: "unknown"}")
                branded?.let { append(if (it) " · Branded content: eligible" else " · Branded content: not eligible") }
                if (restrictions.isNotEmpty()) append(" · Restricted: ${restrictions.joinToString(", ")}")
                append(". Note: Content Monetization / in-stream ads eligibility has no public API — check Meta Business Suite → Monetization.")
            }
            MonetizationStatus(
                pageId = page.id,
                violations = violations,
                summary = summary,
                dataAvailable = true
            )
        }.recoverCatching { e ->
            // Expired/invalid token must surface as AuthExpiredException so the
            // UI forces a re-login instead of showing a permissions hint.
            val body = (e as? HttpException)?.let {
                runCatching { it.response()?.errorBody()?.string() }.getOrNull().orEmpty()
            }.orEmpty()
            val code = Regex("\"code\"\\s*:\\s*(\\d+)").find(body)?.groupValues?.get(1)?.toIntOrNull()
            if (code == 190) throw AuthExpiredException()
            if (e is HttpException && (e.code() == 403 || e.code() == 400)) {
                // Most likely missing pages_manage_metadata permission / App Review.
                MonetizationStatus(
                    pageId = page.id,
                    summary = "Page integrity data needs the pages_manage_metadata permission " +
                        "(granted after Meta App Review). Content Monetization eligibility " +
                        "has no public API — check Meta Business Suite → Monetization. " +
                        "API said: ${e.message()}",
                    dataAvailable = false
                )
            } else {
                throw e
            }
        }.mapFailure(::friendlyError)
    }

    /**
     * Page insights for the last 28 days.
     * Metric names follow the Pages API changelog (page_impressions etc. are
     * deprecated and now return errors, so current names are used).
     */
    override suspend fun getInsights(
        page: FbPage
    ): Result<PageInsights> = withContext(Dispatchers.IO) {
        val pageToken = page.accessToken
            ?: return@withContext Result.failure(
                IllegalStateException("Page access token missing")
            )
        runCatching {
            val dto = service.insights(
                pageId = page.id,
                metric = "page_media_view,page_total_media_view_unique," +
                    "page_post_engagements,page_daily_follows,page_follows",
                accessToken = pageToken
            )
            fun valueOf(name: String): Long? =
                dto.data.find { it.name == name }
                    ?.values?.lastOrNull()?.value
                    ?.toString()?.toDoubleOrNull()?.toLong()

            PageInsights(
                impressions = valueOf("page_media_view"),
                reach = valueOf("page_total_media_view_unique"),
                engagedUsers = valueOf("page_post_engagements"),
                newFollows = valueOf("page_daily_follows") ?: valueOf("page_follows"),
                videoViews = valueOf("page_video_views"),
                dataAvailable = true
            )
        }.mapFailure(::friendlyError)
    }

    /**
     * Earnings: Meta exposes NO public Graph API for Page/creator earnings or
     * payouts. Always returns dataAvailable=false with guidance.
     */
    override suspend fun getEarnings(
        page: FbPage
    ): Result<EarningsInfo> = Result.success(
        EarningsInfo(
            dataAvailable = false,
            summary = "Meta does not expose page earnings or payouts through the " +
                "public Graph API. Check earnings in Meta Business Suite → " +
                "Monetization → Payouts, or the Meta for Creators payouts dashboard."
        )
    )

    /**
     * Publishes [videoFile] as a Reel via the documented resumable flow.
     * NOTE the API rate limit: 30 API-published reels per rolling 24 hours.
     */
    override suspend fun publishReel(
        page: FbPage,
        videoFile: File,
        description: String,
        onProgress: (Int) -> Unit
    ): Result<String> {
        val pageToken = page.accessToken
            ?: return Result.failure(IllegalStateException("Page access token missing"))
        return uploader.uploadReel(
            pageId = page.id,
            pageAccessToken = pageToken,
            videoFile = videoFile,
            description = description,
            onProgress = onProgress
        ).mapFailure(::friendlyError)
    }

    // ------------------------------------------------------------------

    private fun PageDto.toFbPage(source: PageSource) = FbPage(
        id = id,
        name = name,
        followersCount = followersCount,
        pictureUrl = picture?.data?.url,
        source = source
    )

    private fun <T> Result<T>.mapFailure(map: (Throwable) -> Throwable): Result<T> =
        recoverCatching { throw map(it) }

    private fun friendlyError(e: Throwable): Throwable {
        if (e is AuthExpiredException) return e
        if (e is HttpException) {
            val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull().orEmpty()
            val code = Regex("\"code\"\\s*:\\s*(\\d+)").find(body)?.groupValues?.get(1)?.toIntOrNull()
            // OAuthException 190 (subcodes 450-463, 467, 492, …) = expired/invalid token.
            if (code == 190) {
                return AuthExpiredException()
            }
            val msg = Regex("\"message\"\\s*:\\s*\"([^\"]+)\"")
                .find(body)?.groupValues?.get(1)
            return Exception("Graph API error (HTTP ${e.code()}): ${msg ?: e.message()}")
        }
        return e
    }
}
