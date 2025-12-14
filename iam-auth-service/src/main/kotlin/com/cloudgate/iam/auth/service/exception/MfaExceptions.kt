package com.cloudgate.iam.auth.service.exception

/**
 * MFA 등록/검증 시 발생하는 예외들의 공통 정의
 */
class MfaRegistrationNotFoundException(message: String) : RuntimeException(message)

/**
 * 입력된 TOTP 코드가 유효하지 않을 때 사용
 */
class MfaCodeInvalidException(message: String) : RuntimeException(message)
