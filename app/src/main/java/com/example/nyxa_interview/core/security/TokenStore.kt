package com.example.nyxa_interview.core.security

import kotlinx.coroutines.flow.Flow

data class TokenSet(
    val accessToken: String,
    val refreshToken: String,
    /** Epoch millis when [accessToken] expires. */
    val expiresAtMillis: Long,
)

/**
 * Persists auth tokens in platform secure storage only (Android Keystore-backed
 * EncryptedSharedPreferences). Never logs token values; callers must not either.
 */
interface TokenStore {
    fun observeTokens(): Flow<TokenSet?>
    suspend fun getTokens(): TokenSet?
    suspend fun saveTokens(tokens: TokenSet)
    suspend fun clear()
}
