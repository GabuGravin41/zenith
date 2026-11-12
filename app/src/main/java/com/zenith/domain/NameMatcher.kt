package com.zenith.domain

/**
 * Pure algorithmic name deduplication — no AI, no network, offline-first.
 *
 * Strategy (in order of priority):
 *  1. Exact match after normalization (trim, lowercase, collapse spaces)
 *  2. Token-overlap: if the shorter name's tokens are all contained in the longer name → same person
 *     e.g. "JOHN KAMAU" and "JOHN K" → tokens {john} ⊆ {john, kamau} ✓
 *  3. Levenshtein edit distance ≤ 2 for names shorter than 10 chars, ≤ 3 for longer
 *     e.g. "Kamau" vs "Kamao" (typo in SMS)
 *
 * Usage: Call [groupByCanonicalName] on a list of raw name strings.
 * Returns a Map<canonicalName, List<originalNames>> where each entry is one real person.
 */
object NameMatcher {

    // Noise words to strip before comparison
    private val NOISE = setOf(
        "mr", "mrs", "ms", "dr", "prof", "eng", "rev",
        "limited", "ltd", "co", "company", "kenya", "nairobi",
        "account", "acct", "bank", "savings", "loan"
    )

    fun normalize(raw: String): String {
        return raw.trim()
            .lowercase()
            .replace(Regex("[^a-z0-9 ]"), "") // strip punctuation
            .split(" ")
            .filter { it.isNotBlank() && it !in NOISE }
            .joinToString(" ")
    }

    fun titleCase(normalized: String): String =
        normalized.split(" ").joinToString(" ") { w ->
            w.replaceFirstChar { it.uppercase() }
        }

    /** True if two normalized name strings refer to the same person. */
    fun areSamePerson(a: String, b: String): Boolean {
        if (a == b) return true

        val tokensA = a.split(" ").filter { it.isNotBlank() }.toSet()
        val tokensB = b.split(" ").filter { it.isNotBlank() }.toSet()

        // Token subset check: shorter name's tokens must all appear in longer name
        val (shorter, longer) = if (tokensA.size <= tokensB.size) tokensA to tokensB else tokensB to tokensA
        if (shorter.isNotEmpty() && longer.containsAll(shorter)) return true

        // Levenshtein for single-token or short names (handle SMS abbreviations / typos)
        if (a.length <= 15 && levenshtein(a, b) <= 2) return true
        if (a.length > 15  && levenshtein(a, b) <= 3) return true

        return false
    }

    /**
     * Groups a list of raw names into clusters of the same person.
     * Returns a map of canonical (longest/most-complete) name → all raw variants.
     */
    fun groupByCanonicalName(rawNames: List<String>): Map<String, List<String>> {
        // Normalize each name
        val normalized = rawNames.map { it to normalize(it) }

        // Union-Find to cluster
        val parent = rawNames.indices.toMutableList()

        fun find(x: Int): Int {
            var r = x
            while (parent[r] != r) r = parent[r]
            var n = x
            while (parent[n] != r) { val next = parent[n]; parent[n] = r; n = next }
            return r
        }

        fun union(x: Int, y: Int) { parent[find(x)] = find(y) }

        for (i in normalized.indices) {
            for (j in i + 1 until normalized.size) {
                if (areSamePerson(normalized[i].second, normalized[j].second)) {
                    union(i, j)
                }
            }
        }

        // Group by root
        val clusters = mutableMapOf<Int, MutableList<String>>()
        for (i in rawNames.indices) {
            clusters.getOrPut(find(i)) { mutableListOf() }.add(rawNames[i])
        }

        // For each cluster, pick the canonical name = the longest normalized variant (most complete)
        return clusters.values.associate { group ->
            val canonical = group.maxByOrNull { normalize(it).length }
                ?.let { titleCase(normalize(it)) } ?: group.first()
            canonical to group
        }
    }

    // Standard Levenshtein distance
    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                dp[i][j] = if (a[i - 1] == b[j - 1]) dp[i - 1][j - 1]
                else 1 + minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
            }
        }
        return dp[a.length][b.length]
    }
}
