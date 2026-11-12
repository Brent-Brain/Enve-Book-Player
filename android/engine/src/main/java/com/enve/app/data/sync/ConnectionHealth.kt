package com.enve.app.data.sync

import com.enve.core.data.local.ConnectionRegistry
import kotlinx.coroutines.flow.first
import retrofit2.HttpException
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

class AuthRejectedException(message: String) : Exception(message)

fun Response<*>.requireOk(what: String) {
    if (isSuccessful) return
    if (code() == 401 || code() == 403) throw AuthRejectedException("$what rejected the sign-in (HTTP ${code()})")
    error("$what failed (HTTP ${code()})")
}

fun Throwable.isAuthFailure(): Boolean =
    this is AuthRejectedException || (this is HttpException && (code() == 401 || code() == 403))

@Singleton
class ConnectionHealth @Inject constructor(
    private val connectionRegistry: ConnectionRegistry,
) {
    suspend fun markHealthy(connectionId: String) = setNeedsReauth(connectionId, false)

    suspend fun markRejected(connectionId: String) = setNeedsReauth(connectionId, true)

    private suspend fun setNeedsReauth(connectionId: String, needsReauth: Boolean) {
        val connection = connectionRegistry.connections.first().firstOrNull { it.id == connectionId } ?: return
        if (connection.needsReauth != needsReauth) {
            connectionRegistry.upsert(connection.copy(needsReauth = needsReauth))
        }
    }
}
