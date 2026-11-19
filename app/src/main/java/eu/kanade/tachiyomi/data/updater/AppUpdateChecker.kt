package eu.kanade.tachiyomi.data.updater

import android.content.Context
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.util.system.isPreviewBuildType
import tachiyomi.domain.release.interactor.GetApplicationRelease

/**
 * Upstream Aniyomi release checks are disabled for Aniyomi+.
 * A dedicated Aniyomi+ release checker will be added when our release channel is ready.
 */
class AppUpdateChecker {
    suspend fun checkForUpdate(
        context: Context,
        forceCheck: Boolean = false,
    ): GetApplicationRelease.Result = GetApplicationRelease.Result.NoNewUpdate
}

/** Retained for the About screen and update links. */
val GITHUB_REPO: String by lazy {
    if (isPreviewBuildType) {
        "aniyomiorg/aniyomi-preview"
    } else {
        "aniyomiorg/aniyomi"
    }
}

val RELEASE_TAG: String by lazy {
    if (isPreviewBuildType) {
        "r${BuildConfig.COMMIT_COUNT}"
    } else {
        "v${BuildConfig.VERSION_NAME}"
    }
}

val RELEASE_URL = "https://github.com/$GITHUB_REPO/releases/tag/$RELEASE_TAG"
