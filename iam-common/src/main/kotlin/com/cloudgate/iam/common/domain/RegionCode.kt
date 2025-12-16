package com.cloudgate.iam.common.domain

/**
 * 리전 코드를 정규화하고 검증하는 유틸리티
 * - 영문/숫자/하이픈 조합만 허용하며, 대문자로 정규화해 일관된 비교가 가능하도록 처리
 */
object RegionCode {
    private val REGION_PATTERN: Regex = Regex("^[A-Za-z0-9-]{2,20}$")

    fun normalize(rawCode: String): String {
        val trimmed = rawCode.trim()

        require(trimmed.isNotEmpty()) { "리전 코드는 비어 있을 수 없습니다." }
        require(REGION_PATTERN.matches(trimmed)) {
            "리전 코드는 영문/숫자/하이픈으로 2~20자 이내여야 합니다. code=$rawCode"
        }

        return trimmed.uppercase()
    }
}
