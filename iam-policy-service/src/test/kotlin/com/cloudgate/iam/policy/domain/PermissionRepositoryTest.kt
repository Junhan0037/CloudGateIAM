package com.cloudgate.iam.policy.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.dao.DataIntegrityViolationException

@DataJpaTest
class PermissionRepositoryTest @Autowired constructor(
    private val permissionRepository: PermissionRepository
) {

    @Test
    fun `권한을 저장하고 리소스-액션 기준으로 조회할 수 있다`() {
        permissionRepository.saveAndFlush(
            Permission(
                resource = "compute.instance",
                action = "read",
                description = "인스턴스 조회 권한"
            )
        )

        val found = permissionRepository.findByResourceAndAction("compute.instance", "read")

        assertThat(found).isNotNull
        assertThat(found?.action).isEqualTo("read")
    }

    @Test
    fun `동일 리소스-액션 조합은 중복 저장할 수 없다`() {
        permissionRepository.saveAndFlush(Permission(resource = "storage.bucket", action = "write"))

        assertThrows<DataIntegrityViolationException> {
            permissionRepository.saveAndFlush(Permission(resource = "storage.bucket", action = "write"))
        }
    }
}
