package com.eooog.rushseat.application.shared.auth

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import java.util.Base64

class AccessTokenGeneratorTest {
    private val tokenGenerator = AccessTokenGenerator()

    private val prefix = "acc"

    @Test
    fun `generated token has acc prefix and valid base64url payload`() {
        val token = tokenGenerator.generate()

        assertThat(token).isNotNull()

        val expectedPrefix = "${prefix}_"

        assertThat(token.value).startsWith(expectedPrefix)

        val prefixCreated = token.value.substringBefore("_")
        val tokenCreated = token.value.removePrefix(expectedPrefix)

        assertThat(prefixCreated).isEqualTo(prefix)

        assertThat(tokenCreated.length).isEqualTo(43)

        assertThatCode { Base64.getUrlDecoder().decode(tokenCreated) }
            .doesNotThrowAnyException()
    }

    @Test
    fun `generated token has expected total length`() {
        val token = tokenGenerator.generate()

        assertThat(token.value).hasSize(47)
    }

    @Test
    fun `generated token should be parsed`() {
        val token = tokenGenerator.generate()

        val raw = token.value

        val parsed = AccessToken.parse(raw)

        assertThat(parsed.value).isEqualTo(raw)
    }

    @Test
    fun `generated tokens are not repeated`() {
        val tokens =
            (1..100)
                .map { tokenGenerator.generate().value }

        assertThat(tokens).doesNotHaveDuplicates()
    }
}
