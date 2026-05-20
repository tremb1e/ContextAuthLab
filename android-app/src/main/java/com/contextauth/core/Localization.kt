package com.contextauth.core

import java.util.Locale

object LocaleText {
    fun isChinese(locale: Locale = Locale.getDefault()): Boolean {
        val language = locale.language.lowercase(Locale.US)
        val script = locale.script.lowercase(Locale.US)
        return language == "zh" || script in setOf("hans", "hant")
    }

    fun pick(zh: String, en: String, locale: Locale = Locale.getDefault()): String =
        if (isChinese(locale)) zh else en
}
