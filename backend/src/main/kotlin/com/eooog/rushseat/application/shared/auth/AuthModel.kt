package com.eooog.rushseat.application.shared.auth

data class IssueAccessTokenCommand(
    val memberId: Long,
)

data class IssueAccessTokenResult(
    val accessToken: AccessToken,
    val memberId: Long,
)