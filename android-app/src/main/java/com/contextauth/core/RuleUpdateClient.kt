package com.contextauth.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

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
                parsed.copy(message = "HTTP ${response.code}, rules version ${parsed.version}, ${parsed.policy.patternRules.size} dynamic rules")
            }
        }
    }

    companion object {
        fun parseRulesResponse(body: String): RuleCheckResult {
            val json = JSONObject(body)
            val version = json.getString("version")
            val ruleHash = json.getString("rule_hash")
            require(ruleHash.matches(Regex("^[a-f0-9]{64}$"))) { "invalid rule_hash" }
            val policy = RedactionPolicy(
                version = version,
                ruleHash = ruleHash,
                packageBlocklist = json.optJSONArray("package_blocklist").toStringList(),
                maxTextLength = json.optInt("max_text_length", 128).coerceIn(1, 4096),
                patternRules = json.optJSONArray("rules").toPatternRules()
            )
            return RuleCheckResult(version, ruleHash, "rules version $version", policy)
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
                            replacement = replacement
                        )
                    )
                }
            }
        }
    }
}
