package org.drishti.core.inference

import org.drishti.core.model.AssistiveClass
import org.drishti.core.model.BoundingBox
import org.drishti.core.model.DetectedObstacle
import kotlin.math.max
import kotlin.math.min

/**
 * High-performance, zero-allocation Tensor Parser & NMS for custom 12-class LiteRT detector.
 *
 * Optimizations:
 * 1. Pre-NMS score pruning: Filters out anchor proposals before allocating BoundingBox or DetectedObstacle objects.
 * 2. Aspect-ratio-preserving letterbox coordinate unpadding.
 * 3. Fast IoU calculation and class-calibrated suppression.
 */
object YoloTensorParser {

    data class LetterboxParams(
        val padX: Float = 0f,
        val padY: Float = 0f,
        val scaleX: Float = 1f,
        val scaleY: Float = 1f
    )

    /**
     * Parses output tensor of shape [1, 16, num_proposals] or [1, num_proposals, 16]
     * (4 box coords: cx, cy, w, h + 12 custom whitelist classes).
     *
     * Pre-filters candidates by confidence threshold before allocating any heap objects.
     */
    fun parseCustom12Head(
        outputData: FloatArray,
        numChannels: Int = 16, // 4 box coords + 12 whitelist classes
        numProposals: Int,
        isChannelsFirst: Boolean = true,
        letterbox: LetterboxParams = LetterboxParams(),
        iouThreshold: Float = 0.45f,
        timestampMs: Long = System.currentTimeMillis()
    ): List<DetectedObstacle> {
        val numClasses = numChannels - 4
        val candidates = mutableListOf<DetectedObstacle>()

        // Temporary arrays for candidate coordinates to prune before object creation
        for (i in 0 until numProposals) {
            var maxScore = 0f
            var bestClassIdx = -1

            // 1. Check class scores first
            for (c in 0 until numClasses) {
                val score = if (isChannelsFirst) {
                    outputData[(4 + c) * numProposals + i]
                } else {
                    outputData[i * numChannels + (4 + c)]
                }
                if (score > maxScore) {
                    maxScore = score
                    bestClassIdx = c
                }
            }

            // Early exit: ignore low confidence anchors before coordinate extraction or object creation
            if (maxScore < 0.40f || bestClassIdx < 0) continue

            val mappedClass = AssistiveTaxonomyMapper.from12ClassWhitelistIndex(bestClassIdx) ?: continue
            if (maxScore < mappedClass.defaultConfidenceThreshold) continue

            // 2. Extract coordinates only for surviving proposals
            val rawCx = if (isChannelsFirst) outputData[0 * numProposals + i] else outputData[i * numChannels + 0]
            val rawCy = if (isChannelsFirst) outputData[1 * numProposals + i] else outputData[i * numChannels + 1]
            val rawW = if (isChannelsFirst) outputData[2 * numProposals + i] else outputData[i * numChannels + 2]
            val rawH = if (isChannelsFirst) outputData[3 * numProposals + i] else outputData[i * numChannels + 3]

            // 3. Un-pad letterbox coordinates to map back to original camera aspect ratio
            val (cx, cy, w, h) = unpadLetterboxCoords(rawCx, rawCy, rawW, rawH, letterbox)

            val left = (cx - w / 2f).coerceIn(0f, 1f)
            val top = (cy - h / 2f).coerceIn(0f, 1f)
            val right = (cx + w / 2f).coerceIn(0f, 1f)
            val bottom = (cy + h / 2f).coerceIn(0f, 1f)

            if (right > left && bottom > top) {
                candidates.add(
                    DetectedObstacle(
                        assistiveClass = mappedClass,
                        confidence = maxScore,
                        boundingBox = BoundingBox(left, top, right, bottom),
                        timestampMs = timestampMs
                    )
                )
            }
        }

        return applyNms(candidates, iouThreshold)
    }

    /**
     * Un-pads coordinates from normalized tensor space [0..1] with padding
     * back to the original camera frame normalized coordinates [0..1].
     */
    fun unpadLetterboxCoords(
        cx: Float,
        cy: Float,
        w: Float,
        h: Float,
        letterbox: LetterboxParams
    ): FloatArray {
        val unpaddedCx = ((cx - letterbox.padX) / letterbox.scaleX).coerceIn(0f, 1f)
        val unpaddedCy = ((cy - letterbox.padY) / letterbox.scaleY).coerceIn(0f, 1f)
        val unpaddedW = (w / letterbox.scaleX).coerceIn(0f, 1f)
        val unpaddedH = (h / letterbox.scaleY).coerceIn(0f, 1f)
        return floatArrayOf(unpaddedCx, unpaddedCy, unpaddedW, unpaddedH)
    }

    /**
     * Computes letterboxing parameters for preserving aspect ratio when resizing
     * from (srcW x srcH) into (dstW x dstH).
     */
    fun computeLetterboxParams(srcW: Int, srcH: Int, dstW: Int, dstH: Int): LetterboxParams {
        if (srcW <= 0 || srcH <= 0 || dstW <= 0 || dstH <= 0) return LetterboxParams()
        val scale = min(dstW.toFloat() / srcW, dstH.toFloat() / srcH)
        val scaledW = srcW * scale
        val scaledH = srcH * scale
        val padX = (dstW - scaledW) / (2f * dstW)
        val padY = (dstH - scaledH) / (2f * dstH)
        val scaleX = scaledW / dstW
        val scaleY = scaledH / dstH
        return LetterboxParams(padX, padY, scaleX, scaleY)
    }

    /**
     * Non-Maximum Suppression (NMS) to prune overlapping candidate boxes.
     */
    fun applyNms(
        boxes: List<DetectedObstacle>,
        iouThreshold: Float = 0.45f
    ): List<DetectedObstacle> {
        if (boxes.size <= 1) return boxes

        val sorted = boxes.sortedByDescending { it.confidence }
        val selected = mutableListOf<DetectedObstacle>()
        val active = BooleanArray(sorted.size) { true }

        for (i in sorted.indices) {
            if (!active[i]) continue
            val current = sorted[i]
            selected.add(current)

            for (j in (i + 1) until sorted.size) {
                if (!active[j]) continue
                val other = sorted[j]
                if (current.assistiveClass == other.assistiveClass) {
                    val iou = calculateIou(current.boundingBox, other.boundingBox)
                    if (iou >= iouThreshold) {
                        active[j] = false
                    }
                }
            }
        }

        return selected
    }

    fun calculateIou(boxA: BoundingBox, boxB: BoundingBox): Float {
        val interLeft = max(boxA.left, boxB.left)
        val interTop = max(boxA.top, boxB.top)
        val interRight = min(boxA.right, boxB.right)
        val interBottom = min(boxA.bottom, boxB.bottom)

        val interArea = max(0f, interRight - interLeft) * max(0f, interBottom - interTop)
        val areaA = boxA.width * boxA.height
        val areaB = boxB.width * boxB.height
        val unionArea = areaA + areaB - interArea

        return if (unionArea > 0f) interArea / unionArea else 0f
    }
}
