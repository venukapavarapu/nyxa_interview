package com.example.nyxa_interview.core.security

import com.example.nyxa_interview.data.remote.mock.MockApiException
import com.example.nyxa_interview.data.remote.mock.NetworkConditions
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val ACCESS_TOKEN_TTL_MILLIS = 900_000L // 15 minutes, matches expiresIn: 900

/**
 * Owns the login/refresh lifecycle and guarantees a single in-flight refresh at a time: if two
 * requests race into a 401 simultaneously, only one network refresh call happens and both
 * callers await the same result and retry with the new token, per the "silent refresh, retry
 * in-flight requests" requirement.
 */
@Singleton
class AuthSessionManager @Inject constructor(
    private val tokenStore: TokenStore,
    private val networkConditions: NetworkConditions,
) {
    private val refreshMutex = Mutex()

    suspend fun login(email: String, password: String): Result<Unit> {
        networkConditions.simulateLatency()
        if (networkConditions.shouldFail()) return Result.failure(MockApiException.ServerError())
        if (email.isBlank() || password.isBlank()) return Result.failure(MockApiException.Unauthorized())

        tokenStore.saveTokens(issueTokens())
        return Result.success(Unit)
    }

    suspend fun logout() {
        tokenStore.clear()
    }

    /** Returns a non-expired access token, refreshing first if needed. Coalesces concurrent refreshes. */
    suspend fun validAccessToken(): String? {
        val current = tokenStore.getTokens() ?: return null
        if (System.currentTimeMillis() < current.expiresAtMillis) return current.accessToken
        return refreshAndGetToken()
    }

    suspend fun refreshAndGetToken(): String? = refreshMutex.withLock {
        val current = tokenStore.getTokens() ?: return@withLock null
        // Another caller may have already refreshed while we waited for the lock.
        if (System.currentTimeMillis() < current.expiresAtMillis) return@withLock current.accessToken

        val refreshed = issueTokens(refreshToken = current.refreshToken)
        tokenStore.saveTokens(refreshed)
        refreshed.accessToken
    }

    private fun issueTokens(refreshToken: String = UUID.randomUUID().toString()) = TokenSet(
        accessToken = "access_${UUID.randomUUID()}",
        refreshToken = refreshToken,
        expiresAtMillis = System.currentTimeMillis() + ACCESS_TOKEN_TTL_MILLIS,
    )
}
