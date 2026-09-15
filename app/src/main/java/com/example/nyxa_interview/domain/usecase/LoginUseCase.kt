package com.example.nyxa_interview.domain.usecase

import com.example.nyxa_interview.core.result.AppError
import com.example.nyxa_interview.core.result.AppResult
import com.example.nyxa_interview.domain.repository.AuthRepository
import javax.inject.Inject

class LoginUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(email: String, password: String): AppResult<Unit> {
        if (email.isBlank() || password.isBlank()) {
            return AppResult.Error(AppError.Unknown())
        }
        return authRepository.login(email.trim(), password)
    }
}
