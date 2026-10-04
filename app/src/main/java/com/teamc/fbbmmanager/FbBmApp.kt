package com.teamc.fbbmmanager

import android.app.Application
import android.util.Log
import com.facebook.FacebookSdk
import com.facebook.appevents.AppEventsLogger
import com.teamc.fbbmmanager.data.api.GraphClient
import com.teamc.fbbmmanager.data.auth.FacebookAuthManager
import com.teamc.fbbmmanager.data.local.SessionStore
import com.teamc.fbbmmanager.data.repository.GraphPageRepository
import com.teamc.fbbmmanager.data.repository.PageRepository

/**
 * Application class and simple service locator.
 *
 * The Facebook SDK auto-initializes from the manifest meta-data
 * (com.facebook.sdk.AutoInitEnabled=true). META_APP_ID comes from
 * local.properties (see local.properties.template); the project still
 * configures without it, but Facebook Login will fail until set.
 */
class FbBmApp : Application() {

    val authManager: FacebookAuthManager by lazy { FacebookAuthManager() }
    val sessionStore: SessionStore by lazy { SessionStore(this) }
    val repository: PageRepository by lazy { GraphPageRepository(GraphClient.service) }

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.META_APP_ID == "REPLACE_WITH_YOUR_META_APP_ID") {
            Log.w(
                "FbBmApp",
                "META_APP_ID is not set. Copy local.properties.template to " +
                    "local.properties and paste your Meta App ID (see README)."
            )
        } else {
            FacebookSdk.setApplicationId(BuildConfig.META_APP_ID)
            AppEventsLogger.activateApp(this)
        }
    }
}
