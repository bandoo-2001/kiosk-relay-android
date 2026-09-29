package io.github.kioskrelay.web

/**
 * Observable state of the kiosk page.
 *
 * URLs are kept here for UI rendering only. Callers must redact them before writing logs because
 * query strings and fragments can contain credentials.
 */
sealed interface KioskUiState {
    data object Starting : KioskUiState

    data class CompatibilityWarning(val version: String) : KioskUiState

    data class Loading(
        val url: String,
    ) : KioskUiState

    data class Online(
        val url: String,
    ) : KioskUiState

    data class Offline(
        val lastUrl: String?,
    ) : KioskUiState

    data class PageError(
        val url: String?,
        val errorCode: Int?,
        val description: String,
    ) : KioskUiState

    data class BackoffRetry(
        val url: String,
        val attempt: Int,
        val delayMillis: Long,
    ) : KioskUiState

    data class RendererGone(
        val didCrash: Boolean,
        val lastUrl: String?,
    ) : KioskUiState

    data class Fatal(
        val lastUrl: String?,
        val reason: String,
    ) : KioskUiState
}
