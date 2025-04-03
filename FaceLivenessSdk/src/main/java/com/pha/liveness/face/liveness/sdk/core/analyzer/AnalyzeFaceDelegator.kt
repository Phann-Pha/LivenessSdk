package com.pha.liveness.face.liveness.sdk.core.analyzer

import android.content.Context
import android.graphics.Matrix
import android.os.Handler
import android.os.Looper
import android.util.Size
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageAnalysis.Analyzer
import androidx.camera.core.ImageProxy
import androidx.core.os.ExecutorCompat
import com.pha.liveness.face.liveness.sdk.core.interpreter.FaceOcclusionDetector
import com.pha.liveness.face.liveness.sdk.core.interpreter.ImageQualityAnalyzer
import com.pha.liveness.face.liveness.sdk.core.interpreter.LivenessDetector
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlin.math.max

class AnalyzeFaceDelegator(context: Context, selector: Int = 0, detector: AnalyzeFaceValidator) : Analyzer
{
    private val imageQualityAnalyzer = ImageQualityAnalyzer()
    private val livenessDetector = LivenessDetector(context)
    private val occlusionDetector = FaceOcclusionDetector(context)

    private val faceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setMinFaceSize(0.3f)
            .enableTracking()
            .build()
    )

    private val executor = ExecutorCompat.create(Handler(Looper.getMainLooper()))
    private val delegate = ImageAnalyzer(listOf(faceDetector), ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST, executor, selector) { consumer ->
        detector.process(imageQualityAnalyzer, occlusionDetector, livenessDetector, consumer.bitmap(), consumer.value(faceDetector), detectionSize, consumer.timestamp())
    }

    private var detectionSize: Int = 640
    override fun analyze(image: ImageProxy)
    {
        detectionSize = max(image.width, image.height)
        delegate.analyze(image)
    }

    override fun updateTransform(matrix: Matrix?)
    {
        delegate.updateTransform(matrix)
    }

    override fun getDefaultTargetResolution(): Size = delegate.defaultTargetResolution()

    override fun getTargetCoordinateSystem(): Int = delegate.targetCoordinateSystem
}