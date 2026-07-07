package com.eooog.rushseat.application.shared.auth

@JvmInline
value class AccessToken private constructor(
    val value: String,
) {

    companion object {
        private val PATTERN = Regex("^acc_[A-Za-z0-9_-]{43}$")

        fun parse(raw: String): AccessToken {
            return parseOrNull(raw)
                ?: throw IllegalArgumentException("Invalid access token format")
        }

        fun parseOrNull(raw: String?): AccessToken? {
            val normalized = raw?.trim()

            if (normalized.isNullOrBlank()) {
                return null
            }

            if (!PATTERN.matches(normalized)) {
                return null
            }

            return AccessToken(normalized)
        }
    }
}