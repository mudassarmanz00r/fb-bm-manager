package com.teamc.fbbmmanager.data.api.dto

import com.google.gson.annotations.SerializedName

/** Page object as returned by owned_pages / client_pages edges. */
data class PageDto(
    @SerializedName("id") val id: String = "",
    @SerializedName("name") val name: String = "",
    @SerializedName("followers_count") val followersCount: Long? = null,
    @SerializedName("picture") val picture: PictureDto? = null
)

data class PictureDto(
    @SerializedName("data") val data: PictureDataDto? = null
)

data class PictureDataDto(
    @SerializedName("url") val url: String? = null
)

/** GET /{page-id}?fields=access_token */
data class PageTokenDto(
    @SerializedName("id") val id: String = "",
    @SerializedName("access_token") val accessToken: String? = null
)

/** Page detail fields. */
data class PageDetailDto(
    @SerializedName("id") val id: String = "",
    @SerializedName("name") val name: String = "",
    @SerializedName("about") val about: String? = null,
    @SerializedName("link") val link: String? = null,
    @SerializedName("followers_count") val followersCount: Long? = null,
    @SerializedName("picture") val picture: PictureDto? = null,
    @SerializedName("is_eligible_for_branded_content") val brandedContentEligible: Boolean? = null
)

data class VideoDto(
    @SerializedName("id") val id: String = ""
)

data class VideosSummaryDto(
    @SerializedName("data") val data: List<VideoDto> = emptyList(),
    @SerializedName("summary") val summary: SummaryDto? = null
)

data class SummaryDto(
    @SerializedName("total_count") val totalCount: Long? = null
)

/** /{page-id}/insights response. */
data class InsightsResponseDto(
    @SerializedName("data") val data: List<InsightDatumDto> = emptyList()
)

data class InsightDatumDto(
    @SerializedName("name") val name: String = "",
    @SerializedName("period") val period: String = "",
    @SerializedName("values") val values: List<InsightValueDto> = emptyList()
)

data class InsightValueDto(
    @SerializedName("value") val value: Any? = null,
    @SerializedName("end_time") val endTime: String? = null
)

/**
 * Page Integrity API: GET /{page-id}/page_status
 * Docs: https://developers.facebook.com/documentation/pages-api/integrity-webhook
 */
data class PageStatusDto(
    @SerializedName("status") val status: String? = null, // ok | warning | restricted | suspended
    @SerializedName("violations") val violations: List<ViolationDto> = emptyList(),
    @SerializedName("restrictions") val restrictions: List<RestrictionDto> = emptyList(),
    @SerializedName("recommended_actions") val recommendedActions: List<RecommendedActionDto> = emptyList()
)

data class ViolationDto(
    @SerializedName("type") val type: String? = null,
    @SerializedName("description") val description: String? = null
)

data class RestrictionDto(
    @SerializedName("feature") val feature: String? = null,
    @SerializedName("status") val status: String? = null, // RESTRICTED | UNRESTRICTED
    @SerializedName("applied_time") val appliedTime: String? = null
)

data class RecommendedActionDto(
    @SerializedName("action") val action: String? = null,
    @SerializedName("description") val description: String? = null
)

/**
 * Reels publishing: POST /{page-id}/video_reels, upload_phase=start
 * Docs: https://developers.facebook.com/documentation/video-api/guides/reels-publishing
 */
data class ReelsStartDto(
    @SerializedName("video_id") val videoId: String? = null,
    @SerializedName("upload_url") val uploadUrl: String? = null
)

/** Resumable transfer response from rupload.facebook.com */
data class ReelsTransferDto(
    @SerializedName("success") val success: Boolean = false
)

/** Reels publishing: upload_phase=finish */
data class ReelsFinishDto(
    @SerializedName("success") val success: Boolean = false
)
