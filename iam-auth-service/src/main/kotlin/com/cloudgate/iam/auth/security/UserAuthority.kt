package com.cloudgate.iam.auth.security

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.security.core.GrantedAuthority

/**
 * 세션 직렬화를 고려한 단순 권한 표현 객체
 */
data class UserAuthority @JsonCreator constructor(
    @JsonProperty("authority")
    private val authorityValue: String
) : GrantedAuthority {

    override fun getAuthority(): String = authorityValue
}
