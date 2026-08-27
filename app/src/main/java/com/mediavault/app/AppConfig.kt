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

    /**
     * No contact email is configured for this project (none exists anywhere in the repo's own
     * documentation — see PRIVACY.md/TERMS.md/CONTRIBUTING.md). Left `null` deliberately rather
     * than inventing one; Feedback falls back to [GITHUB_ISSUES_URL], the support channel those
     * documents actually name.
     */
    val supportEmail: String? = null

    val support = SupportProjectConfig(
        projectName = "MediaVault",
        isFreeAdFreeOpenSource = true,
        upiId = "dimond077@ybl",
        upiPayeeName = "MediaVault",
        buyMeACoffeeUrl = "https://buymeacoffee.com/diamond077",
    )
}
