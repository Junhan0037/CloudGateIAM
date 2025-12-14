package com.cloudgate.iam.auth.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

/**
 * 시간 기반 계산을 테스트 가능하게 만들기 위한 Clock 빈 설정
 */
@Configuration
class ClockConfig {

    @Bean
    fun systemClock(): Clock = Clock.systemUTC()
}
