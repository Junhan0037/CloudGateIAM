package com.cloudgate.iam.audit.config

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.kafka.ConcurrentKafkaListenerContainerFactoryConfigurer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.util.backoff.FixedBackOff
import java.time.Clock

/**
 * 감사 Kafka 리스너 공통 설정을 정의
 * - 재시도 간격과 횟수를 제한하고, 비즈니스 검증 실패(IllegalArgumentException)는 재시도하지 않음
 * - UTC Clock을 노출해 시간 소스를 통일
 */
@Configuration
class KafkaConsumerConfig {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 공통 설정을 적용한 리스너 컨테이너 팩토리에 에러 핸들러를 결합
     */
    @Bean
    fun kafkaListenerContainerFactory(
        configurer: ConcurrentKafkaListenerContainerFactoryConfigurer,
        consumerFactory: ConsumerFactory<Any, Any>,
        errorHandler: DefaultErrorHandler
    ): ConcurrentKafkaListenerContainerFactory<Any, Any> {
        val factory = ConcurrentKafkaListenerContainerFactory<Any, Any>()
        configurer.configure(factory, consumerFactory)
        factory.setCommonErrorHandler(errorHandler)
        return factory
    }

    /**
     * Kafka 재시도 정책과 로깅 전략을 정의한 에러 핸들러
     */
    @Bean
    fun errorHandler(): DefaultErrorHandler {
        val errorHandler = DefaultErrorHandler(FixedBackOff(1_000L, 3))
        errorHandler.addNotRetryableExceptions(IllegalArgumentException::class.java)
        return errorHandler
    }

    @Bean
    fun systemClock(): Clock = Clock.systemUTC()
}
