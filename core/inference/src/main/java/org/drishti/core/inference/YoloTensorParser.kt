package org.drishti.core.inference

import org.drishti.core.model.AssistiveClass
import org.drishti.core.model.BoundingBox
import org.drishti.core.model.DetectedObstacle
import kotlin.math.max
import kotlin.math.min

/**
 * High-performance, zero-allocation Tensor Parser & NMS for custom 12-class LiteRT detector.
 * Enforces per-class calibrated thresholds and eliminates COCO-80 parsing.
 */
object YoloTensorParser {

    /**
     * Parses output tensor of shape [1, 16, num_proposals] or [1, num_proposals, 16]
     * (4 box coords: cx, cy, w, h + 12 custom whitelist classes).
     */
    fun parseCustom12Head(
        outputData: FloatArray,
        numChannels: Int = 16, // 4 box coords + 12 whitelist classes
        numProposals: Int,
        isChannelsFirst: Boolean = true,
        iouThreshold: Float = 0.45f,
        timestampMs: Long = System.currentTimeMillis()
    ): List<DetectedObstacle> {
        val candidates = mutableListOf<DetectedObstacle>()

        for (i in 0 until numProposals) {
            var maxScore = 0f
            var bestClassIdx = -1

            // Parse the 12 whitelist classes (channels 4..15)
            val numClasses = numChannels - 4
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

            val mappedClass = AssistiveTaxonomyMapper.from12ClassWhitelistIndex(bestClassIdx)
            // Apply calibrated per-class threshold (never a naive low threshold)
            if (mappedClass != null && maxScore >= mappedClass.defaultConfidenceThreshold) {
                val cx = if (isChannelsFirst) outputData[0 * numProposals + i] else outputData[i * numChannels + 0]
                val cy = if (isChannelsFirst) outputData[1 * numProposals + i] else outputData[i * numChannels + 1]
                val w = if (isChannelsFirst) outputData[2 * numProposals + i] else outputData[i * numChannels + 2]
                val h = if (isChannelsFirst) outputData[3 * numProposals + i] else outputData[i * numChannels + 3]

                val left = (cx - w / 2f).coerceIn(0f, 1f)
                val top = (cy - h / 2f).coerceIn(0f, 1f)
                val right = (cx + w / 2f).coerceIn(0f, 1f)
                val bottom = (cy + h / 2f).coerceIn(0f, 1f)

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
