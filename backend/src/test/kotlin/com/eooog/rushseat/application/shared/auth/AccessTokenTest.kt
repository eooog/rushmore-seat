package com.eooog.rushseat.application.shared.auth

import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AccessTokenTest {

    private val prefix = "acc"
    private val tokenBody = "rPgv_HzZx7I6OMBV8B04CKr9WB-PB2yWItbMBFsV0vk"
    private val raw = "${prefix}_${tokenBody}"

    @Test
    fun `Valid Raw Can Be Parsed`() {

        val token = AccessToken.parse(raw)

        assertThat(token.value).isEqualTo(raw)
    }

    @Test
    fun `Trimmed Valid Raw Can Be Parsed`() {

        val rawWithSpace = "    ${prefix}_${tokenBody}    "

        val token = AccessToken.parse(rawWithSpace)

        assertThat(token.value).isEqualTo(raw)
    }

    @Test
    fun `InValid Token Prefix Throws Exception`() {

        val invalidPrefix = "aqq"

        val invalidRaw = "${invalidPrefix}_${tokenBody}"

        Assertions.assertThatThrownBy { AccessToken.parse(invalidRaw) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Invalid Access Token Format")
    }


    @Test
    fun `InValid Too Long Token Throws Exception`() {

        val invalidRaw = "acc_${"a".repeat(44)}"

        Assertions.assertThatThrownBy { AccessToken.parse(invalidRaw) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Invalid Access Token Format")
    }

    @Test
    fun `InValid Too Short Token Throws Exception`() {

        val invalidRaw = "acc_${"a".repeat(42)}"

        Assertions.assertThatThrownBy { AccessToken.parse(invalidRaw) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Invalid Access Token Format")
    }

    @Test
    fun `InValid Token Character Throws Exception`() {

        val invalidTokenBody = "rPgv_HzZx7I6OMBV8B04CKr9WB-PB2yWItbMBFsV0v!"

        val invalidRaw = "${raw}${invalidTokenBody}"

        assertThat(invalidTokenBody).hasSize(43)

        Assertions.assertThatThrownBy { AccessToken.parse(invalidRaw) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Invalid Access Token Format")
    }

    @Test
    fun `InValid Blank Token Throws Exception`() {

        val invalidTokenBody = "rPgv_HzZx7I6OMBV8B||CKr9WB-PB2yWItbMBFsV0vk"

        val invalidRaw = " ".repeat("${raw}${tokenBody}".length)

        Assertions.assertThatThrownBy { AccessToken.parse(invalidRaw) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Invalid Access Token Format")
    }

}