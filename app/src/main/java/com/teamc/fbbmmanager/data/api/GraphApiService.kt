package com.teamc.fbbmmanager.data.api

import com.teamc.fbbmmanager.data.api.dto.InsightsResponseDto
import com.teamc.fbbmmanager.data.api.dto.PageDetailDto
import com.teamc.fbbmmanager.data.api.dto.PageDto
import com.teamc.fbbmmanager.data.api.dto.PageStatusDto
import com.teamc.fbbmmanager.data.api.dto.PageTokenDto
import com.teamc.fbbmmanager.data.api.dto.PagedResponse
import com.teamc.fbbmmanager.data.api.dto.ReelsFinishDto
import com.teamc.fbbmmanager.data.api.dto.ReelsStartDto
import com.teamc.fbbmmanager.data.api.dto.VideosSummaryDto
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Meta Graph API surface used by the app.
 *
 * Every endpoint below was verified against official Meta docs (see README
 * for the docs URLs):
 * - BM pages: https://developers.facebook.com/docs/business-management-apis/business-asset-management/guides/pages/
 * - Page reference (access_token field): https://developers.facebook.com/docs/graph-api/reference/page/
 * - Reels publishing: https://developers.facebook.com/documentation/video-api/guides/reels-publishing
 * - Page Integrity API: https://developers.facebook.com/documentation/pages-api/integrity-webhook
 * - Pages API changelog (insights metrics): https://developers.facebook.com/documentation/pages-api/changelog
 *
 * Auth is an explicit `access_token` query/form parameter per call.
 */
interface GraphApiService {

    // ------------------------------------------------------------------
    // Business Manager pages (permission: business_management)
    // ------------------------------------------------------------------

    @GET("{businessId}/owned_pages")
    suspend fun ownedPages(
        @Path("businessId") businessId: String,
        @Query("fields") fields: String = "id,name,followers_count,picture",
        @Query("limit") limit: Int = 100,
        @Query("access_token") accessToken: String
    ): PagedResponse<PageDto>

    @GET("{businessId}/client_pages")
    suspend fun clientPages(
        @Path("businessId") businessId: String,
        @Query("fields") fields: String = "id,name,followers_count,picture",
        @Query("limit") limit: Int = 100,
        @Query("access_token") accessToken: String
    ): PagedResponse<PageDto>

    // ------------------------------------------------------------------
    // Page access token (permission: pages_show_list)
    // ------------------------------------------------------------------

    @GET("{pageId}")
    suspend fun pageAccessToken(
        @Path("pageId") pageId: String,
        @Query("fields") fields: String = "access_token",
        @Query("access_token") accessToken: String
    ): PageTokenDto

    // ------------------------------------------------------------------
    // Page detail
    // ------------------------------------------------------------------

    @GET("{pageId}")
    suspend fun pageDetail(
        @Path("pageId") pageId: String,
        @Query("fields") fields: String =
            "id,name,about,link,followers_count,picture,is_eligible_for_branded_content",
        @Query("access_token") accessToken: String
    ): PageDetailDto

    @GET("{pageId}/videos")
    suspend fun videosSummary(
        @Path("pageId") pageId: String,
        @Query("summary") summary: Boolean = true,
        @Query("limit") limit: Int = 0,
        @Query("access_token") accessToken: String
    ): VideosSummaryDto

    // ------------------------------------------------------------------
    // Insights (permission: read_insights; metric names per Pages API changelog)
    // ------------------------------------------------------------------

    @GET("{pageId}/insights")
    suspend fun insights(
        @Path("pageId") pageId: String,
        @Query("metric") metric: String,
        @Query("period") period: String = "days_28",
        @Query("access_token") accessToken: String
    ): InsightsResponseDto

    // ------------------------------------------------------------------
    // Page Integrity API: violations / restrictions (permission: pages_manage_metadata)
    // ------------------------------------------------------------------

    @GET("{pageId}/page_status")
    suspend fun pageStatus(
        @Path("pageId") pageId: String,
        @Query("access_token") accessToken: String
    ): PageStatusDto

    // ------------------------------------------------------------------
    // Reels publishing (permission: pages_manage_posts)
    // Docs: https://developers.facebook.com/documentation/video-api/guides/reels-publishing
    // ------------------------------------------------------------------

    /** Step 1 — start upload session. Returns video_id + upload_url. */
    @FormUrlEncoded
    @POST("{pageId}/video_reels")
    suspend fun reelsStart(
        @Path("pageId") pageId: String,
        @Field("upload_phase") uploadPhase: String = "start",
        @Field("access_token") accessToken: String
    ): ReelsStartDto

    /** Step 3 — finish & publish. Caption goes in `description`. */
    @FormUrlEncoded
    @POST("{pageId}/video_reels")
    suspend fun reelsFinish(
        @Path("pageId") pageId: String,
        @Field("upload_phase") uploadPhase: String = "finish",
        @Field("video_id") videoId: String,
        @Field("video_state") videoState: String = "PUBLISHED",
        @Field("description") description: String,
        @Field("access_token") accessToken: String
    ): ReelsFinishDto
}
