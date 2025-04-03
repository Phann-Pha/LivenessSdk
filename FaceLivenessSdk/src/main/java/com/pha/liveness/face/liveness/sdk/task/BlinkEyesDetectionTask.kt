package com.pha.liveness.face.liveness.sdk.task

import android.graphics.Bitmap
import com.google.mlkit.vision.face.Face
import com.pha.liveness.face.liveness.sdk.core.interpreter.FaceOcclusionDetector
import com.pha.liveness.face.liveness.sdk.core.interpreter.ImageQualityAnalyzer
import com.pha.liveness.face.liveness.sdk.core.interpreter.LivenessDetector
import com.pha.liveness.face.liveness.sdk.core.listener.LivenessDetectionTask
import com.pha.liveness.face.liveness.sdk.core.util.DetectionConstance
import com.pha.liveness.face.liveness.sdk.core.util.FacialDetectionUtil
import kotlin.math.absoluteValue

class BlinkEyesDetectionTask : LivenessDetectionTask
{
    private var timestamp: Long = 0L
    private var trackingId: Int = -1
    private var eyesOpen: Float = 0.7f
    private var eyesClose: Float = 0.3f
    private var leftEyeOpenProbabilityList: MutableList<Float> = arrayListOf()
    private var rightEyeOpenProbabilityList: MutableList<Float> = arrayListOf()

    private var sec = 0.1 * 1000000000
    override var isTaskCompleted: Boolean = false

    override fun taskType(): String = DetectionConstance.BLINK_EYES_DETECTION

    override fun process(imageQuality: ImageQualityAnalyzer, occlusionDetector: FaceOcclusionDetector, livenessDetector: LivenessDetector, bitmap: Bitmap?, face: Face, timestamp: Long): Boolean
    {
        var leftEyeBlinked = false
        var rightEyeBlinked = false

        if ((this.timestamp - timestamp).absoluteValue > sec)
        {
            this.timestamp = timestamp
            if (trackingId != face.trackingId)
            {
                trackingId = face.trackingId ?: -1
            }
            else
            {
                leftEyeOpenProbabilityList.add(face.leftEyeOpenProbability ?: 0f)
                if (leftEyeOpenProbabilityList.size > 2)
                {
                    leftEyeBlinked = leftEyeOpenProbabilityList[leftEyeOpenProbabilityList.size - 2] > eyesOpen && leftEyeOpenProbabilityList[leftEyeOpenProbabilityList.size - 1] < eyesClose
                }
                rightEyeOpenProbabilityList.add(face.rightEyeOpenProbability ?: 0f)
                if (rightEyeOpenProbabilityList.size > 2)
                {
                    rightEyeBlinked = rightEyeOpenProbabilityList[rightEyeOpenProbabilityList.size - 2] > eyesOpen && rightEyeOpenProbabilityList[rightEyeOpenProbabilityList.size - 1] < eyesClose
                }
            }
        }

        return leftEyeBlinked && rightEyeBlinked && FacialDetectionUtil.isFacing(face) && imageQuality.onCheckImageQuality(bitmap).isAcceptable() && !occlusionDetector.onValidationOcclusion(bitmap, face).isOccluded() && livenessDetector.isLive(bitmap, face).live()
    }
}
