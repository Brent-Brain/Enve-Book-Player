package com.enve.app.data.sync

import com.enve.app.data.personal.KavitaPersonalSync
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

private const val INTERVAL_MS = 90_000L

@Singleton
class ForegroundSync @Inject constructor(
    private val progressSync: KavitaProgressSync,
    private val personalSync: KavitaPersonalSync,
) {
    suspend fun run(): Nothing {
        while (true) {
            syncOnce()
            delay(INTERVAL_MS)
        }
    }

    suspend fun syncOnce() {
        try {
            personalSync.syncAll()
            progressSync.sync()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("ForegroundSync", "sync failed: ${e.message}")
        }
    }
}
