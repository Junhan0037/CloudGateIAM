import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.9.25" apply false
    kotlin("plugin.spring") version "1.9.25" apply false
    id("org.springframework.boot") version "3.5.8" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
}

allprojects {
    group = "com.cloudgate.iam"
    version = "0.0.1-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    // Kotlin/JVM 설정을 공통 적용해 빌드 구성을 단순화
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions.freeCompilerArgs.add("-Xjsr305=strict")
    }

    // Java 21 툴체인을 강제해 JVM 타겟을 일관성 있게 유지
    plugins.withType<JavaPlugin> {
        extensions.configure<JavaPluginExtension> {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(21))
            }
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}
