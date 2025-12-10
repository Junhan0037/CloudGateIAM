package com.cloudgate.iam.policy

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class PolicyApplication

fun main(args: Array<String>) {
    runApplication<PolicyApplication>(*args)
}
