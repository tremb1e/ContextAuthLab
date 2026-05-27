package com.contextauth.redaction

import com.contextauth.core.RawNodeSnapshot
import com.contextauth.core.RedactionEngine
import com.contextauth.core.RedactionPatternRule
import com.contextauth.core.RedactionPolicy
import com.contextauth.core.RedactionSummary
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
        assertFalse(redacted.contains("https://e.co"))
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
                false, true, false, false, false, true, false, false, true, false, false,
                0, 0, emptyMap(), emptyList()
            ),
            summary
        )
        assertEquals("<EDITABLE_TEXT_DROPPED>", editable!!.textRedacted)

        val password = engine.sanitizeNode(
            RawNodeSnapshot(
                "2", "pkg", "android.widget.EditText", null, "secret", null,
                false, true, false, false, false, true, false, false, true, false, true,
                0, 0, emptyMap(), emptyList()
            ),
            summary
        )
        assertNull(password)
        assertEquals(1, summary.droppedPasswordNodes)
    }

    @Test
    fun keepsAllowedNodeIdentifiersVisibleTextAndRedactsContentDescription() {
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
                true,
                false,
                false,
                0,
                0,
                emptyMap(),
                emptyList()
            ),
            summary
        )!!

        assertEquals("com.example.notes:id/account_email", node.viewIdResourceName)
        assertEquals("Contact <EMAIL>", node.text)
        assertNull(node.textRedacted)
        assertEquals("<TEXT_REDACTED> <PHONE>", node.contentDescRedacted)
    }

    @Test
    fun appliesServerFetchedPolicyWithoutDisablingBaselineRules() {
        val dynamic = RedactionEngine {
            RedactionPolicy(
                maxTextLength = 16,
                patternRules = listOf(RedactionPatternRule("ticket", """TICKET-\d+""", "<TICKET>"))
            )
        }
        val summary = RedactionSummary()

        assertEquals("<LONG_TEXT_DROPPED>", dynamic.redactText("12345678901234567", summary))
        assertEquals("<TICKET>", dynamic.redactText("TICKET-42", summary))
        assertEquals("<EMAIL>", dynamic.redactText("a@b.co", summary))
        assertEquals(1, summary.dynamicRuleHits["ticket"])
    }

    @Test
    fun emptyServerPolicyKeepsBaselineRedaction() {
        val dynamic = RedactionEngine {
            RedactionPolicy(
                maxTextLength = 128,
                patternRules = emptyList()
            )
        }
        val summary = RedactionSummary()

        val redacted = dynamic.redactText("TICKET-42 user@example.com 13800138000", summary)!!

        assertFalse(redacted.contains("TICKET-42"))
        assertFalse(redacted.contains("user@example.com"))
        assertFalse(redacted.contains("13800138000"))
        assertTrue(redacted.contains("<TEXT_REDACTED>"))
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

    @Test
    fun contentDescriptionRulesOnlyApplyToContentDescription() {
        val dynamic = RedactionEngine {
            RedactionPolicy(
                patternRules = listOf(RedactionPatternRule("desc", "VIP", "<VIP>", "content_description"))
            )
        }
        val node = dynamic.sanitizeNode(
            RawNodeSnapshot(
                "4",
                "pkg",
                "android.widget.TextView",
                null,
                "VIP label",
                "VIP details",
                false,
                false,
                false,
                false,
                false,
                true,
                false,
                false,
                true,
                false,
                false,
                0,
                0,
                emptyMap(),
                emptyList()
            ),
            RedactionSummary()
        )!!

        assertEquals("VIP label", node.text)
        assertNull(node.textRedacted)
        assertEquals("<VIP> <TEXT_REDACTED>", node.contentDescRedacted)
    }

    @Test
    fun downloadedDropAndLiteralReplacementRulesAreSafe() {
        val drop = RedactionEngine {
            RedactionPolicy(patternRules = listOf(RedactionPatternRule("secret", "SECRET", "<DROPPED>", "text", "DROP")))
        }
        val literal = RedactionEngine {
            RedactionPolicy(patternRules = listOf(RedactionPatternRule("price", "PRICE", "<PRICE_\$>", "text")))
        }

        assertEquals("<DROPPED>", drop.redactText("SECRET value", RedactionSummary()))
        assertEquals("<PRICE_\$>", literal.redactText("PRICE", RedactionSummary()))
    }

    @Test
    fun ordinaryUiTextIsNotPreservedByDefault() {
        val summary = RedactionSummary()
        val redacted = engine.redactText("Alice says hello", summary)

        assertEquals("<TEXT_REDACTED>", redacted)
        assertEquals(1, summary.redactedPlainText)
    }

    @Test
    fun allowDefaultPolicyStillRedactsPlainUiTextForUploadSafety() {
        val dynamic = RedactionEngine {
            RedactionPolicy(defaultTextAction = "ALLOW")
        }

        assertEquals("<TEXT_REDACTED>", dynamic.redactText("Alice says hello", RedactionSummary()))
    }
}
