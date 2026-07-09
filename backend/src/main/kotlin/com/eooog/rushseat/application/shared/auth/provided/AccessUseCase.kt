package com.eooog.rushseat.application.shared.auth.provided

import com.eooog.rushseat.application.shared.auth.AccessToken
import com.eooog.rushseat.application.shared.auth.IssueAccessTokenCommand
import com.eooog.rushseat.application.shared.auth.IssueAccessTokenResult
import com.eooog.rushseat.application.shared.auth.MemberPrincipal

interface IssueAccessTokenUseCase {
    fun issue(command: IssueAccessTokenCommand): IssueAccessTokenResult
}

interface AccessTokenVerifier {
    fun verify(token: AccessToken): MemberPrincipal?
}
