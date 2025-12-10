package com.cloudgate.iam.auth.web.dto

/**
 * 로그아웃 성공 시 반환하는 응답 DTO
 */
data class LogoutResponse(
    val success: Boolean = true,
    val message: String = "로그아웃이 완료되었습니다."
)
