package com.contextauth.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextFeatureExtractorTest {
    @Test
    fun extractsCountsScoresAndTypingCategory() {
        val event = ContextEventSnapshot(
            eventType = "TYPE_VIEW_TEXT_CHANGED",
            eventTimeWallMillis = 1_000,
            packageNameHash = "a".repeat(64),
            windowTitleRedacted = "<DROPPED>",
            rootNodes = listOf(
                NodeSnapshot("1", "a".repeat(64), "android.widget.EditText", null, "<EDITABLE_TEXT_DROPPED>", null, true, true, false, false, 0, 0),
                NodeSnapshot("2", "a".repeat(64), "android.widget.Button", null, "<DROPPED>", null, true, false, false, false, 0, 0),
                NodeSnapshot("3", "a".repeat(64), "androidx.recyclerview.widget.RecyclerView", null, null, null, false, false, true, false, 0, 0),
            ),
            redactionSummary = emptyMap()
        )

        val feature = ContextFeatureExtractor().extract(event, CollectionSource.BUILTIN_TASK, TaskCategory.C3, "session")
        assertEquals(1, feature.editableCount)
        assertEquals(1, feature.scrollableCount)
        assertEquals(2, feature.clickableCount)
        assertEquals("C3", feature.estimatedContextCategory)
        assertTrue(feature.listLikeScore > 0.5)
        assertEquals("session", feature.taskSessionId)
    }
}
