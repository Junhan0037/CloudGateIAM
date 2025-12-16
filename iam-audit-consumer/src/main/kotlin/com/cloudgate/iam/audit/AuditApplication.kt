package com.cloudgate.iam.audit

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.kafka.annotation.EnableKafka

@SpringBootApplication
@EnableKafka
@ConfigurationPropertiesScan(
    basePackages = [
        "com.cloudgate.iam.audit.config",
        "com.cloudgate.iam.common.config"
    ]
)
class AuditApplication

fun main(args: Array<String>) {
    runApplication<AuditApplication>(*args)
}
