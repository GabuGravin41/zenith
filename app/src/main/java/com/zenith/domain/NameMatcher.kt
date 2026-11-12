package com.zenith.domain

import kotlin.math.abs
import kotlin.math.min

/**
 * High-performance algorithmic name deduplication — pure offline, zero AI.
 * Optimized for mobile devices:
 *  - O(N) exact-match grouping first
 *  - Token-inverted index so we only compare candidate pairs that share words
 *  - Zero-heap-allocation Levenshtein distance with early-exit bounds
 */
object NameMatcher {

    private val NOISE = setOf(
        "mr", "mrs", "ms", "dr", "prof", "eng", "rev",
        "limited", "ltd", "co", "company", "kenya", "nairobi",
        "account", "acct", "bank", "savings", "loan"
    )

    fun normalize(raw: String): String {
        return raw.trim()
            .lowercase()
            .replace(Regex("[^a-z0-9 ]"), "")
            .split(" ")
            .filter { it.isNotBlank() && it !in NOISE }
            .joinToString(" ")
    }

    fun titleCase(normalized: String): String =
        normalized.split(" ").joinToString(" ") { w ->
            w.replaceFirstChar { it.uppercase() }
        }

    /**
     * Groups a list of raw names into clusters of the same person.
     * Fast 2-phase approach:
     * Phase 1: Hash-map grouping on normalized string (O(N))
     * Phase 2: Token-candidate pairing on unique distinct names only
     */
    fun groupByCanonicalName(rawNames: List<String>): Map<String, List<String>> {
        if (rawNames.isEmpty()) return emptyMap()

        // Phase 1: Group exact normalized matches in O(N)
        val normalizedMap = mutableMapOf<String, MutableList<String>>()
        for (raw in rawNames) {
            val norm = normalize(raw)
            if (norm.isNotBlank()) {
                normalizedMap.getOrPut(norm) { mutableListOf() }.add(raw)
            }
        }

        val distinctNorms = normalizedMap.keys.toList()
        if (distinctNorms.size <= 1) {
            return normalizedMap.mapKeys { titleCase(it.key) }
        }

        // Phase 2: Inverted index by token to only compare candidates that share a word or prefix
        val parent = distinctNorms.indices.toMutableList()

        fun find(x: Int): Int {
            var r = x
            while (parent[r] != r) r = parent[r]
            var n = x
            while (parent[n] != r) { val next = parent[n]; parent[n] = r; n = next }
            return r
        }

        fun union(x: Int, y: Int) { parent[find(x)] = find(y) }

        // Token inverted index: token -> list of distinctNorm indices
        val tokenIndex = mutableMapOf<String, MutableList<Int>>()
        val firstCharIndex = mutableMapOf<Char, MutableList<Int>>()

        distinctNorms.forEachIndexed { idx, norm ->
            val tokens = norm.split(" ").filter { it.length >= 3 }
            for (t in tokens) {
                tokenIndex.getOrPut(t) { mutableListOf() }.add(idx)
            }
            if (norm.isNotEmpty()) {
                firstCharIndex.getOrPut(norm[0]) { mutableListOf() }.add(idx)
            }
        }

        val checkedPairs = mutableSetOf<Long>()

        fun testAndUnion(i: Int, j: Int) {
            if (i == j) return
            val pairKey = if (i < j) (i.toLong() shl 32) or (j.toLong() and 0xFFFFFFFFL)
                          else (j.toLong() shl 32) or (i.toLong() and 0xFFFFFFFFL)
            if (!checkedPairs.add(pairKey)) return

            if (areSamePersonFast(distinctNorms[i], distinctNorms[j])) {
                union(i, j)
            }
        }

        // Only compare pairs that share at least one word
        for (indices in tokenIndex.values) {
            if (indices.size > 1) {
                for (a in indices.indices) {
                    for (b in a + 1 until indices.size) {
                        testAndUnion(indices[a], indices[b])
                    }
                }
            }
        }

        // For single-word names, only compare with same first character and length diff <= 2
        for (indices in firstCharIndex.values) {
            if (indices.size > 1) {
                for (a in indices.indices) {
                    val idxA = indices[a]
                    val lenA = distinctNorms[idxA].length
                    for (b in a + 1 until indices.size) {
                        val idxB = indices[b]
                        if (abs(lenA - distinctNorms[idxB].length) <= 2) {
                            testAndUnion(idxA, idxB)
                        }
                    }
                }
            }
        }

        // Phase 3: Assemble clusters
        val clusters = mutableMapOf<Int, MutableList<String>>()
        for (i in distinctNorms.indices) {
            val root = find(i)
            val raws = normalizedMap[distinctNorms[i]] ?: emptyList()
            clusters.getOrPut(root) { mutableListOf() }.addAll(raws)
        }

        return clusters.values.associate { group ->
            val canonical = group.maxByOrNull { normalize(it).length }
                ?.let { titleCase(normalize(it)) } ?: group.first()
            canonical to group
        }
    }

    private fun areSamePersonFast(a: String, b: String): Boolean {
        if (a == b) return true

        val tokensA = a.split(" ").filter { it.isNotBlank() }
        val tokensB = b.split(" ").filter { it.isNotBlank() }

        // Token subset: e.g. "john" in "john kamau"
        val setA = tokensA.toSet()
        val setB = tokensB.toSet()
        if (setA.isNotEmpty() && setB.isNotEmpty()) {
            if (setA.containsAll(setB) || setB.containsAll(setA)) return true
        }

        // Levenshtein: only for similar lengths
        val lenDiff = abs(a.length - b.length)
        val maxDist = if (min(a.length, b.length) <= 12) 2 else 3
        if (lenDiff <= maxDist) {
            return fastLevenshtein(a, b, maxDist) <= maxDist
        }

        return false
    }

    /**
     * Two-row Levenshtein distance with early exit threshold.
     * Allocates zero 2D arrays on heap.
     */
    private fun fastLevenshtein(a: String, b: String, maxAllowed: Int): Int {
        if (a == b) return 0
        if (abs(a.length - b.length) > maxAllowed) return maxAllowed + 1

        var prev = IntArray(b.length + 1) { it }
        var curr = IntArray(b.length + 1)

        for (i in 1..a.length) {
            curr[0] = i
            var minInRow = curr[0]

            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(
                    curr[j - 1] + 1,      // insertion
                    prev[j] + 1,          // deletion
                    prev[j - 1] + cost    // substitution
                )
                minInRow = min(minInRow, curr[j])
            }

            // Early exit if entire row exceeds threshold
            if (minInRow > maxAllowed) return maxAllowed + 1

            val temp = prev
            prev = curr
            curr = temp
        }

        return prev[b.length]
    }
}
