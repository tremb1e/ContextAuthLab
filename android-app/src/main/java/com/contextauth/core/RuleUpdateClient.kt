package com.contextauth.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

data class RuleCheckResult(
    val version: String,
    val ruleHash: String,
    val message: String,
    val policy: RedactionPolicy
)

class RuleUpdateClient(private val client: OkHttpClient) {
    suspend fun fetch(serverUrl: String): Result<RuleCheckResult> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(serverUrl.trimEnd('/') + "/api/v1/rules")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: error("empty rules response")
                if (!response.isSuccessful) error("HTTP ${response.code}")
                val parsed = parseRulesResponse(body)
                parsed.copy(
                    message = "HTTP ${response.code}, ${parsed.message}, " +
                        "${parsed.policy.patternRules.size} dynamic rules"
                )
            }
        }
    }

    companion object {
        private val sha256Regex = Regex("^[a-f0-9]{64}$")

        fun parseRulesResponse(body: String): RuleCheckResult {
            val json = JSONObject(body)
            val version = RuleDefaults.usableVersion(json.optString("version", RuleDefaults.BASELINE_VERSION))
            val computedHash = runCatching { canonicalRuleHash(json) }.getOrDefault(RuleDefaults.BASELINE_RULE_HASH)
            val serverHash = json.optString("rule_hash").trim().lowercase()
            val serverHashUsable = serverHash.matches(sha256Regex) && serverHash != RuleDefaults.ZERO_HASH
            val ruleHash = if (serverHashUsable && serverHash == computedHash) {
                serverHash
            } else {
                RuleDefaults.usableRuleHash(computedHash)
            }
            val hashNote = when {
                serverHash.isBlank() -> "missing hash ignored"
                !serverHashUsable -> "invalid hash ignored"
                serverHash != computedHash -> "hash mismatch ignored"
                else -> "hash verified"
            }
            val policy = RedactionPolicy(
                version = version,
                ruleHash = ruleHash,
                maxTextLength = json.optInt("max_text_length", 128).coerceIn(1, 4096),
                defaultTextAction = safeDefaultTextAction(json.optString("default_text_action", "REDACT")),
                patternRules = json.optJSONArray("rules").toPatternRules()
            )
            return RuleCheckResult(version, ruleHash, "rules version $version, $hashNote", policy)
        }

        fun canonicalRuleHash(json: JSONObject): String {
            val copy = JSONObject(json.toString())
            copy.remove("rule_hash")
            val canonical = canonicalJson(copy)
            val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }

        private fun canonicalJson(value: Any?): String = when (value) {
            null, JSONObject.NULL -> "null"
            is JSONObject -> value.keys().asSequence().toList().sorted().joinToString(prefix = "{", postfix = "}") { key ->
                JSONObject.quote(key) + ":" + canonicalJson(value.get(key))
            }
            is JSONArray -> (0 until value.length()).joinToString(prefix = "[", postfix = "]") { index ->
                canonicalJson(value.get(index))
            }
            is String -> JSONObject.quote(value)
            is Number, is Boolean -> value.toString()
            else -> JSONObject.quote(value.toString())
        }

        private fun JSONArray?.toPatternRules(): List<RedactionPatternRule> {
            if (this == null) return emptyList()
            return buildList {
                for (index in 0 until length()) {
                    val obj = optJSONObject(index) ?: continue
                    val pattern = obj.optString("pattern").trim()
                    if (pattern.isBlank()) continue
                    val action = normalizeRuleAction(obj.optString("action", "REDACT"))
                    if (action == "ALLOW") continue
                    val target = normalizeTarget(obj.optString("target", "text")) ?: continue
                    val replacement = when (action) {
                        "DROP" -> "<DROPPED>"
                        else -> obj.optString("replacement", "<REDACTED>")
                    }
                    val name = obj.optString("name")
                        .ifBlank { obj.optString("id") }
                        .ifBlank { "rule_$index" }
                    val rule = RedactionPatternRule(
                        name = name.replace(Regex("""[^\w.-]"""), "_").take(48),
                        pattern = pattern,
                        replacement = replacement,
                        target = target,
                        action = action
                    )
                    if (rule.regex != null) add(rule)
                }
            }
        }

        private fun safeDefaultTextAction(action: String): String =
            if (action.trim().uppercase() == "DROP") "DROP" else "REDACT"

        private fun normalizeRuleAction(action: String): String = when (action.trim().uppercase()) {
            "DROP" -> "DROP"
            "ALLOW" -> "ALLOW"
            else -> "REDACT"
        }

        private fun normalizeTarget(target: String): String? = when (target.trim().lowercase()) {
            "content_description", "contentdescription", "content_desc", "content-description" -> "content_description"
            "node" -> "node"
            "package", "package_name", "packagename" -> null
            else -> "text"
        }
    }
}
