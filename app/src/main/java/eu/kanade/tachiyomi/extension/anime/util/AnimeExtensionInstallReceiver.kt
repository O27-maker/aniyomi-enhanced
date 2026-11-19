package eu.kanade.tachiyomi.extension.anime.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.extension.anime.model.AnimeExtension
import eu.kanade.tachiyomi.extension.anime.model.AnimeLoadResult
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
 * Package events are processed sequentially so a later removal cannot overtake an earlier APK load.
 */
internal class AnimeExtensionInstallReceiver(
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
                    logcat(LogPriority.ERROR, e) { "Failed to handle anime extension package event: $event" }
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
                when (val result = AnimeExtensionLoader.loadExtensionFromPkgName(event.context, event.pkgName)) {
                    is AnimeLoadResult.Success -> {
                        if (event.isUpdate) {
                            listener.onExtensionUpdated(result.extension)
                        } else {
                            listener.onExtensionInstalled(result.extension)
                        }
                    }
                    is AnimeLoadResult.Untrusted -> listener.onExtensionUntrusted(result.extension)
                    is AnimeLoadResult.NotLoaded -> {
                        if (event.isUpdate) {
                            listener.onExtensionUpdated(result.extension)
                        } else {
                            listener.onExtensionInstalled(result.extension)
                        }
                    }
                    AnimeLoadResult.Error -> logcat(LogPriority.WARN) {
                        "Installed package is not a loadable anime extension: ${event.pkgName}"
                    }
                }
            }
            is Event.Removed -> listener.onPackageUninstalled(event.pkgName)
        }
    }

    /**
     * Returns true if this package is performing an update.
     *
     * @param intent The intent that triggered the event.
     */
    private fun isReplacing(intent: Intent): Boolean {
        return intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
    }

    /**
     * Returns the package name of the installed, updated or removed application.
     */
    private fun getPackageNameFromIntent(intent: Intent?): String? {
        return intent?.data?.encodedSchemeSpecificPart ?: return null
    }

    /**
     * Listener that receives extension installation events.
     */
    interface Listener {
        suspend fun onExtensionInstalled(extension: AnimeExtension.Installed)
        suspend fun onExtensionUpdated(extension: AnimeExtension.Installed)
        suspend fun onExtensionUntrusted(extension: AnimeExtension.Untrusted)
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
