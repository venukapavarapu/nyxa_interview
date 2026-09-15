package com.example.nyxa_interview

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.nyxa_interview.presentation.navigation.AppViewModel
import com.example.nyxa_interview.presentation.navigation.NyxaNavHost
import com.example.nyxa_interview.ui.theme.Nyxa_interviewTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Nyxa_interviewTheme {
                val appViewModel: AppViewModel = hiltViewModel()
                val isLoggedIn by appViewModel.isLoggedIn.collectAsState()

                when (val loggedIn = isLoggedIn) {
                    null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }

                    else -> NyxaNavHost(isLoggedIn = loggedIn, showDebugControls = BuildConfig.DEBUG)
                }
            }
        }
    }
}
