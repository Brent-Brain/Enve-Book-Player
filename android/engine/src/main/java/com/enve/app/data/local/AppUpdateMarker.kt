package com.enve.app.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val MARKER_FILE = "library-cache-install-stamp"

@Singleton
class AppUpdateMarker @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val markerFile = File(context.filesDir, MARKER_FILE)
    private val installStamp = context.packageManager
        .getPackageInfo(context.packageName, 0)
        .lastUpdateTime
        .toString()

    val pending: Boolean
        get() = runCatching { markerFile.readText() }.getOrNull() != installStamp

    fun markCurrent() {
        runCatching { markerFile.writeText(installStamp) }
    }
}
