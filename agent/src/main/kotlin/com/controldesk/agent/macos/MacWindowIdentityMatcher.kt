package com.controldesk.agent.macos

/** Accessibility geometry/title for one window reported by System Events. */
internal data class AccessibleWindowSnapshot(
    val pid: Int,
    val title: String,
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
)

/** Quartz identity and geometry for one window in the current macOS session. */
internal data class QuartzWindowSnapshot(
    val pid: Int,
    val id: String,
    val title: String,
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
)

/**
 * Joins Accessibility windows to Quartz windows without relying on the mutable
 * order of either API. Exact titles win; geometry disambiguates duplicate titles.
 */
internal object MacWindowIdentityMatcher {
    fun match(
        accessible: List<AccessibleWindowSnapshot>,
        quartz: List<QuartzWindowSnapshot>,
    ): List<String?> {
        val result = MutableList<String?>(accessible.size) { null }
        val remainingAccessible = accessible.indices.toMutableSet()
        val remainingQuartz = quartz.indices.toMutableSet()

        // A unique exact title is stronger than geometry and stays valid when a
        // window moves between the two API snapshots.
        val exactTitlePairs = remainingAccessible.flatMap { axIndex ->
            val ax = accessible[axIndex]
            if (ax.title.isBlank()) return@flatMap emptyList()
            val exactQuartz = remainingQuartz.filter { qIndex ->
                quartz[qIndex].pid == ax.pid && quartz[qIndex].title == ax.title
            }
            if (exactQuartz.size != 1) return@flatMap emptyList()
            val qIndex = exactQuartz.single()
            val exactAccessible = remainingAccessible.filter { otherIndex ->
                accessible[otherIndex].pid == ax.pid && accessible[otherIndex].title == ax.title
            }
            if (exactAccessible.size == 1) listOf(axIndex to qIndex) else emptyList()
        }
        exactTitlePairs.forEach { (axIndex, qIndex) ->
            result[axIndex] = quartz[qIndex].id
            remainingAccessible -= axIndex
            remainingQuartz -= qIndex
        }

        // For titleless or renamed windows, accept only mutual, unique nearest
        // exact geometry matches. Ambiguous overlapping windows intentionally stay
        // unidentified instead of inheriting a sibling window's layout.
        while (true) {
            val nearestQuartzByAccessible = remainingAccessible.associateWith { axIndex ->
                nearestUnique(remainingQuartz) { qIndex ->
                    if (quartz[qIndex].pid != accessible[axIndex].pid) null
                    else geometryDistance(accessible[axIndex], quartz[qIndex])
                }
            }
            val nearestAccessibleByQuartz = remainingQuartz.associateWith { qIndex ->
                nearestUnique(remainingAccessible) { axIndex ->
                    if (accessible[axIndex].pid != quartz[qIndex].pid) null
                    else geometryDistance(accessible[axIndex], quartz[qIndex])
                }
            }
            val pairs = nearestQuartzByAccessible.mapNotNull { (axIndex, qIndex) ->
                qIndex?.takeIf { nearestAccessibleByQuartz[it] == axIndex }?.let { axIndex to it }
            }
            if (pairs.isEmpty()) break
            pairs.forEach { (axIndex, qIndex) ->
                result[axIndex] = quartz[qIndex].id
                remainingAccessible -= axIndex
                remainingQuartz -= qIndex
            }
        }
        return result
    }

    private fun <T> nearestUnique(candidates: Set<T>, score: (T) -> Double?): T? {
        val scored = candidates.mapNotNull { candidate -> score(candidate)?.let { candidate to it } }
        val bestScore = scored.minOfOrNull { it.second } ?: return null
        return scored.filter { it.second == bestScore }.singleOrNull()?.first
    }

    private fun geometryDistance(accessible: AccessibleWindowSnapshot, quartz: QuartzWindowSnapshot): Double? =
        (
        kotlin.math.abs(accessible.x - quartz.x) +
            kotlin.math.abs(accessible.y - quartz.y) +
            kotlin.math.abs(accessible.width - quartz.width) +
            kotlin.math.abs(accessible.height - quartz.height)
        ).takeIf { it == 0.0 }

}
