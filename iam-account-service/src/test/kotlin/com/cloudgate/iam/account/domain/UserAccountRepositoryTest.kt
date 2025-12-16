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
class UserAccountRepositoryTest @Autowired constructor(
    private val tenantRepository: TenantRepository,
    private val userAccountRepository: UserAccountRepository
) {

    @Test
    fun `테넌트별 사용자 조회가 동작한다`() {
        val tenant = tenantRepository.saveAndFlush(Tenant(code = "tenant-c", name = "Tenant C", region = "KR"))
        userAccountRepository.saveAndFlush(
            UserAccount(
                tenant = tenant,
                username = "alice",
                email = "alice@example.com",
                passwordHash = "hashed-password"
            )
        )

        val found = userAccountRepository.findByTenantIdAndUsername(tenant.id!!, "alice")

        assertThat(found).isNotNull
        assertThat(found?.tenant?.id).isEqualTo(tenant.id)
        assertThat(found?.username).isEqualTo("alice")
    }

    @Test
    fun `테넌트 내 이메일 중복 시 무결성이 보장된다`() {
        val tenant = tenantRepository.saveAndFlush(Tenant(code = "tenant-d", name = "Tenant D", region = "KR"))
        userAccountRepository.saveAndFlush(
            UserAccount(
                tenant = tenant,
                username = "bob",
                email = "bob@example.com",
                passwordHash = "hashed-password"
            )
        )

        val duplicatedEmail = UserAccount(
            tenant = tenant,
            username = "bobby",
            email = "bob@example.com",
            passwordHash = "hashed-password"
        )

        assertThrows<DataIntegrityViolationException> {
            userAccountRepository.saveAndFlush(duplicatedEmail)
        }
    }
}
