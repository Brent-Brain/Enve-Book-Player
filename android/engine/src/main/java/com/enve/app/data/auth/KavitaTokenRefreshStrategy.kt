package com.enve.app.data.auth

import com.enve.core.auth.CredentialVault
import com.enve.core.data.local.ConnectionRegistry
import com.enve.core.data.local.PreferencesManager
import com.enve.core.data.remote.TokenRefreshContext
import com.enve.core.data.remote.TokenRefreshStrategy
import com.enve.core.data.remote.auth.applyConnectionAuthHeaders
import com.enve.core.di.RefreshClient
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject

class KavitaTokenRefreshStrategy @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val vault: CredentialVault,
    private val connectionRegistry: ConnectionRegistry,
    @RefreshClient private val refreshClientProvider: javax.inject.Provider<OkHttpClient>,
) : TokenRefreshStrategy {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private sealed interface Attempt {
        data class Success(val accessToken: String) : Attempt
        data object Rejected : Attempt
        data object Unavailable : Attempt
    }

    override fun refresh(context: TokenRefreshContext): String? {
        var rejected = false
        val refreshToken = context.refreshToken
        val accessToken = context.connectionId?.let { vault.get(CredentialVault.accessTokenKey(it)) }
            ?: preferencesManager.getAccessTokenSync()
        if (!refreshToken.isNullOrBlank() && !accessToken.isNullOrBlank()) {
            val body = buildJsonObject {
                put("token", accessToken)
                put("refreshToken", refreshToken)
            }
            when (val attempt = post(context, "api/Account/refresh-token", body)) {
                is Attempt.Success -> return attempt.accessToken
                Attempt.Rejected -> rejected = true
                Attempt.Unavailable -> Unit
            }
        }

        val username = context.username
        val password = context.password
        if (!username.isNullOrBlank() && !password.isNullOrBlank()) {
            val body = buildJsonObject {
                put("username", username)
                put("password", password)
            }
            when (val attempt = post(context, "api/Account/login", body)) {
                is Attempt.Success -> return attempt.accessToken
                Attempt.Rejected -> rejected = true
                Attempt.Unavailable -> return null
            }
        }

        if (rejected || password.isNullOrBlank()) markNeedsReauth(context)
        return null
    }

    private fun post(context: TokenRefreshContext, path: String, body: JsonObject): Attempt = try {
        val request = Request.Builder()
            .url("${context.serverUrl.trimEnd('/')}/$path")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .header("Accept", "application/json")
            .applyConnectionAuthHeaders(context.connection)
            .build()
        refreshClientProvider.get().newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                if (response.code in 400..499) Attempt.Rejected else Attempt.Unavailable
            } else {
                val parsed = json.parseToJsonElement(response.body?.string().orEmpty()).jsonObject
                val token = parsed["token"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                if (token == null) {
                    Attempt.Unavailable
                } else {
                    store(context.connectionId, token, parsed["refreshToken"]?.jsonPrimitive?.contentOrNull)
                    Attempt.Success(token)
                }
            }
        }
    } catch (_: Exception) {
        Attempt.Unavailable
    }

    private fun markNeedsReauth(context: TokenRefreshContext) {
        val connection = context.connection ?: return
        if (!connection.needsReauth) {
            runBlocking { connectionRegistry.upsert(connection.copy(needsReauth = true)) }
        }
    }

    private fun store(connectionId: String?, accessToken: String, refreshToken: String?) {
        runBlocking {
            preferencesManager.saveAuth(accessToken, refreshToken)
            connectionId?.let { id ->
                vault.put(CredentialVault.accessTokenKey(id), accessToken)
                if (!refreshToken.isNullOrBlank()) vault.put(CredentialVault.refreshTokenKey(id), refreshToken)
            }
        }
    }
}
