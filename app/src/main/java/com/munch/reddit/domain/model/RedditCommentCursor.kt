package com.munch.reddit.domain.model

import kotlin.collections.ArrayDeque

/**
 * Tracks pending Reddit comment expansion work for a given post.
 *
 * Reddit returns "more" placeholders that must be expanded via `/api/morechildren`, as well
 * as "continue this thread" sentinels that require the focused comment endpoint. This cursor
 * keeps both queues so the caller can keep fetching until no placeholder remains. The [linkId]
 * stores the parent post identifier (`t3_xxxx`) while [permalinkPath] (without `.json`) allows
 * callers to reconstruct endpoint paths when expanding focused comment threads.
 */
data class RedditCommentCursor(
    val linkId: String,
    private val initialMoreChildren: List<String>,
    private val initialContinueThreadTargets: List<String> = emptyList(),
    val permalinkPath: String? = null,
    private val initialPlaceholderParents: Map<String, String> = emptyMap(),
    private val initialForcedContinueParent: String? = null
) {

    /**
     * Queue of regular "more" comment ids that must be hydrated via `/api/morechildren`.
     */
    private val moreQueue: ArrayDeque<String> = ArrayDeque(initialMoreChildren.distinct())

    /**
     * Queue of comment ids that need the focused comment endpoint to follow a
     * “continue this thread” branch.
     */
    private val continueThreadQueue: ArrayDeque<String> = ArrayDeque(initialContinueThreadTargets.distinct())

    private val placeholderParentMap: MutableMap<String, String> = initialPlaceholderParents.toMutableMap()
    private var forcedContinueParent: String? = initialForcedContinueParent

    /**
     * Returns `true` when there is still pending work in either queue.
     */
    fun hasWork(): Boolean = moreQueue.isNotEmpty() || continueThreadQueue.isNotEmpty()

    /**
     * Removes and returns up to [max] ids (capped at Reddit's limit of 100) for a single
     * `/api/morechildren` request.
     */
    fun takeMoreBatch(max: Int = 100): List<String> {
        if (moreQueue.isEmpty() || max <= 0) return emptyList()
        val limit = minOf(max, 100, moreQueue.size)
        return List(limit) { moreQueue.removeFirst() }
    }

    /**
     * Enqueues additional ids discovered in subsequent "more" placeholders.
     */
    fun enqueueMore(ids: Collection<String>, parentMap: Map<String, String> = emptyMap()) {
        if (ids.isEmpty()) return
        ids.forEach { id ->
            val normalized = id.trim()
            if (normalized.isNotEmpty() && !moreQueue.contains(normalized)) {
                moreQueue.addLast(normalized)
            }
        }
        if (parentMap.isNotEmpty()) {
            parentMap.forEach { (childId, parentId) ->
                val normalizedChild = childId.trim().removePrefix("t1_").removePrefix("t3_")
                val normalizedParent = parentId.trim().removePrefix("t1_").removePrefix("t3_")
                if (normalizedChild.isNotEmpty() && normalizedParent.isNotEmpty()) {
                    placeholderParentMap[normalizedChild] = normalizedParent
                }
            }
        }
    }

    /**
     * Enqueues the comment id that should be expanded via the focused comment endpoint.
     */
    fun enqueueContinueThread(parentCommentId: String) {
        val normalized = parentCommentId.trim()
        if (normalized.isNotEmpty() && !continueThreadQueue.contains(normalized)) {
            continueThreadQueue.addLast(normalized)
        }
    }

    /**
     * Retrieves the next comment id that must be hydrated via the focused comment endpoint,
     * or `null` if the queue is empty.
     */
    fun pollContinueThreadParent(): String? =
        if (continueThreadQueue.isEmpty()) null else continueThreadQueue.removeFirst()

    /**
     * Snapshot of pending ids waiting on `/api/morechildren`.
     */
    fun remainingMoreIds(): List<String> = moreQueue.toList()

    /**
     * Snapshot of pending ids that still require the focused comment endpoint.
     */
    fun remainingContinueThreadParentIds(): List<String> = continueThreadQueue.toList()

    /**
     * Approximate count of outstanding placeholders (regular + continue thread).
     */
    fun totalPendingWorkCount(): Int = moreQueue.size + continueThreadQueue.size

    /**
     * Removes placeholder-to-parent mappings for the provided ids and returns how many
     * placeholders were associated with each parent comment.
     */
    fun consumePlaceholderParents(placeholderIds: Collection<String>): Map<String, Int> {
        if (placeholderIds.isEmpty()) return emptyMap()
        val counts = mutableMapOf<String, Int>()
        for (placeholderId in placeholderIds) {
            val normalized = placeholderId.trim().removePrefix("t1_").removePrefix("t3_")
            val parentId = placeholderParentMap.remove(placeholderId)
                ?: placeholderParentMap.remove(normalized)
                ?: continue
            counts[parentId] = counts.getOrDefault(parentId, 0) + 1
        }
        return counts
    }

    fun markForceContinueParent(parentId: String) {
        val normalized = parentId.trim().removePrefix("t1_").removePrefix("t3_")
        if (normalized.isNotEmpty()) {
            forcedContinueParent = normalized
            val exists = continueThreadQueue.any { id ->
                id.trim().removePrefix("t1_").removePrefix("t3_") == normalized
            }
            if (!exists) {
                continueThreadQueue.addLast(normalized)
            }
        }
    }

    fun consumeForcedContinueParent(): String? {
        val target = forcedContinueParent ?: return null
        forcedContinueParent = null
        val size = continueThreadQueue.size
        var removed: String? = null
        repeat(size) {
            val id = continueThreadQueue.removeFirst()
            val normalized = id.trim().removePrefix("t1_").removePrefix("t3_")
            if (removed == null && normalized == target) {
                removed = id
            } else {
                continueThreadQueue.addLast(id)
            }
        }
        return removed
    }

    /**
     * Returns true when the continue-thread queue contains the specified parent comment id.
     */
    fun hasContinueThreadForParent(parentId: String?): Boolean {
        if (parentId == null) return false
        val normalized = parentId.trim().removePrefix("t1_").removePrefix("t3_")
        if (normalized.isEmpty()) return false
        if (forcedContinueParent == normalized) return true
        return continueThreadQueue.any { id ->
            val candidate = id.trim().removePrefix("t1_").removePrefix("t3_")
            candidate == normalized
        }
    }

    fun promotePlaceholdersForParent(parentId: String): Boolean {
        if (moreQueue.isEmpty()) return false
        val normalizedParent = parentId.trim().removePrefix("t1_").removePrefix("t3_")
        if (normalizedParent.isEmpty()) return false
        val size = moreQueue.size
        val matches = ArrayDeque<String>()
        val remainder = ArrayDeque<String>()
        repeat(size) {
            val id = moreQueue.removeFirst()
            val normalizedChild = id.trim().removePrefix("t1_").removePrefix("t3_")
            val mappedParent = placeholderParentMap[normalizedChild]
            if (mappedParent == normalizedParent) {
                matches.addLast(id)
            } else {
                remainder.addLast(id)
            }
        }
        if (matches.isEmpty()) {
            while (remainder.isNotEmpty()) {
                moreQueue.addLast(remainder.removeFirst())
            }
            return false
        }
        while (matches.isNotEmpty()) {
            moreQueue.addLast(matches.removeFirst())
        }
        while (remainder.isNotEmpty()) {
            moreQueue.addLast(remainder.removeFirst())
        }
        return true
    }

    fun promoteContinueThreadParent(parentId: String): Boolean {
        if (continueThreadQueue.isEmpty()) return false
        val normalizedParent = parentId.trim().removePrefix("t1_").removePrefix("t3_")
        if (normalizedParent.isEmpty()) return false
        val size = continueThreadQueue.size
        var targetId: String? = null
        val remainder = ArrayDeque<String>()
        repeat(size) {
            val id = continueThreadQueue.removeFirst()
            val normalized = id.trim().removePrefix("t1_").removePrefix("t3_")
            if (targetId == null && normalized == normalizedParent) {
                targetId = id
            } else {
                remainder.addLast(id)
            }
        }
        if (targetId == null) {
            while (remainder.isNotEmpty()) {
                continueThreadQueue.addLast(remainder.removeFirst())
            }
            return false
        }
        continueThreadQueue.addFirst(targetId!!)
        while (remainder.isNotEmpty()) {
            continueThreadQueue.addLast(remainder.removeFirst())
        }
        return true
    }

    /**
     * Builds a new cursor representing the remaining work as of now.
     */
    fun snapshot(): RedditCommentCursor = RedditCommentCursor(
        linkId = linkId,
        initialMoreChildren = remainingMoreIds(),
        initialContinueThreadTargets = remainingContinueThreadParentIds(),
        permalinkPath = permalinkPath,
        initialPlaceholderParents = placeholderParentMap.toMap(),
        initialForcedContinueParent = forcedContinueParent
    )
}
