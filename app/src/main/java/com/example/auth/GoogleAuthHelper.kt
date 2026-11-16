package com.example.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.example.data.AlertRepository
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
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
import java.security.MessageDigest

private const val AUTH_TAG = "Auth"
private const val EXPECTED_DEBUG_SHA1 = "4B:8A:65:91:77:FC:73:4D:E6:7D:FC:EF:5B:35:47:0B:90:33:43:DB"

private fun resolveWebClientId(context: Context): String? {
    return try {
        context.getString(R.string.default_web_client_id).trim().takeIf { it.isNotEmpty() }
    } catch (e: Exception) {
        Log.e(AUTH_TAG, "Failed to resolve R.string.default_web_client_id", e)
        null
    }
}

@Suppress("DEPRECATION")
private fun getApkSha1Fingerprint(context: Context): String? {
    return try {
        val pkgName = context.packageName
        val pm = context.packageManager
        val certBytes: ByteArray? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = pm.getPackageInfo(pkgName, PackageManager.GET_SIGNING_CERTIFICATES)
            val signingInfo = info.signingInfo
            val certs = if (signingInfo?.hasMultipleSigners() == true) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo?.signingCertificateHistory
            }
            certs?.firstOrNull()?.toByteArray()
        } else {
            val info = pm.getPackageInfo(pkgName, PackageManager.GET_SIGNATURES)
            info.signatures?.firstOrNull()?.toByteArray()
        }
        certBytes?.let { bytes ->
            val digest = MessageDigest.getInstance("SHA-1").digest(bytes)
            digest.joinToString(":") { b -> "%02X".format(b) }
        }
    } catch (e: Exception) {
        Log.w(AUTH_TAG, "Could not inspect APK signing SHA-1: ${e.message}")
        null
    }
}

private fun formatSigningDiagnostic(context: Context, fallbackMessage: String): String {
    val runtimeSha1 = getApkSha1Fingerprint(context)
    return if (runtimeSha1 != null && !runtimeSha1.equals(EXPECTED_DEBUG_SHA1, ignoreCase = true)) {
        "$fallbackMessage\n\nSHA-1 цього APK: $runtimeSha1\nДодайте цей SHA-1 у Firebase Console (проєкт gen-lang-client-0900673208 → Налаштування → Android-додаток) або додайте секрет DEBUG_KEYSTORE_BASE64 у GitHub Actions."
    } else {
        fallbackMessage
    }
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
    if (clientId == null) {
        onUnauthenticated()
        return
    }

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

fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope,
    onAuthCancelled: () -> Unit = {}
) {
    val clientId = resolveWebClientId(context)
    if (clientId == null) {
        onAuthError("Конфігурація Google Sign-In відсутня (default_web_client_id не знайдено).")
        return
    }

    scope.launch {
        val activity = context.findActivity()
        if (activity == null) {
            onAuthError("Не вдалося відкрити вікно входу Google")
            return@launch
        }

        // Primary: Credential Manager GetSignInWithGoogleOption (single option per request)
        try {
            val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
            val primaryRequest = GetCredentialRequest.Builder()
                .addCredentialOption(signInOption)
                .build()
            if (signInWithCredentialManagerOption(activity, credentialManager, primaryRequest)) {
                onAuthSuccess()
            } else {
                onAuthError("Отримано неочікуваний тип облікових даних Google.")
            }
            return@launch
        } catch (e: GetCredentialCancellationException) {
            val msg = e.message.orEmpty()
            Log.w(AUTH_TAG, "Google Sign-In cancelled or dismissed: $msg", e)
            val isExplicitUserDismiss = msg.contains("by the user", ignoreCase = true)
            if (isExplicitUserDismiss) {
                onAuthCancelled()
            } else {
                onAuthError(
                    formatSigningDiagnostic(
                        activity,
                        "Вхід через Google перервано сервісом Google Play. Спробуйте ще раз."
                    )
                )
            }
            return@launch
        } catch (e: NoCredentialException) {
            Log.w(AUTH_TAG, "NoCredentialException in GetSignInWithGoogleOption, trying GetGoogleIdOption: ${e.message}")
        } catch (e: GetCredentialException) {
            Log.e(AUTH_TAG, "GetCredentialException in Google Sign-In: ${e.message}", e)
            val detail = e.localizedMessage ?: "Помилка сервісу авторизації Google"
            onAuthError(formatSigningDiagnostic(activity, "Не вдалося увійти через Google: $detail"))
            return@launch
        } catch (e: Exception) {
            Log.e(AUTH_TAG, "Unexpected error in Google Sign-In", e)
            val detail = e.localizedMessage ?: "Помилка входу через Google"
            onAuthError(formatSigningDiagnostic(activity, "Не вдалося увійти через Google: $detail"))
            return@launch
        }

        // Secondary fallback only when NoCredentialException occurs: GetGoogleIdOption in a separate request
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
            } else {
                onAuthError("На пристрої не знайдено обліковий запис Google для входу.")
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w(AUTH_TAG, "Secondary Google Sign-In cancelled or dismissed: ${e.message}", e)
            onAuthCancelled()
        } catch (e: Exception) {
            Log.e(AUTH_TAG, "Secondary Google Sign-In failed", e)
            val detail = e.localizedMessage ?: "Перевірте підключення та наявність Google-акаунта на пристрої"
            onAuthError(formatSigningDiagnostic(activity, "Не вдалося увійти через Google: $detail"))
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
