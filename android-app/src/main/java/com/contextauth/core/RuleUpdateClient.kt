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
                    message = "HTTP ${response.code}, rules version ${parsed.version}, " +
                        "${parsed.policy.patternRules.size} dynamic rules, " +
                        "${parsed.policy.packageBlocklist.size} package skips"
                )
            }
        }
    }

    companion object {
        fun parseRulesResponse(body: String): RuleCheckResult {
            val json = JSONObject(body)
            val version = json.getString("version")
            val ruleHash = json.getString("rule_hash")
            require(ruleHash.matches(Regex("^[a-f0-9]{64}$"))) { "invalid rule_hash" }
            require(canonicalRuleHash(json) == ruleHash) { "rule_hash_mismatch" }
            val policy = RedactionPolicy(
                version = version,
                ruleHash = ruleHash,
                packageBlocklist = json.optJSONArray("package_blocklist").toStringList(),
                maxTextLength = json.optInt("max_text_length", 128).coerceIn(1, 4096),
                defaultTextAction = json.optString("default_text_action", "REDACT").ifBlank { "REDACT" },
                patternRules = json.optJSONArray("rules").toPatternRules()
            )
            return RuleCheckResult(version, ruleHash, "rules version $version", policy)
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

        private fun JSONArray?.toStringList(): List<String> {
            if (this == null) return emptyList()
            return buildList {
                for (index in 0 until length()) {
                    val value = optString(index).trim()
                    if (value.isNotBlank()) add(value)
                }
            }
        }

        private fun JSONArray?.toPatternRules(): List<RedactionPatternRule> {
            if (this == null) return emptyList()
            return buildList {
                for (index in 0 until length()) {
                    val obj = optJSONObject(index) ?: continue
                    val pattern = obj.optString("pattern").trim()
                    if (pattern.isBlank()) continue
                    val action = obj.optString("action", "REDACT").uppercase()
                    if (action == "ALLOW") continue
                    val target = obj.optString("target", "text").ifBlank { "text" }
                    val replacement = when (action) {
                        "DROP" -> "<DROPPED>"
                        else -> obj.optString("replacement", "<REDACTED>")
                    }
                    val name = obj.optString("name")
                        .ifBlank { obj.optString("id") }
                        .ifBlank { "rule_$index" }
                    add(
                        RedactionPatternRule(
                            name = name.replace(Regex("""[^\w.-]"""), "_").take(48),
                            pattern = pattern,
                            replacement = replacement,
                            target = target
                        )
                    )
                }
            }
        }
    }
}
