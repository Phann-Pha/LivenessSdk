package com.pha.mrz.document.reader.sdk.core.evaluate

import android.graphics.Matrix
import android.os.Handler
import android.os.Looper
import android.util.Size
import android.view.View
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageAnalysis.Analyzer
import androidx.camera.core.ImageProxy
import androidx.camera.view.TransformExperimental
import androidx.core.os.ExecutorCompat
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.pha.mrz.document.reader.sdk.core.analyzer.ImageAnalyzer
import com.pha.mrz.document.reader.sdk.util.OpenCvNativeBridge
import com.pha.mrz.document.reader.sdk.util.view.ScanCanvasView
import kotlin.math.max

@TransformExperimental
class DocumentDelegator(detector: DocumentValidator, selector: Int, frame: View, reference: View, scanCanvasView: ScanCanvasView) : Analyzer
{
    private val openCv: OpenCvNativeBridge = OpenCvNativeBridge()
    private val executor = ExecutorCompat.create(Handler(Looper.getMainLooper()))
    private val detector = ObjectDetection.getClient(
        ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .enableClassification()
            .build()
    )

    private val delegate = ImageAnalyzer(listOf(this.detector), ImageAnalysis.COORDINATE_SYSTEM_ORIGINAL, executor, selector, openCv, frame, reference) { result ->
        detector.process(result.value(this.detector), result.quadrilateral(), result.previewSize(), result.timestamp())
        scanCanvasView.showShape(result.previewSize().width.toFloat(), result.previewSize().height.toFloat(), result.quadrilateral().points)
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

    override fun getDefaultTargetResolution(): Size = delegate.defaultTargetResolution

    override fun getTargetCoordinateSystem(): Int = delegate.targetCoordinateSystem
}