package com.cloudgate.iam.auth.web.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive

/**
 * 아이디/패스워드 로그인 요청 DTO
 */
data class LoginRequest(
    @field:NotNull
    @field:Positive
    val tenantId: Long?,

    @field:NotBlank
    val username: String,

    @field:NotBlank
    val password: String
)
