package com.eooog.rushseat.common

import org.springframework.http.HttpStatus

class ApiError(
    val code: String,
    override val message: String,
    val status: HttpStatus,
) : RuntimeException(message)
