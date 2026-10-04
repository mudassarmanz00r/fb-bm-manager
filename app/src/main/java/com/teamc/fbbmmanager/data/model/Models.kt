package com.teamc.fbbmmanager.data.model

/** A Facebook Page connected to the Business Manager. */
data class FbPage(
    val id: String,
    val name: String,
    val followersCount: Long?,
    val pictureUrl: String?,
    /** Page access token, fetched on demand via /{page-id}?fields=access_token. */
    val accessToken: String? = null,
    val source: PageSource
)

enum class PageSource { OWNED, CLIENT }

/** Extra detail for a single page. */
data class PageDetail(
    val page: FbPage,
    val about: String?,
    val link: String?,
    /** Total uploaded videos (/{page-id}/videos summary.total_count). Null if unavailable. */
    val videoCount: Long?
)

/**
 * Monetization status for a page.
 * NOTE: Meta's public Graph API exposes only a subset of monetization data.
 * Fields stay nullable and [dataAvailable] is false when the API exposes
 * nothing — the UI shows an honest "not exposed by API" state instead of
 * fake data.
 */
data class MonetizationStatus(
    val pageId: String,
    val reelsPlayEligible: Boolean? = null,
    val inStreamAdsEligible: Boolean? = null,
    val violations: List<String> = emptyList(),
    val summary: String,
    val dataAvailable: Boolean
)

/** A small set of page insights for the last 28 days. */
data class PageInsights(
    val periodLabel: String = "Last 28 days",
    val impressions: Long? = null,
    val reach: Long? = null,
    val engagedUsers: Long? = null,
    val newFollows: Long? = null,
    val videoViews: Long? = null,
    val dataAvailable: Boolean = true
)

/**
 * Earnings for a page.
 * NOTE: Meta does not publicly expose per-page earnings via Graph API for
 * third-party apps; this model carries whatever the API returns (usually
 * nothing) and the UI marks it accordingly.
 */
data class EarningsInfo(
    val dataAvailable: Boolean,
    val summary: String
)
