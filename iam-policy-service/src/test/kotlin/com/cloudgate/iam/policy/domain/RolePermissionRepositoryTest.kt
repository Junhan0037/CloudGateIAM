package com.cloudgate.iam.policy.domain

import com.cloudgate.iam.common.domain.RoleScope
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.dao.DataIntegrityViolationException

@DataJpaTest
class RolePermissionRepositoryTest @Autowired constructor(
    private val roleRepository: RoleRepository,
    private val permissionRepository: PermissionRepository,
    private val rolePermissionRepository: RolePermissionRepository
) {

    @Test
    fun `역할에 권한을 매핑하고 조회할 수 있다`() {
        val role = roleRepository.saveAndFlush(
            Role(
                tenantId = 10L,
                name = "TENANT_USER",
                scope = RoleScope.TENANT
            )
        )
        val permission = permissionRepository.saveAndFlush(
            Permission(
                resource = "iam.user",
                action = "read",
                description = "사용자 정보 조회"
            )
        )
        rolePermissionRepository.saveAndFlush(
            RolePermission(
                role = role,
                permission = permission,
                tenantId = 10L
            )
        )

        val permissions = rolePermissionRepository.findByRoleId(role.id!!)

        assertThat(permissions).hasSize(1)
        assertThat(permissions.first().permission.action).isEqualTo("read")
    }

    @Test
    fun `동일 권한을 동일 역할에 중복 매핑할 수 없다`() {
        val role = roleRepository.saveAndFlush(Role(tenantId = 11L, name = "PROJECT_ADMIN", scope = RoleScope.PROJECT))
        val permission = permissionRepository.saveAndFlush(Permission(resource = "compute.instance", action = "write"))

        rolePermissionRepository.saveAndFlush(RolePermission(role = role, permission = permission, tenantId = 11L))

        assertThrows<DataIntegrityViolationException> {
            rolePermissionRepository.saveAndFlush(RolePermission(role = role, permission = permission, tenantId = 11L))
        }
    }
}
