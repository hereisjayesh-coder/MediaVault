package com.mediavault.app

import com.mediavault.app.support.SupportProjectConfig

/**
 * Single source of truth for every externally-facing URL/identifier this app hard-codes —
 * UPI ID, GitHub links, contact email. Settings (Support/Updates/About/Feedback sections) reads
 * only from here; nothing below should ever be duplicated as a literal string elsewhere.
 */
object AppConfig {
    const val GITHUB_REPOSITORY_URL = "https://github.com/hereisjayesh-coder/MediaVault"
    const val GITHUB_ISSUES_URL = "$GITHUB_REPOSITORY_URL/issues"
    const val GITHUB_RELEASES_URL = "$GITHUB_REPOSITORY_URL/releases"

    /** Settings' Feedback & Contact section is the only reader — see [com.mediavault.app.ui.screens.settings]. */
    const val FEEDBACK_EMAIL = "dallemahesh09@gmail.com"

    val support = SupportProjectConfig(
        projectName = "MediaVault",
        isFreeAdFreeOpenSource = true,
        upiId = "dimond077@ybl",
        upiPayeeName = "MediaVault",
        buyMeACoffeeUrl = "https://buymeacoffee.com/diamond077",
    )
}
