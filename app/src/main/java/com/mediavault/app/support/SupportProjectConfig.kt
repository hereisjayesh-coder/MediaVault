package com.mediavault.app.support

/**
 * Everything a "Support the Project" UI needs to know about one project's donation options —
 * deliberately app-agnostic (no MediaVault-specific defaults here) so `SupportSection` and its
 * sibling composables (`ui/components/support/`) can be reused as-is by a future application:
 * construct a different [SupportProjectConfig] and pass it in, no code changes required.
 *
 * No payment SDK, no stored credentials, no donation tracking is implied or required by this
 * type — it only carries the plain data a UI needs to build a "copy UPI ID" action, a UPI
 * `ACTION_VIEW` intent, a QR code, and an external Buy Me a Coffee link.
 */
data class SupportProjectConfig(
    val projectName: String,
    val isFreeAdFreeOpenSource: Boolean,
    val upiId: String,
    val upiPayeeName: String,
    val buyMeACoffeeUrl: String,
)
