package com.example.nyxa_interview.presentation.common

/** Marker for a screen's immutable render state. */
interface UiState

/** Marker for a user-originated intent dispatched to a [MviViewModel]. */
interface UiIntent

/** Marker for a one-shot side effect (navigation, snackbar) that should not be replayed on rotation. */
interface UiEffect
