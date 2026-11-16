package com.example.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.data.AlertRepository
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.OAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private const val AUTH_TAG = "Auth"
private const val DEFAULT_WEB_CLIENT_ID_FALLBACK =
    "74392611696-pf519u9c48ee19oqf4t5oh5kiv7ddg4r.apps.googleusercontent.com"

private fun resolveWebClientId(context: Context): String {
    val resId = context.resources.getIdentifier(
        "default_web_client_id",
        "string",
        context.packageName
    )
    if (resId != 0) {
        val value = context.getString(resId).trim()
        if (value.isNotEmpty()) return value
    }
    return DEFAULT_WEB_CLIENT_ID_FALLBACK
}

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        trySend(firebaseAuth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

fun resolvedUserEmail(user: FirebaseUser?): String {
    if (user == null) return ""
    val direct = user.email?.trim().orEmpty()
    if (direct.isNotBlank()) return direct
    return user.providerData
        .firstOrNull { !it.email.isNullOrBlank() }
        ?.email
        ?.trim()
        .orEmpty()
}

fun isVerifiedAdminUser(user: FirebaseUser?): Boolean {
    if (user == null) return false
    val email = resolvedUserEmail(user)
    if (!AlertRepository.isAdminEmail(email)) return false
    val verifiedGoogleProvider = user.providerData.any {
        it.providerId == GoogleAuthProvider.PROVIDER_ID && AlertRepository.isAdminEmail(it.email)
    }
    return user.isEmailVerified || verifiedGoogleProvider
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

fun attemptAutoSignIn(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onUnauthenticated: () -> Unit,
    scope: CoroutineScope
) {
    if (Firebase.auth.currentUser != null) {
        onAuthSuccess()
        return
    }
    val clientId = resolveWebClientId(context)

    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(true)
        .setServerClientId(clientId)
        .setAutoSelectEnabled(true)
        .build()

    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()

    scope.launch {
        try {
            val activityOrContext = context.findActivity() ?: context
            val result = withTimeoutOrNull(3000L) {
                credentialManager.getCredential(activityOrContext, request)
            }
            val credential = result?.credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                Firebase.auth.signInWithCredential(authCredential).await()
                onAuthSuccess()
            } else {
                onUnauthenticated()
            }
        } catch (_: Exception) {
            onUnauthenticated()
        }
    }
}

private suspend fun signInWithCredentialManagerOption(
    activity: Activity,
    credentialManager: CredentialManager,
    request: GetCredentialRequest
): Boolean {
    val result = credentialManager.getCredential(activity, request)
    val credential = result.credential
    if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
        Firebase.auth.signInWithCredential(authCredential).await()
        return true
    }
    return false
}

private suspend fun signInWithFirebaseGoogleWebFlow(activity: Activity) {
    val provider = OAuthProvider.newBuilder("google.com")
        .setScopes(listOf("email", "profile"))
        .addCustomParameter("prompt", "select_account")
        .build()

    val pendingTask = Firebase.auth.pendingAuthResult
    if (pendingTask != null) {
        pendingTask.await()
        return
    }

    Firebase.auth
        .startActivityForSignInWithProvider(activity, provider)
        .await()
}

fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope,
    onAuthCancelled: () -> Unit = {}
) {
    val clientId = resolveWebClientId(context)

    scope.launch {
        val activity = context.findActivity()
        if (activity == null) {
            onAuthError("Не вдалося відкрити вікно входу Google")
            return@launch
        }

        // 1. Primary: Credential Manager GetSignInWithGoogleOption (One Tap / Bottom sheet)
        try {
            val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
            val primaryRequest = GetCredentialRequest.Builder()
                .addCredentialOption(signInOption)
                .build()
            if (signInWithCredentialManagerOption(activity, credentialManager, primaryRequest)) {
                onAuthSuccess()
                return@launch
            }
        } catch (e: GetCredentialCancellationException) {
            val msg = e.message.orEmpty()
            Log.w(AUTH_TAG, "Primary Google Sign-In returned cancellation: $msg", e)
            val isExplicitUserDismiss = msg.contains("by the user", ignoreCase = true)
            if (isExplicitUserDismiss) {
                onAuthCancelled()
                return@launch
            }
        } catch (e: NoCredentialException) {
            Log.w(AUTH_TAG, "NoCredentialException in primary Sign-In, trying fallbacks: ${e.message}")
        } catch (e: GetCredentialException) {
            Log.w(AUTH_TAG, "GetCredentialException in primary Sign-In, trying fallbacks: ${e.message}", e)
        } catch (e: Exception) {
            Log.w(AUTH_TAG, "Unexpected error in primary Sign-In, trying fallbacks: ${e.message}", e)
        }

        // 2. Secondary: Credential Manager GetGoogleIdOption (any Google account on device)
        try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(clientId)
                .setAutoSelectEnabled(false)
                .build()
            val secondaryRequest = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()
            if (signInWithCredentialManagerOption(activity, credentialManager, secondaryRequest)) {
                onAuthSuccess()
                return@launch
            }
        } catch (e: GetCredentialCancellationException) {
            val msg = e.message.orEmpty()
            Log.w(AUTH_TAG, "Secondary Google Sign-In returned cancellation: $msg", e)
        } catch (e: Exception) {
            Log.w(AUTH_TAG, "Secondary Google Sign-In failed, falling back to Firebase OAuth: ${e.message}", e)
        }

        // 3. Universal Fallback: Firebase Web OAuth via Chrome Custom Tab (works even without Play Services Credential Manager or matching SHA-1)
        try {
            signInWithFirebaseGoogleWebFlow(activity)
            if (Firebase.auth.currentUser != null) {
                onAuthSuccess()
            } else {
                onAuthError("Вхід через Google не завершено. Спробуйте ще раз.")
            }
        } catch (e: Exception) {
            Log.e(AUTH_TAG, "Google Web OAuth fallback failed", e)
            val errMsg = e.localizedMessage ?: "Помилка входу через Google"
            if (errMsg.contains("canceled", ignoreCase = true) || errMsg.contains("cancelled", ignoreCase = true)) {
                onAuthCancelled()
            } else {
                onAuthError("Не вдалося увійти через Google: $errMsg")
            }
        }
    }
}

fun signOutGoogle(
    credentialManager: CredentialManager,
    scope: CoroutineScope,
    onSignOutComplete: () -> Unit = {}
) {
    scope.launch {
        withContext(NonCancellable) {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w(AUTH_TAG, "Failed to clear credential state: ${e.message}")
            } finally {
                Firebase.auth.signOut()
                onSignOutComplete()
            }
        }
    }
}
