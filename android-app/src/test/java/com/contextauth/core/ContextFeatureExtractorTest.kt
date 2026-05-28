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
            appPackageName = "com.example.front",
            foregroundActivityClassName = "com.example.front.MainActivity",
            foregroundComponentName = "com.example.front/.MainActivity",
            inputMethodVisible = true,
            windowTitleRedacted = "<DROPPED>",
            rootNodes = listOf(
                NodeSnapshot("1", "android.widget.EditText", null, null, "<EDITABLE_TEXT_DROPPED>", null, true, true, false, false, 0, 0),
                NodeSnapshot("2", "android.widget.Button", "com.example:id/ok", "确认", null, null, true, false, false, false, 0, 0),
                NodeSnapshot("3", "androidx.recyclerview.widget.RecyclerView", null, null, null, null, false, false, true, false, 0, 0),
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

    @Test
    fun preservesPasswordNodeSeenForUpstreamRedactionFailures() {
        val event = ContextEventSnapshot(
            eventType = "TYPE_WINDOW_CONTENT_CHANGED",
            eventTimeWallMillis = 1_000,
            appPackageName = "com.example.front",
            foregroundActivityClassName = null,
            foregroundComponentName = null,
            inputMethodVisible = false,
            windowTitleRedacted = null,
            rootNodes = listOf(
                NodeSnapshot("1", "android.widget.EditText", null, null, null, null, false, true, false, true, 0, 0)
            ),
            redactionSummary = emptyMap()
        )

        val feature = ContextFeatureExtractor().extract(event, CollectionSource.THIRD_PARTY_APP, null, null)

        assertTrue(feature.passwordNodeSeen)
    }

    @Test
    fun mediaLikeThirdPartyUiEstimatesVideoCategory() {
        val event = ContextEventSnapshot(
            eventType = "TYPE_WINDOW_CONTENT_CHANGED",
            eventTimeWallMillis = 1_000,
            appPackageName = "com.example.video",
            foregroundActivityClassName = "com.example.video.PlayerActivity",
            foregroundComponentName = "com.example.video/.PlayerActivity",
            inputMethodVisible = false,
            coarseOrientation = CoarseOrientation.LANDSCAPE,
            windowTitleRedacted = "<TEXT_REDACTED>",
            rootNodes = listOf(
                NodeSnapshot("1", "android.view.SurfaceView", null, null, null, null, false, false, false, false, 0, 0)
            ),
            redactionSummary = emptyMap()
        )

        val feature = ContextFeatureExtractor().extract(event, CollectionSource.THIRD_PARTY_APP, null, null)

        assertEquals("C6", feature.estimatedContextCategory)
        assertEquals(CoarseOrientation.LANDSCAPE, feature.coarseOrientation)
        assertTrue(feature.mediaLikeScore > 0.5)
    }
}
