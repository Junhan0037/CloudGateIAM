package com.cloudgate.iam.auth.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializer

/**
 * 세션 직렬화 방식을 JDK 직렬화로 지정해 보안 컨텍스트를 안전하게 저장
 */
@Configuration
class SessionConfig {

    @Bean(name = ["springSessionDefaultRedisSerializer"])
    fun springSessionDefaultRedisSerializer(): RedisSerializer<Any> = JdkSerializationRedisSerializer()
}
