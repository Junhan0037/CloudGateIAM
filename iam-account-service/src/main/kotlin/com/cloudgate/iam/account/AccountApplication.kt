package com.cloudgate.iam.account

import com.cloudgate.iam.common.tenant.TenantFilterConfiguration
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Import

@SpringBootApplication
@Import(TenantFilterConfiguration::class)
class AccountApplication

fun main(args: Array<String>) {
    runApplication<AccountApplication>(*args)
}
