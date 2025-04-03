package com.pha.liveness.face.liveness.sdk.task

import android.graphics.Bitmap
import com.google.mlkit.vision.face.Face
import com.pha.liveness.face.liveness.sdk.core.interpreter.FaceOcclusionDetector
import com.pha.liveness.face.liveness.sdk.core.interpreter.ImageQualityAnalyzer
import com.pha.liveness.face.liveness.sdk.core.interpreter.LivenessDetector
import com.pha.liveness.face.liveness.sdk.core.listener.LivenessDetectionTask
import com.pha.liveness.face.liveness.sdk.core.util.FacialDetectionUtil
import com.pha.liveness.face.liveness.sdk.core.util.DetectionConstance

class SmilingDetectionTask : LivenessDetectionTask
{
    override var isTaskCompleted: Boolean = false

    override fun taskType(): String = DetectionConstance.SMILE_DETECTION

    override fun process(imageQuality: ImageQualityAnalyzer, occlusionDetector: FaceOcclusionDetector, livenessDetector: LivenessDetector, bitmap: Bitmap?, face: Face, timestamp: Long): Boolean
    {
        val isSmile = (face.smilingProbability ?: 0f) > 0.6f
        return FacialDetectionUtil.isFacing(face) && FacialDetectionUtil.isEyesOpen(face) && isSmile && imageQuality.onCheckImageQuality(bitmap).isAcceptable() && !occlusionDetector.onValidationOcclusion(bitmap, face).isOccluded() && livenessDetector.isLive(bitmap, face).live()
    }
}