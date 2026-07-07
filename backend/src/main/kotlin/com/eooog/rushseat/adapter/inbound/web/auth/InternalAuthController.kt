package com.eooog.rushseat.adapter.inbound.web.auth

import com.eooog.rushseat.application.shared.auth.IssueAccessTokenCommand
import com.eooog.rushseat.application.shared.auth.provided.IssueAccessTokenUseCase
import jakarta.validation.Valid
import jakarta.validation.constraints.Positive
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class InternalAuthController(
    private val issueAccessTokenUseCase: IssueAccessTokenUseCase,
) {

    @PostMapping("/internal/auth/access-tokens")
    fun issue(
        @Valid @RequestBody request: IssueAccessTokenRequest,
    ): IssueAccessTokenResponse {
        val result = issueAccessTokenUseCase.issue(
            IssueAccessTokenCommand(
                memberId = request.memberId,
            )
        )

        return IssueAccessTokenResponse(
            accessToken = result.accessToken.value,
            memberId = result.memberId,
        )
    }
}

data class IssueAccessTokenRequest(
    @field:Positive
    val memberId: Long,
)

data class IssueAccessTokenResponse(
    val accessToken: String,
    val memberId: Long,
)