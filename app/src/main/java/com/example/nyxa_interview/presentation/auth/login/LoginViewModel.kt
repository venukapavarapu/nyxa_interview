package com.example.nyxa_interview.presentation.auth.login

import androidx.lifecycle.viewModelScope
import com.example.nyxa_interview.core.result.AppError
import com.example.nyxa_interview.core.result.AppResult
import com.example.nyxa_interview.domain.usecase.LoginUseCase
import com.example.nyxa_interview.presentation.common.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val loginUseCase: LoginUseCase,
) : MviViewModel<LoginUiState, LoginIntent, LoginEffect>(LoginUiState()) {

    override fun onIntent(intent: LoginIntent) {
        when (intent) {
            is LoginIntent.EmailChanged -> setState { copy(email = intent.value, errorMessage = null) }
            is LoginIntent.PasswordChanged -> setState { copy(password = intent.value, errorMessage = null) }
            LoginIntent.DismissError -> setState { copy(errorMessage = null) }
            LoginIntent.Submit -> submit()
        }
    }

    private fun submit() {
        if (!currentState.isSubmitEnabled) return
        setState { copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            when (val result = loginUseCase(currentState.email, currentState.password)) {
                is AppResult.Success -> {
                    setState { copy(isLoading = false) }
                    sendEffect(LoginEffect.NavigateToHome)
                }

                is AppResult.Error -> setState {
                    copy(isLoading = false, errorMessage = result.error.toMessage())
                }
            }
        }
    }

    private fun AppError.toMessage(): String = when (this) {
        AppError.Network -> "Network hiccup, please try again."
        AppError.Timeout -> "Request timed out, please try again."
        AppError.Unauthorized -> "Invalid email or password."
        is AppError.Server -> "Something went wrong on our end."
        is AppError.Unknown -> "Please enter your email and password."
    }
}
