package com.teamc.fbbmmanager.data.api.dto

import com.google.gson.annotations.SerializedName

/** Generic Graph API paged envelope. */
data class PagedResponse<T>(
    @SerializedName("data") val data: List<T> = emptyList(),
    @SerializedName("paging") val paging: Paging? = null
)

data class Paging(
    @SerializedName("cursors") val cursors: Cursors? = null,
    @SerializedName("next") val next: String? = null
)

data class Cursors(
    @SerializedName("before") val before: String? = null,
    @SerializedName("after") val after: String? = null
)

/** Graph API error envelope. */
data class GraphErrorResponse(
    @SerializedName("error") val error: GraphError? = null
)

data class GraphError(
    @SerializedName("message") val message: String? = null,
    @SerializedName("type") val type: String? = null,
    @SerializedName("code") val code: Int? = null,
    @SerializedName("error_subcode") val subcode: Int? = null
)
