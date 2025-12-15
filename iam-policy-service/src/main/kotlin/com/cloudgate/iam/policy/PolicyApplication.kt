package com.cloudgate.iam.policy

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan(
    basePackages = [
        "com.cloudgate.iam.policy.config",
        "com.cloudgate.iam.common.config"
    ]
)
class PolicyApplication

fun main(args: Array<String>) {
    runApplication<PolicyApplication>(*args)
}
