package com.example.nyxa_interview.presentation.auth.login

import com.example.nyxa_interview.presentation.common.UiEffect
import com.example.nyxa_interview.presentation.common.UiIntent
import com.example.nyxa_interview.presentation.common.UiState

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
) : UiState {
    val isSubmitEnabled: Boolean get() = email.isNotBlank() && password.isNotBlank() && !isLoading
}

sealed interface LoginIntent : UiIntent {
    data class EmailChanged(val value: String) : LoginIntent
    data class PasswordChanged(val value: String) : LoginIntent
    data object Submit : LoginIntent
    data object DismissError : LoginIntent
}

sealed interface LoginEffect : UiEffect {
    data object NavigateToHome : LoginEffect
}
