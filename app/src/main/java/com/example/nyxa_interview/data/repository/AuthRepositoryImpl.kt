package com.example.nyxa_interview.data.repository

import com.example.nyxa_interview.core.result.AppError
import com.example.nyxa_interview.core.result.AppResult
import com.example.nyxa_interview.core.security.AuthSessionManager
import com.example.nyxa_interview.core.security.TokenStore
import com.example.nyxa_interview.data.remote.mock.MockApiException
import com.example.nyxa_interview.data.remote.mock.MockBackend
import com.example.nyxa_interview.domain.model.Member
import com.example.nyxa_interview.domain.repository.AuthRepository
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val sessionManager: AuthSessionManager,
    private val tokenStore: TokenStore,
    private val mockBackend: MockBackend,
) : AuthRepository {

    override val isLoggedIn = tokenStore.observeTokens().map { it != null }

    override suspend fun login(email: String, password: String): AppResult<Unit> {
        return sessionManager.login(email, password).fold(
            onSuccess = { AppResult.Success(Unit) },
            onFailure = { throwable -> AppResult.Error(throwable.toAppError()) },
        )
    }

    override suspend fun logout() {
        sessionManager.logout()
    }

    override suspend fun currentMember(): AppResult<Member> {
        sessionManager.validAccessToken() ?: return AppResult.Error(AppError.Unauthorized)
        return try {
            AppResult.Success(mockBackend.currentMember())
        } catch (e: MockApiException) {
            AppResult.Error(e.toAppError())
        }
    }

    private fun Throwable.toAppError(): AppError = when (this) {
        is MockApiException.Unauthorized -> AppError.Unauthorized
        is MockApiException.ServerError -> AppError.Network
        is MockApiException.DroppedConnection -> AppError.Timeout
        is MockApiException.NotFound -> AppError.Server(message.orEmpty())
        else -> AppError.Unknown(this)
    }
}
