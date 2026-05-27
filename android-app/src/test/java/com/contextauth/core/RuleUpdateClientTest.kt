package com.contextauth.core

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleUpdateClientTest {
    @Test
    fun parsesEmptyServerRulesWithoutDisablingBaseline() {
        val body = withRuleHash(
            """
            {
              "version": "3",
              "updated_at": "2026-05-18T00:00:00Z",
              "rules": [],
              "package_blocklist": [],
              "max_text_length": 128,
              "default_text_action": "REDACT"
            }
            """.trimIndent()
        )
        val result = RuleUpdateClient.parseRulesResponse(body)

        assertEquals("3", result.version)
        assertEquals(result.ruleHash, result.policy.ruleHash)
        assertTrue(result.policy.patternRules.isEmpty())
        assertEquals(128, result.policy.maxTextLength)
        assertEquals("REDACT", result.policy.defaultTextAction)
    }

    @Test
    fun parsesNonEmptyDefaultServerRules() {
        val result = RuleUpdateClient.parseRulesResponse(
            withRuleHash(
            """
            {
              "version": "6",
              "updated_at": "2026-05-21T00:00:00Z",
              "rules": [
                {"id": "email", "target": "text", "action": "REDACT", "pattern": "\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b", "replacement": "<EMAIL>"},
                {"id": "url", "target": "text", "action": "REDACT", "pattern": "\\b(?:https?://|www\\.)[^\\s<>\\\"']+", "replacement": "<URL>"}
              ],
              "package_blocklist": ["bank", "password"],
              "max_text_length": 128,
              "default_text_action": "REDACT"
            }
            """.trimIndent()
            )
        )

        assertEquals("6", result.version)
        assertEquals(listOf("email", "url"), result.policy.patternRules.map { it.name })
        assertEquals("text", result.policy.patternRules.first().target)
    }

    @Test
    fun parsesDynamicRedactionPolicy() {
        val result = RuleUpdateClient.parseRulesResponse(
            withRuleHash(
            """
            {
              "version": "4",
              "updated_at": "2026-05-21T00:00:00Z",
              "rules": [{"id": "ticket id", "target": "text", "action": "REDACT", "pattern": "TICKET-\\d+", "replacement": "<TICKET>"}],
              "package_blocklist": ["privatechat"],
              "max_text_length": 64,
              "default_text_action": "REDACT"
            }
            """.trimIndent()
            )
        )

        assertEquals(64, result.policy.maxTextLength)
        assertEquals("ticket_id", result.policy.patternRules.single().name)
        assertEquals("<TICKET>", result.policy.patternRules.single().replacement)
        assertEquals("text", result.policy.patternRules.single().target)
    }

    @Test
    fun parsesDropAndAllowActionsFromServerSchema() {
        val result = RuleUpdateClient.parseRulesResponse(
            withRuleHash(
            """
            {
              "version": "5",
              "updated_at": "2026-05-21T00:00:00Z",
              "rules": [
                {"id": "drop-secret", "target": "text", "action": "DROP", "pattern": "SECRET"},
                {"id": "allow-debug", "target": "text", "action": "ALLOW", "pattern": "DEBUG"}
              ],
              "package_blocklist": [],
              "max_text_length": 128,
              "default_text_action": "REDACT"
            }
            """.trimIndent()
            )
        )

        assertEquals(1, result.policy.patternRules.size)
        assertEquals("drop-secret", result.policy.patternRules.single().name)
        assertEquals("<DROPPED>", result.policy.patternRules.single().replacement)
    }

    @Test
    fun acceptsMismatchedRuleHashUsingComputedHash() {
        val result = RuleUpdateClient.parseRulesResponse(
            """
            {
              "version": "5",
              "updated_at": "2026-05-21T00:00:00Z",
              "rules": [],
              "package_blocklist": [],
              "max_text_length": 128,
              "default_text_action": "REDACT",
              "rule_hash": "${"c".repeat(64)}"
            }
            """.trimIndent()
        )

        assertEquals("5", result.version)
        assertFalse(result.ruleHash == "c".repeat(64))
        assertTrue(result.ruleHash.matches(Regex("^[a-f0-9]{64}$")))
        assertTrue(result.message.contains("hash mismatch ignored"))
    }

    @Test
    fun acceptsMissingVersionAndRuleHashWithSafeDefaults() {
        val result = RuleUpdateClient.parseRulesResponse(
            """
            {
              "updated_at": "2026-05-21T00:00:00Z",
              "rules": [{"id": "bad", "target": "text", "action": "REDACT", "pattern": "["}],
              "package_blocklist": [],
              "max_text_length": 0,
              "default_text_action": "ALLOW"
            }
            """.trimIndent()
        )

        assertEquals(RuleDefaults.BASELINE_VERSION, result.version)
        assertTrue(result.ruleHash.matches(Regex("^[a-f0-9]{64}$")))
        assertEquals(1, result.policy.maxTextLength)
        assertEquals("REDACT", result.policy.defaultTextAction)
        assertTrue(result.policy.patternRules.isEmpty())
    }

    @Test
    fun skipsPackageNameTargetsAndParsesContentDescriptionTargets() {
        val result = RuleUpdateClient.parseRulesResponse(
            withRuleHash(
                """
                {
                  "version": "7",
                  "updated_at": "2026-05-21T00:00:00Z",
                  "rules": [
                    {"id": "skip-private", "target": "package_name", "action": "DROP", "pattern": "privatechat"},
                    {"id": "desc", "target": "content_description", "action": "REDACT", "pattern": "VIP", "replacement": "<VIP>"}
                  ],
                  "package_blocklist": [],
                  "max_text_length": 128,
                  "default_text_action": "REDACT"
                }
                """.trimIndent()
            )
        )

        assertEquals(listOf("content_description"), result.policy.patternRules.map { it.target })
        assertEquals(listOf("REDACT"), result.policy.patternRules.map { it.action })
    }

    private fun withRuleHash(payload: String): String {
        val json = JSONObject(payload)
        json.put("rule_hash", RuleUpdateClient.canonicalRuleHash(json))
        return json.toString()
    }
}
