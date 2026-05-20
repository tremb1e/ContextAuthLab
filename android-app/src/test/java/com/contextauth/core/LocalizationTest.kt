package com.contextauth.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class LocalizationTest {
    @Test
    fun choosesLanguageFromLocale() {
        assertTrue(LocaleText.isChinese(Locale.SIMPLIFIED_CHINESE))
        assertFalse(LocaleText.isChinese(Locale.US))
        assertEquals("中文", LocaleText.pick("中文", "English", Locale.CHINA))
        assertEquals("English", LocaleText.pick("中文", "English", Locale.US))
        assertEquals("English", LocaleText.pick("中文", "English", Locale("en", "CN")))
    }

    @Test
    fun taskCategoryHasEnglishUiCopy() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            assertEquals("Simulated phone settings", TaskCategory.C4.localizedTaskName())
            Locale.setDefault(Locale.CHINA)
            assertEquals("模拟手机设置", TaskCategory.C4.localizedTaskName())
        } finally {
            Locale.setDefault(previous)
        }
    }
}
