package com.contextauth.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleUpdateClientTest {
    @Test
    fun parsesCurrentEmptyServerRules() {
        val hash = "a".repeat(64)
        val result = RuleUpdateClient.parseRulesResponse(
            """
            {
              "version": "3",
              "updated_at": "2026-05-18T00:00:00Z",
              "rules": [],
              "package_blocklist": [],
              "max_text_length": 128,
              "default_text_action": "REDACT",
              "rule_hash": "$hash"
            }
            """.trimIndent()
        )

        assertEquals("3", result.version)
        assertEquals(hash, result.ruleHash)
        assertEquals(hash, result.policy.ruleHash)
        assertTrue(result.policy.patternRules.isEmpty())
        assertTrue(result.policy.packageBlocklist.isEmpty())
        assertEquals(128, result.policy.maxTextLength)
    }

    @Test
    fun parsesDynamicRedactionPolicy() {
        val result = RuleUpdateClient.parseRulesResponse(
            """
            {
              "version": "4",
              "rules": [{"id": "ticket id", "target": "text", "action": "REDACT", "pattern": "TICKET-\\d+", "replacement": "<TICKET>"}],
              "package_blocklist": ["privatechat"],
              "max_text_length": 64,
              "rule_hash": "${"b".repeat(64)}"
            }
            """.trimIndent()
        )

        assertEquals(listOf("privatechat"), result.policy.packageBlocklist)
        assertEquals(64, result.policy.maxTextLength)
        assertEquals("ticket_id", result.policy.patternRules.single().name)
        assertEquals("<TICKET>", result.policy.patternRules.single().replacement)
    }

    @Test
    fun parsesDropAndAllowActionsFromServerSchema() {
        val result = RuleUpdateClient.parseRulesResponse(
            """
            {
              "version": "5",
              "rules": [
                {"id": "drop-secret", "target": "text", "action": "DROP", "pattern": "SECRET"},
                {"id": "allow-debug", "target": "text", "action": "ALLOW", "pattern": "DEBUG"}
              ],
              "package_blocklist": [],
              "max_text_length": 128,
              "rule_hash": "${"c".repeat(64)}"
            }
            """.trimIndent()
        )

        assertEquals(1, result.policy.patternRules.size)
        assertEquals("drop-secret", result.policy.patternRules.single().name)
        assertEquals("<DROPPED>", result.policy.patternRules.single().replacement)
    }
}
