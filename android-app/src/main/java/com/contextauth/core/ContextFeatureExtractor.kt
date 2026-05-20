package com.contextauth.core

class ContextFeatureExtractor {
    fun extract(
        event: ContextEventSnapshot,
        source: CollectionSource,
        taskCategory: TaskCategory?,
        taskSessionId: String?
    ): ContextFeature {
        val nodes = event.rootNodes
        val editable = nodes.count { it.editable }
        val scrollable = nodes.count { it.scrollable }
        val clickable = nodes.count { it.clickable }
        val histogram = nodes
            .mapNotNull { it.className?.substringAfterLast('.') }
            .groupingBy { it }
            .eachCount()
        val mediaScore = if (histogram.keys.any { it.contains("Surface", ignoreCase = true) || it.contains("Player", ignoreCase = true) }) 0.8 else 0.0
        val listScore = if (scrollable > 0) 0.8 else 0.1
        val formScore = when {
            editable >= 2 -> 0.8
            editable == 1 && clickable > 2 -> 0.6
            else -> 0.1
        }
        val gameScore = if (mediaScore > 0.5 && clickable <= 1) 0.4 else 0.1
        val estimated = when {
            taskCategory != null -> taskCategory.name
            editable >= 2 -> "C4"
            event.eventType == "TYPE_VIEW_TEXT_CHANGED" || editable == 1 -> "C3"
            event.eventType == "TYPE_VIEW_SCROLLED" || scrollable > 0 -> "C2"
            mediaScore > 0.5 -> "C5"
            clickable > 4 -> "C4"
            else -> taskCategory?.name ?: "UNKNOWN"
        }
        return ContextFeature(
            eventId = event.eventId,
            computedAtWallMillis = System.currentTimeMillis(),
            collectionSource = source,
            taskCategory = taskCategory,
            taskSessionId = taskSessionId,
            editableCount = editable,
            scrollableCount = scrollable,
            clickableCount = clickable,
            mediaLikeScore = mediaScore,
            listLikeScore = listScore,
            formLikeScore = formScore,
            gameLikeScore = gameScore,
            nodeClassHistogram = histogram,
            eventType = event.eventType,
            coarseOrientation = "portrait",
            estimatedContextCategory = estimated
        )
    }
}
