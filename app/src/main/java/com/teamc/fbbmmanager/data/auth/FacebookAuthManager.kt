package com.teamc.fbbmmanager.data.auth

import androidx.fragment.app.FragmentActivity
import com.facebook.AccessToken
import com.facebook.CallbackManager
import com.facebook.FacebookCallback
import com.facebook.FacebookException
import com.facebook.login.LoginManager
import com.facebook.login.LoginResult

/**
 * Wraps the Facebook Login SDK. Permissions are requested at login time;
 * Meta App Review is required before non-test users can grant the
 * business/page permissions (see README).
 */
class FacebookAuthManager {

    val callbackManager: CallbackManager = CallbackManager.Factory.create()

    /**
     * Starts Facebook Login. The host Activity must forward onActivityResult
     * to [callbackManager].
     */
    @Suppress("DEPRECATION")
    fun login(activity: FragmentActivity, onResult: (Result<AccessToken>) -> Unit) {
        LoginManager.getInstance().registerCallback(
            callbackManager,
            object : FacebookCallback<LoginResult> {
                override fun onCancel() {
                    onResult(Result.failure(Exception("Login cancelled by user")))
                }

                override fun onError(error: FacebookException) {
                    onResult(Result.failure(error))
                }

                override fun onSuccess(result: LoginResult) {
                    onResult(Result.success(result.accessToken))
                }
            }
        )
        LoginManager.getInstance()
            .logInWithReadPermissions(activity, LOGIN_PERMISSIONS)
    }

    fun logout() {
        LoginManager.getInstance().logOut()
    }

    fun currentToken(): AccessToken? =
        AccessToken.getCurrentAccessToken()?.takeIf { !it.isExpired }

    fun isLoggedIn(): Boolean = currentToken() != null

    companion object {
        /**
         * Permissions requested at login. All of these require Meta App Review
         * for real users; test users / app roles can grant them immediately.
         */
        val LOGIN_PERMISSIONS = listOf(
            "pages_show_list",      // list Pages the user manages
            "pages_read_engagement",// read page content, followers, insights basics
            "pages_manage_posts",   // publish reels/videos to pages
            "business_management",  // read Business Manager assets (owned/client pages)
            "read_insights",        // page insights metrics
            "pages_manage_metadata" // page_status (Page Integrity API) for violations/restrictions
        )
    }
}
