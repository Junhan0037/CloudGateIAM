package com.cloudgate.iam.policy

import com.cloudgate.iam.common.tenant.TenantFilterConfiguration
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Import

@SpringBootApplication
@ConfigurationPropertiesScan(
    basePackages = [
        "com.cloudgate.iam.policy.config",
        "com.cloudgate.iam.common.config"
    ]
)
@Import(TenantFilterConfiguration::class)
class PolicyApplication

fun main(args: Array<String>) {
    runApplication<PolicyApplication>(*args)
}
