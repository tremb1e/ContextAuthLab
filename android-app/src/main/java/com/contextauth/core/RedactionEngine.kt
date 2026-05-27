package com.contextauth.core

import java.util.concurrent.atomic.AtomicReference

data class RawNodeSnapshot(
    val nodeId: String,
    val packageName: String?,
    val className: String?,
    val viewId: String?,
    val text: CharSequence?,
    val contentDescription: CharSequence?,
    val clickable: Boolean,
    val editable: Boolean,
    val scrollable: Boolean,
    val checkable: Boolean,
    val checked: Boolean,
    val enabled: Boolean,
    val focused: Boolean,
    val selected: Boolean,
    val visibleToUser: Boolean,
    val longClickable: Boolean,
    val password: Boolean,
    val childCount: Int,
    val depth: Int,
    val boundsGrid: Map<String, Int>,
    val actionsSummary: List<String>
)

data class RedactionSummary(
    var droppedPasswordNodes: Int = 0,
    var droppedEditableTexts: Int = 0,
    var redactedPlainText: Int = 0,
    var replacedEmail: Int = 0,
    var replacedPhone: Int = 0,
    var replacedUrl: Int = 0,
    var replacedNumber: Int = 0,
    var replacedCard: Int = 0,
    var replacedIdNumber: Int = 0,
    var replacedToken: Int = 0,
    val dynamicRuleHits: MutableMap<String, Int> = linkedMapOf()
) {
    fun asMap(): Map<String, Int> = mapOf(
        "dropped_password_nodes" to droppedPasswordNodes,
        "dropped_editable_texts" to droppedEditableTexts,
        "redacted_plain_text" to redactedPlainText,
        "replaced_email" to replacedEmail,
        "replaced_phone" to replacedPhone,
        "replaced_url" to replacedUrl,
        "replaced_number" to replacedNumber,
        "replaced_card" to replacedCard,
        "replaced_id_number" to replacedIdNumber,
        "replaced_token" to replacedToken
    ) + dynamicRuleHits.mapKeys { "dynamic_${it.key}" }
}

data class RedactionPatternRule(
    val name: String,
    val pattern: String,
    val replacement: String,
    val target: String = "text",
    val action: String = "REDACT"
) {
    val regex: Regex? = runCatching { Regex(pattern) }.getOrNull()
}

data class RedactionPolicy(
    val version: String = RuleDefaults.BASELINE_VERSION,
    val ruleHash: String = RuleDefaults.BASELINE_RULE_HASH,
    val maxTextLength: Int = 128,
    val defaultTextAction: String = "REDACT",
    val patternRules: List<RedactionPatternRule> = emptyList()
)

object RedactionPolicyStore {
    private val policyRef = AtomicReference(RedactionPolicy())

    val policy: RedactionPolicy
        get() = policyRef.get()

    fun update(policy: RedactionPolicy) {
        policyRef.set(policy)
    }
}

class RedactionEngine(
    private val policyProvider: () -> RedactionPolicy = { RedactionPolicyStore.policy }
) {
    private val email = Regex("""\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b""", RegexOption.IGNORE_CASE)
    private val phone = Regex("""(?<!\d)(?:\+?86[-\s]?)?1[3-9]\d{9}(?!\d)""")
    private val url = Regex("""\b(?:https?://|www\.)[^\s<>"']+""", RegexOption.IGNORE_CASE)
    private val card = Regex("""(?<![\w-])(?:\d[ -]?){13,19}(?![\w-])""")
    private val idNum = Regex("""(?<![\w-])(?:\d{15}|\d{17}[\dXx])(?![\w-])""")
    private val longNumber = Regex("""(?<!\d)\d{4,}(?!\d)""")
    private val token = Regex("""\b(?:[A-Fa-f0-9]{24,}|[A-Za-z0-9+/=_-]{32,})\b""")
    private val placeholder = Regex("""<[^<>\s]{2,64}>""")

    fun sanitizeNode(raw: RawNodeSnapshot, summary: RedactionSummary): NodeSnapshot? {
        if (raw.password) {
            summary.droppedPasswordNodes += 1
            return null
        }
        val textRedacted = if (raw.editable) {
            summary.droppedEditableTexts += 1
            "<EDITABLE_TEXT_DROPPED>"
        } else {
            null
        }
        val visibleText = if (raw.editable) null else visibleTextForTarget(raw.text?.toString(), summary, "text")
        return NodeSnapshot(
            nodeId = raw.nodeId,
            className = raw.className,
            viewIdResourceName = raw.viewId,
            text = visibleText,
            textRedacted = textRedacted,
            contentDescRedacted = redactTextForTarget(raw.contentDescription?.toString(), summary, "content_description"),
            clickable = raw.clickable,
            editable = raw.editable,
            scrollable = raw.scrollable,
            checkable = raw.checkable,
            checked = raw.checked,
            enabled = raw.enabled,
            focused = raw.focused,
            selected = raw.selected,
            visibleToUser = raw.visibleToUser,
            longClickable = raw.longClickable,
            password = false,
            childCount = raw.childCount,
            depth = raw.depth,
            boundsGrid = raw.boundsGrid,
            actionsSummary = raw.actionsSummary
        )
    }

    fun redactText(value: String?, summary: RedactionSummary = RedactionSummary()): String? {
        return redactTextForTarget(value, summary, "text")
    }

    private fun redactTextForTarget(value: String?, summary: RedactionSummary, fieldTarget: String): String? {
        if (value == null) return null
        if (value.isBlank()) return "<EMPTY>"
        val policy = policyProvider()
        var result: String = value
        policy.patternRules.filter { it.appliesToTextField(fieldTarget) }.forEach { rule ->
            val regex = rule.regex ?: return@forEach
            val replacement = rule.replacement.ifBlank { "<REDACTED>" }
            if (rule.action.uppercase() == "DROP" && regex.containsMatchIn(result)) {
                regex.findAll(result).forEach { _ ->
                    summary.dynamicRuleHits[rule.name] = (summary.dynamicRuleHits[rule.name] ?: 0) + 1
                }
                return "<DROPPED>"
            }
            result = replace(result, regex, replacement) {
                summary.dynamicRuleHits[rule.name] = (summary.dynamicRuleHits[rule.name] ?: 0) + 1
            }
        }
        if (result.length > policy.maxTextLength.coerceAtLeast(1)) return "<LONG_TEXT_DROPPED>"
        result = replace(result, idNum, "<ID_NUM>") { summary.replacedIdNumber += 1 }
        result = replace(result, card, "<CARD>") { summary.replacedCard += 1 }
        result = replace(result, email, "<EMAIL>") { summary.replacedEmail += 1 }
        result = replace(result, phone, "<PHONE>") { summary.replacedPhone += 1 }
        result = replace(result, url, "<URL>") { summary.replacedUrl += 1 }
        result = replace(result, token, "<TOKEN>") { summary.replacedToken += 1 }
        result = replace(result, longNumber, "<NUM>") { summary.replacedNumber += 1 }
        return when (policy.defaultTextAction.uppercase()) {
            "DROP" -> {
                summary.redactedPlainText += 1
                "<DROPPED>"
            }
            else -> redactPlainTextSegments(result, summary)
        }
    }

    private fun visibleTextForTarget(value: String?, summary: RedactionSummary, fieldTarget: String): String? {
        if (value == null) return null
        if (value.isBlank()) return "<EMPTY>"
        val policy = policyProvider()
        var result: String = value
        policy.patternRules.filter { it.appliesToTextField(fieldTarget) }.forEach { rule ->
            val regex = rule.regex ?: return@forEach
            val replacement = rule.replacement.ifBlank { "<REDACTED>" }
            if (rule.action.uppercase() == "DROP" && regex.containsMatchIn(result)) {
                regex.findAll(result).forEach { _ ->
                    summary.dynamicRuleHits[rule.name] = (summary.dynamicRuleHits[rule.name] ?: 0) + 1
                }
                return "<DROPPED>"
            }
            result = replace(result, regex, replacement) {
                summary.dynamicRuleHits[rule.name] = (summary.dynamicRuleHits[rule.name] ?: 0) + 1
            }
        }
        if (result.length > policy.maxTextLength.coerceAtLeast(1)) return "<LONG_TEXT_DROPPED>"
        result = replace(result, idNum, "<ID_NUM>") { summary.replacedIdNumber += 1 }
        result = replace(result, card, "<CARD>") { summary.replacedCard += 1 }
        result = replace(result, email, "<EMAIL>") { summary.replacedEmail += 1 }
        result = replace(result, phone, "<PHONE>") { summary.replacedPhone += 1 }
        result = replace(result, url, "<URL>") { summary.replacedUrl += 1 }
        result = replace(result, token, "<TOKEN>") { summary.replacedToken += 1 }
        result = replace(result, longNumber, "<NUM>") { summary.replacedNumber += 1 }
        return if (policy.defaultTextAction.uppercase() == "DROP") "<DROPPED>" else result
    }

    private fun replace(input: String, regex: Regex, replacement: String, onHit: () -> Unit): String {
        if (!regex.containsMatchIn(input)) return input
        regex.findAll(input).forEach { _ -> onHit() }
        return regex.replace(input) { replacement }
    }

    private fun RedactionPatternRule.appliesToTextField(fieldTarget: String): Boolean {
        val normalized = target.lowercase()
        return normalized == "node" || normalized == fieldTarget
    }

    private fun redactPlainTextSegments(value: String, summary: RedactionSummary): String {
        val tokens = mutableListOf<String>()
        var cursor = 0
        placeholder.findAll(value).forEach { match ->
            val before = value.substring(cursor, match.range.first)
            if (before.isNotBlank()) tokens.add("<TEXT_REDACTED>")
            tokens.add(match.value)
            cursor = match.range.last + 1
        }
        val tail = value.substring(cursor)
        if (tail.isNotBlank()) tokens.add("<TEXT_REDACTED>")
        if (tokens.isEmpty()) {
            summary.redactedPlainText += 1
            return "<TEXT_REDACTED>"
        }
        val compact = tokens.fold(mutableListOf<String>()) { acc, token ->
            if (acc.lastOrNull() != token) acc.add(token)
            acc
        }
        if (compact.any { it == "<TEXT_REDACTED>" }) summary.redactedPlainText += 1
        return compact.joinToString(" ")
    }
}
