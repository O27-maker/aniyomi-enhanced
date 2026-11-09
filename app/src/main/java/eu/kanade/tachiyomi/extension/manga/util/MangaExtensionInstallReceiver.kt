package eu.kanade.tachiyomi.extension.manga.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.extension.manga.model.MangaExtension
import eu.kanade.tachiyomi.extension.manga.model.MangaLoadResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

/**
 * Broadcast receiver that listens for the system's packages installed, updated or removed, and only
 * notifies the given [listener] when the package is an extension.
 *
 * Package events are processed sequentially so a later removal cannot be overtaken by an earlier
 * asynchronous APK load.
 */
internal class MangaExtensionInstallReceiver(
    private val listener: Listener,
    scope: CoroutineScope,
) : BroadcastReceiver() {

    private val events = Channel<Event>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (event in events) {
                try {
                    handle(event)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    logcat(LogPriority.ERROR, e) { "Failed to handle manga extension package event: $event" }
                }
            }
        }
    }

    fun register(context: Context) {
        ContextCompat.registerReceiver(context, this, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    private val filter = IntentFilter().apply {
        addAction(Intent.ACTION_PACKAGE_ADDED)
        addAction(Intent.ACTION_PACKAGE_REPLACED)
        addAction(Intent.ACTION_PACKAGE_REMOVED)
        addAction(ACTION_EXTENSION_ADDED)
        addAction(ACTION_EXTENSION_REPLACED)
        addAction(ACTION_EXTENSION_REMOVED)
        addDataScheme("package")
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return

        when (intent.action) {
            Intent.ACTION_PACKAGE_ADDED, ACTION_EXTENSION_ADDED -> {
                if (isReplacing(intent)) return
                getPackageNameFromIntent(intent)?.let {
                    events.trySend(Event.Installed(context.applicationContext, it, isUpdate = false))
                }
            }
            Intent.ACTION_PACKAGE_REPLACED, ACTION_EXTENSION_REPLACED -> {
                getPackageNameFromIntent(intent)?.let {
                    events.trySend(Event.Installed(context.applicationContext, it, isUpdate = true))
                }
            }
            Intent.ACTION_PACKAGE_REMOVED, ACTION_EXTENSION_REMOVED -> {
                if (isReplacing(intent)) return
                getPackageNameFromIntent(intent)?.let { events.trySend(Event.Removed(it)) }
            }
        }
    }

    private suspend fun handle(event: Event) {
        when (event) {
            is Event.Installed -> {
                when (val result = MangaExtensionLoader.loadMangaExtensionFromPkgName(event.context, event.pkgName)) {
                    is MangaLoadResult.Success -> {
                        if (event.isUpdate) {
                            listener.onExtensionUpdated(result.extension)
                        } else {
                            listener.onExtensionInstalled(result.extension)
                        }
                    }
                    is MangaLoadResult.NotLoaded -> {
                        if (event.isUpdate) {
                            listener.onExtensionUpdated(result.extension)
                        } else {
                            listener.onExtensionInstalled(result.extension)
                        }
                    }
                    is MangaLoadResult.Untrusted -> listener.onExtensionUntrusted(result.extension)
                    MangaLoadResult.Error -> logcat(LogPriority.WARN) {
                        "Installed package is not a loadable manga extension: ${event.pkgName}"
                    }
                }
            }
            is Event.Removed -> listener.onPackageUninstalled(event.pkgName)
        }
    }

    private fun isReplacing(intent: Intent): Boolean {
        return intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
    }

    private fun getPackageNameFromIntent(intent: Intent?): String? {
        return intent?.data?.encodedSchemeSpecificPart
    }

    interface Listener {
        suspend fun onExtensionInstalled(extension: MangaExtension.Installed)
        suspend fun onExtensionUpdated(extension: MangaExtension.Installed)
        suspend fun onExtensionUntrusted(extension: MangaExtension.Untrusted)
        suspend fun onPackageUninstalled(pkgName: String)
    }

    private sealed interface Event {
        data class Installed(val context: Context, val pkgName: String, val isUpdate: Boolean) : Event
        data class Removed(val pkgName: String) : Event
    }

    companion object {
        private const val ACTION_EXTENSION_ADDED = "${BuildConfig.APPLICATION_ID}.ACTION_EXTENSION_ADDED"
        private const val ACTION_EXTENSION_REPLACED = "${BuildConfig.APPLICATION_ID}.ACTION_EXTENSION_REPLACED"
        private const val ACTION_EXTENSION_REMOVED = "${BuildConfig.APPLICATION_ID}.ACTION_EXTENSION_REMOVED"

        fun notifyAdded(context: Context, pkgName: String) {
            notify(context, pkgName, ACTION_EXTENSION_ADDED)
        }

        fun notifyReplaced(context: Context, pkgName: String) {
            notify(context, pkgName, ACTION_EXTENSION_REPLACED)
        }

        fun notifyRemoved(context: Context, pkgName: String) {
            notify(context, pkgName, ACTION_EXTENSION_REMOVED)
        }

        private fun notify(context: Context, pkgName: String, action: String) {
            Intent(action).apply {
                data = "package:$pkgName".toUri()
                `package` = context.packageName
                context.sendBroadcast(this)
            }
        }
    }
}
