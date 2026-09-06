package com.dominikdomotor.nextcloudpasswords.autofill.debug

import android.content.Context
import com.dominikdomotor.nextcloudpasswords.BuildConfig
import com.dominikdomotor.nextcloudpasswords.GF
import java.io.File
import org.json.JSONObject

/**
 * The last few fill requests, on disk, newest first.
 *
 * A file rather than memory because the autofill service runs in its own process: whatever it saw is gone by the time
 * the settings screen is opened. Plain JSON so a capture can be shared and read outside the app.
 *
 * Every entry describes another app's screen - resource ids, form labels, and the first characters of any text already
 * in a field. Nothing here is written in a release build: every entry point is behind [enabled], which is a compile
 * time constant, so R8 removes the whole feature from the shipped app.
 */
object AutofillCaptureStore {
    val enabled: Boolean
        get() = BuildConfig.DEBUG

    /** Records one request, then drops all but the newest [KEEP]. */
    fun write(context: Context, capture: AutofillCapture) {
        if (!enabled) return
        runCatching {
                val dir = directory(context).apply { mkdirs() }
                File(dir, "${capture.takenAt}-${capture.id}.json").writeText(capture.toJson().toString(2))
                dir.listFiles()
                    ?.sortedByDescending { it.name }
                    ?.drop(KEEP)
                    ?.forEach { it.delete() }
            }
            .onFailure { GF.println("Autofill capture not saved: $it") }
    }

    /** Newest first, which is the order the inspector lists them in. */
    fun readAll(context: Context): List<AutofillCapture> {
        if (!enabled) return emptyList()
        val files = directory(context).listFiles()?.sortedByDescending { it.name } ?: return emptyList()
        return files.mapNotNull { file ->
            runCatching { AutofillCapture.fromJson(JSONObject(file.readText())) }
                .onFailure { GF.println("Autofill capture ${file.name} unreadable: $it") }
                .getOrNull()
        }
    }

    fun clear(context: Context) {
        directory(context).listFiles()?.forEach { it.delete() }
    }

    /**
     * On the shared external cache, so a capture can be handed to another app through a content URI.
     *
     * The service process and the main process both reach it, and `cache` rather than `files` because a capture is
     * disposable diagnostic data, not something worth surviving a low-storage cleanup.
     */
    private fun directory(context: Context): File = File(context.cacheDir, "autofill-captures")

    /** Enough to compare a few screens against each other without letting the folder grow unbounded. */
    private const val KEEP = 20
}
