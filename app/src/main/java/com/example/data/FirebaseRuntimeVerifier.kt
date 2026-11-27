package com.example.data

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp

private const val VERIFIER_TAG = "FirebaseVerifier"

data class FirebaseCheckItem(
    val id: String,
    val label: String,
    val passed: Boolean,
    val detail: String
)

sealed interface FirebaseInitState {
    val checks: List<FirebaseCheckItem>

    data class Ready(
        val projectId: String,
        val databaseId: String,
        override val checks: List<FirebaseCheckItem>
    ) : FirebaseInitState

    data class NotConfigured(
        val summaryReason: String,
        override val checks: List<FirebaseCheckItem>
    ) : FirebaseInitState

    data class InvalidConfiguration(
        val summaryReason: String,
        override val checks: List<FirebaseCheckItem>
    ) : FirebaseInitState
}

object FirebaseRuntimeVerifier {

    private val googleAppIdRegex = Regex("""^\d+:\d+:android:[0-9a-fA-F]{6,}$""")
    private val placeholderMarkers = listOf(
        "remixed-project-id",
        "your-project-id",
        "your_project_id",
        "placeholder",
        "replace-me",
        "example-project",
        "dummy"
    )

    /**
     * Runs live runtime verification of FirebaseApp initialization, Google Services generated
     * resources (`project_id`, `google_app_id`, `default_web_client_id`), and Firestore database config.
     */
    fun verify(context: Context): FirebaseInitState {
        val appCtx = context.applicationContext ?: context
        if (FirebaseApp.getApps(appCtx).isEmpty()) {
            runCatching { FirebaseApp.initializeApp(appCtx) }
                .onFailure { Log.w(VERIFIER_TAG, "FirebaseApp.initializeApp failed: ${it.message}") }
        }
        val initialized = FirebaseApp.getApps(appCtx).isNotEmpty()
        val options = if (initialized) runCatching { FirebaseApp.getInstance().options }.getOrNull() else null

        val projectId = options?.projectId?.trim()?.takeIf { it.isNotEmpty() }
            ?: resolveStringResource(appCtx, "project_id")
        val googleAppId = options?.applicationId?.trim()?.takeIf { it.isNotEmpty() }
            ?: resolveStringResource(appCtx, "google_app_id")
        val webClientId = resolveDefaultWebClientId(appCtx)
        val databaseId = resolveStringResource(appCtx, "firestore_database_id")

        return evaluateConfiguration(
            firebaseAppInitialized = initialized,
            projectId = projectId,
            googleAppId = googleAppId,
            webClientId = webClientId,
            databaseId = databaseId
        )
    }

    /**
     * Pure deterministic validation logic used at runtime and verified by JVM unit tests.
     */
    internal fun evaluateConfiguration(
        firebaseAppInitialized: Boolean,
        projectId: String?,
        googleAppId: String?,
        webClientId: String?,
        databaseId: String?
    ): FirebaseInitState {
        val cleanProjectId = projectId?.trim().orEmpty()
        val cleanAppId = googleAppId?.trim().orEmpty()
        val cleanWebClientId = webClientId?.trim().orEmpty()
        val cleanDbId = databaseId?.trim().orEmpty()

        val projectValid = cleanProjectId.isNotEmpty() && !isPlaceholder(cleanProjectId)
        val appIdValid = cleanAppId.isNotEmpty() && !isPlaceholder(cleanAppId) && googleAppIdRegex.matches(cleanAppId)
        val webClientValid = cleanWebClientId.isNotEmpty() &&
            !isPlaceholder(cleanWebClientId) &&
            cleanWebClientId.endsWith(".apps.googleusercontent.com")
        val dbValid = cleanDbId.isNotEmpty() && !isPlaceholder(cleanDbId)

        val checks = listOf(
            FirebaseCheckItem(
                id = "firebase_app",
                label = "Cloud App Initialization",
                passed = firebaseAppInitialized,
                detail = if (firebaseAppInitialized) "FirebaseApp active" else "Waiting for google-services.json"
            ),
            FirebaseCheckItem(
                id = "project_and_app_id",
                label = "Firebase Project & App ID",
                passed = projectValid && appIdValid,
                detail = when {
                    projectValid && appIdValid -> "Project connected"
                    cleanProjectId.isEmpty() && cleanAppId.isEmpty() -> "Not packaged in this build"
                    else -> "Invalid or placeholder project/app ID"
                }
            ),
            FirebaseCheckItem(
                id = "google_signin_client",
                label = "Google Sign-In Web Client",
                passed = webClientValid,
                detail = when {
                    webClientValid -> "Web OAuth client ready"
                    cleanWebClientId.isEmpty() -> "default_web_client_id missing"
                    else -> "Invalid OAuth Web Client ID"
                }
            ),
            FirebaseCheckItem(
                id = "firestore_db",
                label = "Cloud Firestore Database",
                passed = dbValid,
                detail = if (dbValid) "Database ID configured" else "firestore_database_id missing"
            )
        )

        if (firebaseAppInitialized && projectValid && appIdValid && webClientValid && dbValid) {
            return FirebaseInitState.Ready(
                projectId = cleanProjectId,
                databaseId = cleanDbId,
                checks = checks
            )
        }

        val configCompletelyAbsent = !firebaseAppInitialized && cleanProjectId.isEmpty() && cleanAppId.isEmpty()
        if (configCompletelyAbsent) {
            return FirebaseInitState.NotConfigured(
                summaryReason = "Cloud services are not connected yet because google-services.json was not included when this build was created.",
                checks = checks
            )
        }

        val reason = when {
            !projectValid || !appIdValid ->
                "The Firebase project or Android app identifier in google-services.json appears incomplete or invalid."
            !webClientValid ->
                "Firebase is partially configured, but Google Sign-In (default_web_client_id) is missing or invalid."
            !firebaseAppInitialized ->
                "Firebase resources were detected, but FirebaseApp failed to initialize at startup."
            else ->
                "Cloud Firestore database configuration is missing or invalid."
        }
        return FirebaseInitState.InvalidConfiguration(
            summaryReason = reason,
            checks = checks
        )
    }

    private fun isPlaceholder(value: String): Boolean {
        val lower = value.lowercase()
        return placeholderMarkers.any { marker -> lower.contains(marker) }
    }

    @SuppressLint("DiscouragedApi")
    private fun resolveStringResource(context: Context, name: String): String? {
        val resId = context.resources.getIdentifier(name, "string", context.packageName)
            .takeIf { it != 0 }
            ?: context.resources.getIdentifier(name, "string", "com.example")
        if (resId == 0) return null
        return runCatching { context.getString(resId) }
            .getOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }
}
