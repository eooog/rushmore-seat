package com.eooog.rushseat.application.shared.auth

import com.eooog.rushseat.application.shared.auth.provided.AccessTokenVerifier
import com.eooog.rushseat.application.shared.auth.provided.IssueAccessTokenUseCase
import com.eooog.rushseat.application.shared.auth.required.AccessTokenStorePort
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Duration

@Service
class AuthService(
    private val accessTokenGenerator: AccessTokenGenerator,
    private val accessTokenStorePort: AccessTokenStorePort,
    @Value("\${rushmore-seat.auth.access-token-ttl:600s}") private val accessTokenTtl: Duration,
) : IssueAccessTokenUseCase,
    AccessTokenVerifier {
    override fun issue(command: IssueAccessTokenCommand): IssueAccessTokenResult {
        require(command.memberId > 0) {
            "memberId must be positive"
        }

        val accessToken = accessTokenGenerator.generate()

        accessTokenStorePort.save(
            accessToken = accessToken,
            memberId = command.memberId,
            ttl = accessTokenTtl,
        )

        return IssueAccessTokenResult(
            accessToken = accessToken,
            memberId = command.memberId,
        )
    }

    override fun verify(token: AccessToken): MemberPrincipal? {
        val memberId =
            accessTokenStorePort.findMemberId(token)
                ?: return null

        return MemberPrincipal(memberId = memberId)
    }
}
