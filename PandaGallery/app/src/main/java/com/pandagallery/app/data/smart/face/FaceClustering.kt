package com.pandagallery.app.data.smart.face

/** Identifies one detected face: which photo it came from, and which face within it. */
data class FaceRef(val mediaId: Long, val faceIndex: Int) : Comparable<FaceRef> {
    override fun compareTo(other: FaceRef): Int =
        compareValuesBy(this, other, FaceRef::mediaId, FaceRef::faceIndex)

    override fun toString(): String = "$mediaId:$faceIndex"
}

/**
 * One face's embedding plus the metadata clustering needs.
 *
 * [embedding] must be L2-normalized so cosine similarity is a plain dot product.
 * [quality] in 0..1 drives seeding order — sharp, front-facing, large faces become
 * cluster seeds so a blurry profile shot never defines what a person "looks like".
 * [previousKey] is the group this face belonged to last run, which is what keeps a
 * user's chosen name attached to a person across re-indexes.
 */
class FaceObservation(
    val ref: FaceRef,
    val embedding: FloatArray,
    val quality: Float,
    val previousKey: String? = null,
)

class FaceCluster(
    val key: String,
    val centroid: FloatArray,
    val refs: List<FaceRef>,
) {
    val mediaIds: Set<Long> = refs.mapTo(linkedSetOf(), FaceRef::mediaId)
}

/**
 * Similarity above which two faces are considered the same person on first pass.
 * Tuned conservatively: a missed match shows up as two groups the user can merge in one
 * tap, while a false match silently puts a stranger in someone's album.
 */
const val SAME_PERSON_THRESHOLD = 0.62f

/** Centroid similarity above which two whole clusters collapse. Stricter than the above. */
const val CLUSTER_MERGE_THRESHOLD = 0.70f

/** Sweeps of the merge pass. Three is enough to settle in practice and bounds the cost. */
private const val MAX_MERGE_PASSES = 3

/**
 * Groups faces by identity.
 *
 * Full pairwise agglomerative clustering is O(n²) in the number of faces, which stops
 * being viable somewhere around a few thousand photos. This does the practical thing
 * instead: a quality-ordered nearest-centroid pass (O(n·k)) followed by a small number of
 * centroid merge sweeps (O(k²)), which recovers most of what single-pass assignment gets
 * wrong when a person's first-seen face was unrepresentative.
 *
 * Deterministic for a given input set — ordering is fully specified at every step, so the
 * same library always produces the same groups.
 */
fun clusterFaces(
    observations: List<FaceObservation>,
    sameThreshold: Float = SAME_PERSON_THRESHOLD,
    mergeThreshold: Float = CLUSTER_MERGE_THRESHOLD,
    minPhotosPerPerson: Int = 2,
): List<FaceCluster> {
    if (observations.isEmpty()) return emptyList()

    val ordered = observations.sortedWith(
        compareByDescending<FaceObservation> { it.quality }.thenBy { it.ref }
    )

    val members = mutableListOf<MutableList<FaceObservation>>()
    val centroids = mutableListOf<FloatArray>()

    ordered.forEach { observation ->
        var bestIndex = -1
        var bestSimilarity = sameThreshold
        centroids.forEachIndexed { index, centroid ->
            val similarity = dot(centroid, observation.embedding)
            // Strictly greater keeps assignment stable when two centroids tie.
            if (similarity > bestSimilarity) {
                bestSimilarity = similarity
                bestIndex = index
            }
        }
        if (bestIndex < 0) {
            members += mutableListOf(observation)
            centroids += observation.embedding.copyOf()
        } else {
            members[bestIndex] += observation
            centroids[bestIndex] = meanNormalized(members[bestIndex])
        }
    }

    for (pass in 0 until MAX_MERGE_PASSES) {
        if (!mergeOnce(members, centroids, mergeThreshold)) break
    }

    return buildClusters(members, centroids, minPhotosPerPerson)
}

/**
 * One merge sweep. Collects every cluster pair above [threshold], applies them
 * highest-similarity-first through a union-find so a chain of near-duplicates collapses in
 * a single pass, and rebuilds centroids from the merged membership.
 *
 * @return true when anything merged, so the caller can stop sweeping early.
 */
private fun mergeOnce(
    members: MutableList<MutableList<FaceObservation>>,
    centroids: MutableList<FloatArray>,
    threshold: Float,
): Boolean {
    val count = members.size
    if (count < 2) return false

    val candidates = mutableListOf<Triple<Float, Int, Int>>()
    for (left in 0 until count) {
        for (right in left + 1 until count) {
            val similarity = dot(centroids[left], centroids[right])
            if (similarity >= threshold) candidates += Triple(similarity, left, right)
        }
    }
    if (candidates.isEmpty()) return false

    val parent = IntArray(count) { it }
    fun find(node: Int): Int {
        var current = node
        while (parent[current] != current) {
            parent[current] = parent[parent[current]]
            current = parent[current]
        }
        return current
    }

    candidates
        .sortedWith(compareByDescending<Triple<Float, Int, Int>> { it.first }.thenBy { it.second }.thenBy { it.third })
        .forEach { (_, left, right) ->
            val leftRoot = find(left)
            val rightRoot = find(right)
            // Lowest index wins so the surviving cluster is deterministic.
            if (leftRoot != rightRoot) parent[maxOf(leftRoot, rightRoot)] = minOf(leftRoot, rightRoot)
        }

    val regrouped = LinkedHashMap<Int, MutableList<FaceObservation>>()
    for (index in 0 until count) {
        regrouped.getOrPut(find(index)) { mutableListOf() } += members[index]
    }
    if (regrouped.size == count) return false

    members.clear()
    centroids.clear()
    regrouped.values.forEach { group ->
        members += group
        centroids += meanNormalized(group)
    }
    return true
}

/**
 * Turns raw membership into named clusters, inheriting each group's key from whatever its
 * members were called last run so user-assigned names survive re-indexing. When two
 * clusters both inherit the same key — a person who legitimately split in two — the larger
 * group keeps the name and the smaller mints a fresh one.
 */
private fun buildClusters(
    members: List<List<FaceObservation>>,
    centroids: List<FloatArray>,
    minPhotosPerPerson: Int,
): List<FaceCluster> {
    val surviving = members.indices
        .map { index -> index to members[index] }
        .filter { (_, group) -> group.mapTo(mutableSetOf()) { it.ref.mediaId }.size >= minPhotosPerPerson }
        .sortedWith(
            compareByDescending<Pair<Int, List<FaceObservation>>> { it.second.size }
                .thenBy { it.second.minOf(FaceObservation::ref) }
        )

    val claimed = mutableSetOf<String>()
    return surviving.map { (index, group) ->
        val inherited = group.mapNotNull(FaceObservation::previousKey)
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map(Map.Entry<String, Int>::key)
            .firstOrNull { it !in claimed }
        val key = inherited ?: mintKey(group, claimed)
        claimed += key
        FaceCluster(
            key = key,
            centroid = centroids[index],
            refs = group.map(FaceObservation::ref).sorted(),
        )
    }
}

/** A fresh key derived from the group's best face, disambiguated if it somehow collides. */
private fun mintKey(group: List<FaceObservation>, claimed: Set<String>): String {
    val seed = group.maxWithOrNull(
        compareBy<FaceObservation> { it.quality }.thenByDescending { it.ref }
    ) ?: group.first()
    var candidate = "p:${seed.ref}"
    var suffix = 1
    while (candidate in claimed) candidate = "p:${seed.ref}#${suffix++}"
    return candidate
}

/** Cosine similarity for L2-normalized vectors. */
internal fun dot(left: FloatArray, right: FloatArray): Float {
    if (left.size != right.size) return -1f
    var sum = 0f
    for (index in left.indices) sum += left[index] * right[index]
    return sum
}

/** Mean of the members, renormalized so it stays comparable via [dot]. */
private fun meanNormalized(group: List<FaceObservation>): FloatArray {
    val dimensions = group.first().embedding.size
    val sum = FloatArray(dimensions)
    group.forEach { observation ->
        val embedding = observation.embedding
        for (index in 0 until dimensions) sum[index] += embedding[index]
    }
    return l2Normalize(sum)
}

/** In-place-safe L2 normalization. A zero vector is returned unchanged rather than NaN. */
internal fun l2Normalize(vector: FloatArray): FloatArray {
    var magnitude = 0f
    for (value in vector) magnitude += value * value
    magnitude = kotlin.math.sqrt(magnitude)
    if (magnitude <= 1e-6f) return vector
    for (index in vector.indices) vector[index] = vector[index] / magnitude
    return vector
}
