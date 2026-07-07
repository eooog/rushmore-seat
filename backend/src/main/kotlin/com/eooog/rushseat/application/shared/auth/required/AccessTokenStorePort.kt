package com.eooog.rushseat.application.shared.auth.required

import com.eooog.rushseat.application.shared.auth.AccessToken
import java.time.Duration

interface AccessTokenStorePort {

    fun save(
        accessToken: AccessToken,
        memberId: Long,
        ttl: Duration,
    )

    fun findMemberId(accessToken: AccessToken): Long?
}