package com.cloudgate.iam.auth

import com.cloudgate.iam.common.tenant.TenantFilterConfiguration
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Import
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@SpringBootApplication
@EntityScan(basePackages = ["com.cloudgate.iam.account.domain"])
@EnableJpaRepositories(basePackages = ["com.cloudgate.iam.account.domain"])
@ConfigurationPropertiesScan(
    basePackages = [
        "com.cloudgate.iam.auth.config",
        "com.cloudgate.iam.common.config"
    ]
)
@Import(TenantFilterConfiguration::class)
class AuthApplication

fun main(args: Array<String>) {
    runApplication<AuthApplication>(*args)
}
