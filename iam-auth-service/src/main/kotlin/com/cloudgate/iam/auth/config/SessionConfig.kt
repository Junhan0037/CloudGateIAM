package com.cloudgate.iam.auth.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializer

/**
 * 세션 직렬화 방식을 JSON으로 지정해 관측 및 디버깅 용이성을 확보
 */
@Configuration
class SessionConfig {

    @Bean(name = ["springSessionDefaultRedisSerializer"])
    fun springSessionDefaultRedisSerializer(): RedisSerializer<Any> = GenericJackson2JsonRedisSerializer()
}
