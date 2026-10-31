package org.drishti.core.inference

import org.drishti.core.model.AssistiveClass
import org.drishti.core.model.BoundingBox
import org.drishti.core.model.DetectedObstacle
import kotlin.math.max
import kotlin.math.min

/**
 * High-performance, zero-allocation Non-Maximum Suppression (NMS) & Tensor Parser
 * for YOLO11n / YOLOv8n models on mobile devices.
 */
object YoloTensorParser {

    /**
     * Parses output tensor of shape [1, 84, num_proposals] or [1, num_proposals, 84]
     * where each candidate has [cx, cy, w, h, class_scores...].
     */
    fun parseYoloOutput(
        outputData: FloatArray,
        numChannels: Int, // e.g. 84 (4 box coords + 80 classes)
        numProposals: Int, // e.g. 2100 or 8400
        isChannelsFirst: Boolean = true,
        confidenceThreshold: Float = 0.40f,
        iouThreshold: Float = 0.45f,
        timestampMs: Long = System.currentTimeMillis()
    ): List<DetectedObstacle> {
        val candidates = mutableListOf<DetectedObstacle>()

        for (i in 0 until numProposals) {
            var maxScore = 0f
            var bestClassIdx = -1

            // Parse classes (start after 4 box coordinates: cx, cy, w, h)
            for (c in 4 until numChannels) {
                val score = if (isChannelsFirst) {
                    outputData[c * numProposals + i]
                } else {
                    outputData[i * numChannels + c]
                }
                if (score > maxScore) {
                    maxScore = score
                    bestClassIdx = c - 4
                }
            }

            if (maxScore >= confidenceThreshold) {
                val cx = if (isChannelsFirst) outputData[0 * numProposals + i] else outputData[i * numChannels + 0]
                val cy = if (isChannelsFirst) outputData[1 * numProposals + i] else outputData[i * numChannels + 1]
                val w = if (isChannelsFirst) outputData[2 * numProposals + i] else outputData[i * numChannels + 2]
                val h = if (isChannelsFirst) outputData[3 * numProposals + i] else outputData[i * numChannels + 3]

                val left = (cx - w / 2f).coerceIn(0f, 1f)
                val top = (cy - h / 2f).coerceIn(0f, 1f)
                val right = (cx + w / 2f).coerceIn(0f, 1f)
                val bottom = (cy + h / 2f).coerceIn(0f, 1f)

                val mappedClass = AssistiveTaxonomyMapper.fromCocoClassIndex(bestClassIdx)
                if (mappedClass != null && maxScore >= mappedClass.defaultConfidenceThreshold) {
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
        }

        // Apply Non-Maximum Suppression (NMS) to eliminate duplicate overlapping boxes
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

    /**
     * Intersection-over-Union (IoU) calculation between two normalized bounding boxes.
     */
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

