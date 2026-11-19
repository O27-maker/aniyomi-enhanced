package eu.kanade.tachiyomi.data.updater

import android.content.Context
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
