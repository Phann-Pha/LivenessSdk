package com.pha.liveness.face.liveness.sdk.task

import android.graphics.Bitmap
import com.google.mlkit.vision.face.Face
import com.pha.liveness.face.liveness.sdk.core.interpreter.FaceOcclusionDetector
import com.pha.liveness.face.liveness.sdk.core.interpreter.ImageQualityAnalyzer
import com.pha.liveness.face.liveness.sdk.core.interpreter.LivenessDetector
import com.pha.liveness.face.liveness.sdk.core.listener.LivenessDetectionTask
import com.pha.liveness.face.liveness.sdk.core.util.FacialDetectionUtil
import com.pha.liveness.face.liveness.sdk.core.util.DetectionConstance

class FacingDetectionTask : LivenessDetectionTask
{
    companion object
    {
        private const val FACING_CAMERA_KEEP_TIME = 1500L
    }

    override var isTaskCompleted: Boolean = false

    override fun taskType(): String = DetectionConstance.FACING_DETECTION

    private var startTime = 0L

    override fun start()
    {
        startTime = System.currentTimeMillis()
    }

    override fun process(imageQuality: ImageQualityAnalyzer, occlusionDetector: FaceOcclusionDetector, livenessDetector: LivenessDetector, bitmap: Bitmap?, face: Face, timestamp: Long): Boolean
    {
        if (!FacialDetectionUtil.isFacing(face) && !FacialDetectionUtil.isEyesOpen(face) && !imageQuality.onCheckImageQuality(bitmap).isAcceptable() && occlusionDetector.onValidationOcclusion(bitmap, face).isOccluded() && !livenessDetector.isLive(bitmap, face).live())
        {
            startTime = System.currentTimeMillis()
            return false
        }

        return System.currentTimeMillis() - startTime >= FACING_CAMERA_KEEP_TIME
    }
}