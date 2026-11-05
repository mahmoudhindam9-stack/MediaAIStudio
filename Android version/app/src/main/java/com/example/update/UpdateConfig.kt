package com.example.update

import com.example.BuildConfig

object UpdateConfig {
    const val EXPECTED_PACKAGE_NAME = "com.example.mediaaistudio"

    /**
     * Permanent Release Signing Certificate SHA-256 fingerprint (lowercase, no colons).
     * Injected securely via BuildConfig or environment.
     */
    val PERMANENT_RELEASE_CERT_SHA256: String = BuildConfig.PERMANENT_RELEASE_CERT_SHA256
        .lowercase()
        .replace(":", "")
        .trim()

    /**
     * Local development debug certificate fingerprint.
     */
    const val DEBUG_CERT_SHA256: String = "62a820f073d4797e2da54338d447b0317d225f501c4e9b8811d8a30c8494ab74"

    /**
     * Trusted set of certificate fingerprints.
     */
    val TRUSTED_SIGNATURE_FINGERPRINTS: Set<String> = buildSet {
        if (PERMANENT_RELEASE_CERT_SHA256.isNotBlank()) {
            add(PERMANENT_RELEASE_CERT_SHA256)
        }
        if (BuildConfig.DEBUG) {
            add(DEBUG_CERT_SHA256)
        }
    }
}
