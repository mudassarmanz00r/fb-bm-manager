package com.teamc.fbbmmanager.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import com.teamc.fbbmmanager.FbBmApp
import com.teamc.fbbmmanager.ui.nav.AppNav
import com.teamc.fbbmmanager.ui.theme.FbBmTheme

/**
 * Single-activity app. FragmentActivity base is required by the Facebook
 * Login SDK. Facebook onActivityResult events are forwarded to the SDK's
 * CallbackManager here.
 */
class MainActivity : FragmentActivity() {

    private val sessionVm: SessionViewModel by viewModels()
    private val pagesVm: PagesViewModel by viewModels()
    private val detailVm: PageDetailViewModel by viewModels()
    private val uploadVm: UploadViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Wire the shared session VM for auth-expiry signalling.
        SessionHolder.sessionVm = sessionVm
        setContent {
            // Theme follows the saved preference (dark by default on first run).
            FbBmTheme(darkTheme = sessionVm.darkTheme) {
                AppNav(
                    activity = this,
                    sessionVm = sessionVm,
                    pagesVm = pagesVm,
                    detailVm = detailVm,
                    uploadVm = uploadVm
                )
            }
        }
    }

    @Deprecated("Forwarded to Facebook SDK")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        (application as FbBmApp).authManager.callbackManager
            .onActivityResult(requestCode, resultCode, data)
        super.onActivityResult(requestCode, resultCode, data)
    }
}
