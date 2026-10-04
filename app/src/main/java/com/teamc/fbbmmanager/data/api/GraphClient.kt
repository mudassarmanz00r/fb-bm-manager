package com.teamc.fbbmmanager.data.api

import com.teamc.fbbmmanager.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Retrofit entry point for the Meta Graph API.
 * Base URL carries the API version from BuildConfig (single place to bump).
 * Auth is passed as an explicit `access_token` query parameter on every call
 * so each request is traceable to the documented endpoint.
 */
object GraphClient {

    private const val HOST = "https://graph.facebook.com/"

    private val okHttp: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS) // chunked uploads can be slow
            .writeTimeout(120, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .build()
    }

    val service: GraphApiService by lazy {
        Retrofit.Builder()
            .baseUrl("$HOST${BuildConfig.GRAPH_API_VERSION}/")
            .client(okHttp)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GraphApiService::class.java)
    }

    /** Raw OkHttp client, used for the chunked Reel upload transfer calls. */
    fun rawClient(): OkHttpClient = okHttp

    fun graphVideoHost(): String = "https://graph.facebook.com/${BuildConfig.GRAPH_API_VERSION}/"
}
