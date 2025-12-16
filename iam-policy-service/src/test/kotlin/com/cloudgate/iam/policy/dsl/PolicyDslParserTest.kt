package com.cloudgate.iam.policy.dsl

import com.cloudgate.iam.policy.domain.PolicyEffect
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import kotlin.test.assertIs

class PolicyDslParserTest {
    private val parser = PolicyDslParser(jacksonObjectMapper())

    @Test
    fun `축약형 조건 맵을 AND 트리로 파싱한다`() {
        val json = """
            {
              "conditions": {
                "user.department": "DEV",
                "resource.region": ["KR", "US"],
                "env.ip": "10.0.0.0/8"
              }
            }
        """.trimIndent()

        val document = parser.parse(
            resource = "compute.instance",
            actions = setOf("compute.instance:read"),
            effect = PolicyEffect.ALLOW,
            conditionJson = json
        )

        val root = document.condition
        assertIs<AllConditions>(root)
        assertEquals(3, root.conditions.size)

        val resourceRegion = root.conditions.filterIsInstance<MatchCondition>()
            .first { it.attribute.scope == AttributeScope.RESOURCE }
        assertEquals(AttributeOperator.IN, resourceRegion.operator)
        assertEquals(listOf("KR", "US"), resourceRegion.values)
    }

    @Test
    fun `all-any-not 트리를 중첩 파싱한다`() {
        val json = """
            {
              "version": "2024-10-01",
              "condition": {
                "any": [
                  {
                    "match": {
                      "attribute": "user.riskLevel",
                      "op": "LTE",
                      "value": 2
                    }
                  },
                  {
                    "not": {
                      "match": {
                        "attribute": "env.ip",
                        "op": "CIDR",
                        "value": "192.168.0.0/16"
                      }
                    }
                  }
                ]
              }
            }
        """.trimIndent()

        val document = parser.parse(
            resource = "iam.session",
            actions = setOf("iam.session:issue"),
            effect = PolicyEffect.DENY,
            conditionJson = json
        )

        val root = document.condition
        assertIs<AnyConditions>(root)
        assertEquals(2, root.conditions.size)

        val cidrNot = root.conditions.filterIsInstance<NotCondition>().first()
        val cidrMatch = (cidrNot.condition as MatchCondition)
        assertEquals(AttributeOperator.CIDR, cidrMatch.operator)
        assertEquals("192.168.0.0/16", cidrMatch.values.first())
    }

    @Test
    fun `잘못된 속성 prefix는 예외를 발생시킨다`() {
        val json = """
            {
              "condition": {
                "match": {
                  "attribute": "unknown.department",
                  "op": "EQ",
                  "value": "DEV"
                }
              }
            }
        """.trimIndent()

        assertThrows(IllegalArgumentException::class.java) {
            parser.parse(
                resource = "compute.instance",
                actions = setOf("compute.instance:write"),
                effect = PolicyEffect.ALLOW,
                conditionJson = json
            )
        }
    }
}
