package com.eooog.rushseat.application.shared.auth

import com.eooog.rushseat.application.shared.auth.AuthTestConfig.FakeAccessTokenStorePort
import com.eooog.rushseat.support.time.TestClock
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import java.time.Duration

@SpringJUnitConfig(AuthTestConfig::class)
class AuthServiceTest {
    @Autowired
    lateinit var authService: AuthService

    @Autowired
    lateinit var clock: TestClock

    @Autowired
    lateinit var fakeAccessTokenStorePort: FakeAccessTokenStorePort

    private val memberId = 259L
    private val accessTokenTtl = Duration.ofSeconds(600)

    @BeforeEach
    fun setUp() {
        clock.reset()
        fakeAccessTokenStorePort.clear()
    }

    @Test
    fun `issue() should return issued token and memberId`() {
        val issueResult =
            authService.issue(
                IssueAccessTokenCommand(memberId = memberId),
            )

        assertThat(issueResult.accessToken).isNotNull
        assertThat(issueResult.accessToken.value).startsWith("acc_")
        assertThat(issueResult.memberId).isEqualTo(memberId)
    }

    @Test
    fun `issue() should reject non-positive memberId`() {
        assertThatIllegalArgumentException()
            .isThrownBy {
                authService.issue(
                    IssueAccessTokenCommand(memberId = 0L),
                )
            }.withMessage("memberId must be positive")

        assertThatIllegalArgumentException()
            .isThrownBy {
                authService.issue(
                    IssueAccessTokenCommand(memberId = -1L),
                )
            }.withMessage("memberId must be positive")
    }

    @Test
    fun `verify() should return principal when token exists and not expired`() {
        val issueResult =
            authService.issue(
                IssueAccessTokenCommand(memberId = memberId),
            )

        val principal = authService.verify(issueResult.accessToken)

        assertThat(principal).isNotNull
        assertThat(principal!!.memberId).isEqualTo(memberId)
    }

    @Test
    fun `verify() should return null when token does not exist`() {
        val tokenBody = "a".repeat(43)

        val unknownToken =
            AccessToken.parse(
                "acc_$tokenBody",
            )

        val principal = authService.verify(unknownToken)

        assertThat(principal).isNull()
    }

    @Test
    fun `verify() should return principal before expiration boundary`() {
        val issueResult =
            authService.issue(
                IssueAccessTokenCommand(memberId = memberId),
            )

        clock.advance(accessTokenTtl.minusNanos(1))

        val principal = authService.verify(issueResult.accessToken)

        assertThat(principal).isNotNull
        assertThat(principal!!.memberId).isEqualTo(memberId)
    }

    @Test
    fun `verify() should return null at expiration boundary`() {
        val issueResult =
            authService.issue(
                IssueAccessTokenCommand(memberId = memberId),
            )

        clock.advance(accessTokenTtl)

        val principal = authService.verify(issueResult.accessToken)

        assertThat(principal).isNull()
    }
}
