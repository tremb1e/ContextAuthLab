package com.contextauth.redaction

import com.contextauth.core.RawNodeSnapshot
import com.contextauth.core.RedactionEngine
import com.contextauth.core.RedactionPatternRule
import com.contextauth.core.RedactionPolicy
import com.contextauth.core.RedactionSummary
import com.contextauth.core.sha256Hex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RedactionEngineTest {
    private val engine = RedactionEngine()

    @Test
    fun redactsFixedFormatSensitiveText() {
        val summary = RedactionSummary()
        val redacted = engine.redactText(
            "user@example.com 13800138000 https://e.co 4111111111111111 11010519491231002X abcdefabcdefabcdefabcdefabcdefab",
            summary
        )
        assertFalse(redacted!!.contains("user@example.com"))
        assertFalse(redacted.contains("13800138000"))
        assertFalse(redacted.contains("https://example.com"))
        assertTrue(redacted.contains("<EMAIL>"))
        assertTrue(redacted.contains("<PHONE>"))
        assertTrue(redacted.contains("<URL>"))
        assertTrue(redacted.contains("<CARD>"))
        assertTrue(redacted.contains("<ID_NUM>"))
        assertTrue(redacted.contains("<TOKEN>"))
    }

    @Test
    fun dropsEditableAndPasswordNodes() {
        val summary = RedactionSummary()
        val editable = engine.sanitizeNode(
            RawNodeSnapshot(
                "1", "pkg", "android.widget.EditText", null, "secret", null,
                false, true, false, false, false, true, false, false, false,
                0, 0, emptyMap(), emptyList()
            ),
            summary
        )
        assertEquals("<EDITABLE_TEXT_DROPPED>", editable!!.textRedacted)

        val password = engine.sanitizeNode(
            RawNodeSnapshot(
                "2", "pkg", "android.widget.EditText", null, "secret", null,
                false, true, false, false, false, true, false, false, true,
                0, 0, emptyMap(), emptyList()
            ),
            summary
        )
        assertNull(password)
        assertEquals(1, summary.droppedPasswordNodes)
    }

    @Test
    fun sanitizesNodeIdentifiersAndContentDescription() {
        val summary = RedactionSummary()
        val node = engine.sanitizeNode(
            RawNodeSnapshot(
                "3",
                "com.example.notes",
                "android.widget.TextView",
                "com.example.notes:id/account_email",
                "Contact user@example.com",
                "Call 13800138000",
                true,
                false,
                false,
                false,
                false,
                true,
                false,
                false,
                false,
                0,
                0,
                emptyMap(),
                emptyList()
            ),
            summary
        )!!

        assertEquals(sha256Hex("com.example.notes"), node.packageNameHash)
        assertEquals(sha256Hex("com.example.notes:id/account_email"), node.viewIdHash)
        assertFalse(node.packageNameHash!!.contains("com.example"))
        assertFalse(node.viewIdHash!!.contains("account_email"))
        assertEquals("Contact <EMAIL>", node.textRedacted)
        assertEquals("Call <PHONE>", node.contentDescRedacted)
    }

    @Test
    fun skipsSensitivePackages() {
        assertTrue(engine.shouldSkipPackage("com.example.bank.mobile"))
        assertTrue(engine.shouldSkipPackage("org.telegram.messenger"))
        assertFalse(engine.shouldSkipPackage("com.example.notes"))
    }

    @Test
    fun appliesServerFetchedPolicyWithoutDisablingBaselineRules() {
        val dynamic = RedactionEngine {
            RedactionPolicy(
                packageBlocklist = listOf("privatechat"),
                maxTextLength = 16,
                patternRules = listOf(RedactionPatternRule("ticket", """TICKET-\d+""", "<TICKET>"))
            )
        }
        val summary = RedactionSummary()

        assertTrue(dynamic.shouldSkipPackage("com.example.privatechat"))
        assertEquals("<LONG_TEXT_DROPPED>", dynamic.redactText("12345678901234567", summary))
        assertEquals("<TICKET>", dynamic.redactText("TICKET-42", summary))
        assertEquals("<EMAIL>", dynamic.redactText("a@b.co", summary))
        assertEquals(1, summary.dynamicRuleHits["ticket"])
    }

    @Test
    fun emptyServerPolicyKeepsBaselineRedaction() {
        val dynamic = RedactionEngine {
            RedactionPolicy(
                packageBlocklist = emptyList(),
                maxTextLength = 128,
                patternRules = emptyList()
            )
        }
        val summary = RedactionSummary()

        val redacted = dynamic.redactText("TICKET-42 user@example.com 13800138000", summary)!!

        assertTrue(redacted.contains("TICKET-42"))
        assertFalse(redacted.contains("user@example.com"))
        assertFalse(redacted.contains("13800138000"))
        assertTrue(redacted.contains("<EMAIL>"))
        assertTrue(redacted.contains("<PHONE>"))
        assertTrue(summary.dynamicRuleHits.isEmpty())
    }

    @Test
    fun dynamicRulesDoNotDisableBaselineRedaction() {
        val dynamic = RedactionEngine {
            RedactionPolicy(
                patternRules = listOf(RedactionPatternRule("ticket", """TICKET-\d+""", "<TICKET>"))
            )
        }

        val redacted = dynamic.redactText("TICKET-42 user@example.com", RedactionSummary())
        assertEquals("<TICKET> <EMAIL>", redacted)
    }
}
