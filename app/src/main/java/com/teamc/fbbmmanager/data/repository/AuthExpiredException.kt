package com.teamc.fbbmmanager.data.repository

/**
 * Thrown when the Graph API reports an expired/invalid access token
 * (OAuth error code 190). The UI layer catches this to force a re-login
 * instead of showing a generic network error.
 */
class AuthExpiredException(message: String = "Facebook session expired — please log in again.") :
    Exception(message)
