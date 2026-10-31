package org.drishti.core.inference

import org.drishti.core.model.DetectedObstacle

interface ObstacleDetector {
    fun initialize(): Boolean
    fun detect(timestampMs: Long): List<DetectedObstacle>
    fun close()
}
