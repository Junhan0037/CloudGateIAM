package com.cloudgate.iam.common.tenant

/**
 * 쓰레드 로컬 기반으로 현재 요청의 tenantId를 보관
 * - 유효성 검사(양수) 후 설정하며, 요청 종료 시 반드시 clear로 정리
 */
object TenantContextHolder {
    private val tenantIdHolder: ThreadLocal<Long?> = ThreadLocal()

    fun setTenantId(tenantId: Long) {
        require(tenantId > 0) { "tenantId는 0보다 커야 합니다." }
        tenantIdHolder.set(tenantId)
    }

    fun currentTenantId(): Long? = tenantIdHolder.get()

    fun clear() {
        tenantIdHolder.remove()
    }

    /**
     * 주어진 tenantId로 컨텍스트를 설정한 뒤 블록을 실행하고 항상 정리
     */
    inline fun <T> withTenant(tenantId: Long, block: () -> T): T {
        setTenantId(tenantId)
        return try {
            block()
        } finally {
            clear()
        }
    }
}
