package com.cloudgate.iam.account.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.test.context.ActiveProfiles

@DataJpaTest
@ActiveProfiles("test")
class TenantRepositoryTest @Autowired constructor(
    private val tenantRepository: TenantRepository
) {

    @Test
    fun `테넌트 코드가 중복되면 예외가 발생한다`() {
        val tenant = Tenant(code = "tenant-a", name = "Tenant A")
        tenantRepository.saveAndFlush(tenant)

        val duplicated = Tenant(code = "tenant-a", name = "Tenant A Duplicate")

        assertThrows<DataIntegrityViolationException> {
            tenantRepository.saveAndFlush(duplicated)
        }
    }

    @Test
    fun `코드 기반 조회가 가능하다`() {
        val tenant = Tenant(code = "tenant-b", name = "Tenant B")
        tenantRepository.saveAndFlush(tenant)

        val found = tenantRepository.findByCode("tenant-b")

        assertThat(found).isNotNull
        assertThat(found?.code).isEqualTo("tenant-b")
    }
}
