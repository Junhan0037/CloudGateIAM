plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("io.spring.dependency-management")
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("jakarta.persistence:jakarta.persistence-api:3.1.0")
    implementation("org.springframework.boot:spring-boot-autoconfigure:3.5.8")
    implementation("org.springframework.boot:spring-boot-starter-aop:3.5.8")
    implementation("org.springframework:spring-tx:6.1.13")
    implementation("org.hibernate.orm:hibernate-core:6.6.1.Final")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
}
