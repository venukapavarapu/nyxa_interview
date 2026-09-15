package com.example.nyxa_interview.domain.repository

import com.example.nyxa_interview.core.result.AppResult
import com.example.nyxa_interview.domain.model.Member
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val isLoggedIn: Flow<Boolean>
    suspend fun login(email: String, password: String): AppResult<Unit>
    suspend fun logout()
    suspend fun currentMember(): AppResult<Member>
}
