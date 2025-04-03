package com.pha.liveness.face.liveness.sdk.task

import android.graphics.Bitmap
import com.google.mlkit.vision.face.Face
import com.pha.liveness.face.liveness.sdk.core.interpreter.FaceOcclusionDetector
import com.pha.liveness.face.liveness.sdk.core.interpreter.ImageQualityAnalyzer
import com.pha.liveness.face.liveness.sdk.core.interpreter.LivenessDetector
import com.pha.liveness.face.liveness.sdk.core.listener.LivenessDetectionTask
import com.pha.liveness.face.liveness.sdk.core.util.DetectionConstance
import com.pha.liveness.face.liveness.sdk.core.util.FaceAngle

class TopMoveDetectionTask : LivenessDetectionTask
{
    private var hasMoveToTop = false

    override var isTaskCompleted = false

    override fun taskType(): String = DetectionConstance.MOVEMENT_TOP_DETECTION

    override fun start()
    {
        hasMoveToTop = false
    }

    override fun process(imageQuality: ImageQualityAnalyzer, occlusionDetector: FaceOcclusionDetector, livenessDetector: LivenessDetector, bitmap: Bitmap?, face: Face, timestamp: Long): Boolean
    {
        val angle = FaceAngle(face.headEulerAngleX, face.headEulerAngleY, face.headEulerAngleZ)
        if (imageQuality.onCheckImageQuality(bitmap).isAcceptable() && !occlusionDetector.onValidationOcclusion(bitmap, face).isOccluded() && livenessDetector.isLive(bitmap, face).live())
        {
            if (angle.up() || angle.superUp())
            {
                hasMoveToTop = true
            }
        }
        return hasMoveToTop
    }
}