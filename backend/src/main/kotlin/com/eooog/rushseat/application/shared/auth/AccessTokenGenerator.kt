package com.eooog.rushseat.application.shared.auth

import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.util.Base64

@Component
class AccessTokenGenerator {

    private val secureRandom = SecureRandom()

    fun generate(): AccessToken {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)

        val tokenValue = "acc_" + Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(bytes)

        return AccessToken.parse(tokenValue)
    }

}