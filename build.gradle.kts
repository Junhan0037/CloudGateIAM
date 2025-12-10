import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.9.25" apply false
    kotlin("plugin.spring") version "1.9.25" apply false
    kotlin("plugin.jpa") version "1.9.25" apply false
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
        jvmArgs("-Xshare:off") // 테스트 시 Gradle가 부트스트랩 클래스패스를 확장하면서 발생하는 CDS 경고를 차단

        // Mockito 인라인 모커의 동적 에이전트 주입 경고를 방지하기 위해 Byte Buddy 에이전트를 명시적으로 추가
        val agentJar = configurations.findByName("testRuntimeClasspath")
            ?.resolvedConfiguration
            ?.resolvedArtifacts
            ?.map { it.file }
            ?.firstOrNull { it.name.contains("byte-buddy-agent") }

        agentJar?.let { jvmArgs("-javaagent:${it.absolutePath}") }
    }
}
