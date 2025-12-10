package com.cloudgate.iam.policy.domain

import com.cloudgate.iam.common.domain.RoleScope
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.dao.DataIntegrityViolationException

@DataJpaTest
class UserRoleAssignmentRepositoryTest @Autowired constructor(
    private val roleRepository: RoleRepository,
    private val userRoleAssignmentRepository: UserRoleAssignmentRepository
) {

    @Test
    fun `사용자에게 역할을 매핑하고 조회할 수 있다`() {
        val role = roleRepository.saveAndFlush(Role(tenantId = 3L, name = "PROJECT_VIEWER", scope = RoleScope.PROJECT))
        userRoleAssignmentRepository.saveAndFlush(
            UserRoleAssignment(
                tenantId = 3L,
                userId = 100L,
                role = role,
                projectId = 50L
            )
        )

        val assignments = userRoleAssignmentRepository.findByTenantIdAndUserId(3L, 100L)

        assertThat(assignments).hasSize(1)
        assertThat(assignments.first().role.id).isEqualTo(role.id)
        assertThat(assignments.first().projectId).isEqualTo(50L)
    }

    @Test
    fun `동일한 역할을 중복 매핑하면 예외가 발생한다`() {
        val role = roleRepository.saveAndFlush(Role(tenantId = 4L, name = "SYSTEM_ADMIN", scope = RoleScope.SYSTEM))
        userRoleAssignmentRepository.saveAndFlush(
            UserRoleAssignment(
                tenantId = 4L,
                userId = 200L,
                role = role
            )
        )

        assertThrows<DataIntegrityViolationException> {
            userRoleAssignmentRepository.saveAndFlush(
                UserRoleAssignment(
                    tenantId = 4L,
                    userId = 200L,
                    role = role
                )
            )
        }
    }
}
