package com.cloudgate.iam.policy.domain

import com.cloudgate.iam.common.domain.RoleScope
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.dao.DataIntegrityViolationException

@DataJpaTest
class RoleRepositoryTest @Autowired constructor(
    private val roleRepository: RoleRepository
) {

    @Test
    fun `테넌트별 역할을 저장하고 조회할 수 있다`() {
        val role = roleRepository.saveAndFlush(
            Role(
                tenantId = 1L,
                name = "TENANT_ADMIN",
                scope = RoleScope.TENANT,
                description = "테넌트 관리자"
            )
        )

        val found = roleRepository.findByTenantIdAndName(1L, "TENANT_ADMIN")

        assertThat(found).isNotNull
        assertThat(found?.id).isEqualTo(role.id)
        assertThat(found?.scope).isEqualTo(RoleScope.TENANT)
    }

    @Test
    fun `동일 테넌트와 스코프 내 중복 역할 이름은 막는다`() {
        roleRepository.saveAndFlush(Role(tenantId = 2L, name = "TENANT_USER", scope = RoleScope.TENANT))

        assertThrows<DataIntegrityViolationException> {
            roleRepository.saveAndFlush(Role(tenantId = 2L, name = "TENANT_USER", scope = RoleScope.TENANT))
        }
    }
}
