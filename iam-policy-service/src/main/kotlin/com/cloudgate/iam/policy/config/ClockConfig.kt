package com.cloudgate.iam.policy.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

/**
 * 정책 서비스 전역에서 사용하는 UTC Clock 빈
 */
@Configuration
class ClockConfig {
    @Bean
    fun systemClock(): Clock = Clock.systemUTC()
}
