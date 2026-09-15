package com.example.nyxa_interview.data.remote

import com.example.nyxa_interview.core.result.AppError
import com.example.nyxa_interview.core.result.AppResult
import com.example.nyxa_interview.core.security.AuthSessionManager
import com.example.nyxa_interview.data.remote.mock.MockApiException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps every mock-backend call with token attachment and a single silent-refresh-and-retry:
 * a call that fails as [MockApiException.Unauthorized] triggers one token refresh (coalesced
 * across concurrent callers by [AuthSessionManager]) and is retried exactly once with the new
 * token, matching the "requests in flight during refresh must be retried, not failed" rule.
 */
@Singleton
class AuthenticatedApiGateway @Inject constructor(
    private val sessionManager: AuthSessionManager,
) {
    suspend fun <T> call(block: suspend (accessToken: String) -> T): AppResult<T> {
        val token = sessionManager.validAccessToken()
            ?: return AppResult.Error(AppError.Unauthorized)

        return try {
            AppResult.Success(block(token))
        } catch (e: MockApiException.Unauthorized) {
            val refreshedToken = sessionManager.refreshAndGetToken()
                ?: return AppResult.Error(AppError.Unauthorized)
            try {
                AppResult.Success(block(refreshedToken))
            } catch (retryError: Exception) {
                AppResult.Error(retryError.toAppError())
            }
        } catch (e: Exception) {
            AppResult.Error(e.toAppError())
        }
    }

    private fun Throwable.toAppError(): AppError = when (this) {
        is MockApiException.Unauthorized -> AppError.Unauthorized
        is MockApiException.ServerError -> AppError.Network
        is MockApiException.DroppedConnection -> AppError.Timeout
        is MockApiException.NotFound -> AppError.Server(message.orEmpty())
        is MockApiException.InsufficientSpinCredits -> AppError.InsufficientCredits
        else -> AppError.Unknown(this)
    }
}
