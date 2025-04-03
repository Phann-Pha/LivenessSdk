package com.pha.liveness.face.liveness.sdk.core.analyzer

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.YuvImage
import android.media.Image
import android.util.Log
import android.util.Size
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.impl.utils.TransformUtils
import androidx.camera.core.impl.utils.TransformUtils.getRectToRect
import androidx.camera.view.TransformExperimental
import androidx.camera.view.transform.ImageProxyTransformFactory
import androidx.core.util.Consumer
import com.google.android.gms.common.internal.Preconditions
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.interfaces.Detector
import com.pha.liveness.face.liveness.sdk.model.ResultImageAnalyzerWrapper
import java.io.ByteArrayOutputStream
import java.util.concurrent.CancellationException
import java.util.concurrent.Executor

@OptIn(TransformExperimental::class)
class ImageAnalyzer(detectors: List<FaceDetector>, targetCoordinateSystem: Int, executor: Executor, selector: Int = 0, consumer: Consumer<ResultImageAnalyzerWrapper>) : ImageAnalysis.Analyzer
{
    private val _detectors: List<FaceDetector>
    private val _targetCoordinateSystem: Int

    private val _selector: Int
    private val _consumer: Consumer<ResultImageAnalyzerWrapper>

    private val _executor: Executor
    private val _imageAnalysisTransformFactory: ImageProxyTransformFactory
    private var _sensorToTarget: Matrix? = null

    init
    {
        if (targetCoordinateSystem != ImageAnalysis.COORDINATE_SYSTEM_ORIGINAL)
        {
            for (detector in detectors)
            {
                val message = "Segmentation only works with COORDINATE_SYSTEM_ORIGINAL"
                Preconditions.checkArgument(detector.detectorType != Detector.TYPE_SEGMENTATION, message)
            }
        }

        // Make an immutable copy of the app provided detectors.
        _detectors = ArrayList(detectors)
        _targetCoordinateSystem = targetCoordinateSystem

        _selector = selector

        _consumer = consumer
        _executor = executor
        _imageAnalysisTransformFactory = ImageProxyTransformFactory()
        _imageAnalysisTransformFactory.isUsingRotationDegrees = true
    }

    @OptIn(ExperimentalGetImage::class)
    @SuppressLint("RestrictedApi")
    override fun analyze(imageProxy: ImageProxy)
    {
        val image = imageProxy.image
        if (image != null && image.format == ImageFormat.YUV_420_888)
        {
            /** By default, the matrix is identity for COORDINATE_SYSTEM_ORIGINAL. **/
            val analysisToTarget = Matrix()
            if (_targetCoordinateSystem != ImageAnalysis.COORDINATE_SYSTEM_ORIGINAL)
            {
                val sensorToTarget = _sensorToTarget
                if (sensorToTarget == null)
                {
                    /** If the app set a target coordinate system, do not perform detection until the transform is ready. */
                    Log.d(TAG, "Transform is null.")
                    imageProxy.close()
                    return
                }

                val sensorToAnalysis = Matrix(imageProxy.imageInfo.sensorToBufferTransformMatrix)

                /** Calculate the rotation added by ML Kit. */
                val sourceRect = RectF(0f, 0f, imageProxy.width.toFloat(), imageProxy.height.toFloat())
                val bufferRect: RectF = rotateRect(sourceRect, imageProxy.imageInfo.rotationDegrees)
                val analysisToMlKitRotation: Matrix = getRectToRect(sourceRect, bufferRect, imageProxy.imageInfo.rotationDegrees)

                /** Concat the MLKit transformation with sensor to Analysis. */
                sensorToAnalysis.postConcat(analysisToMlKitRotation)

                /** Invert to get analysis to sensor. */
                sensorToAnalysis.invert(analysisToTarget)

                /** Concat sensor to target to get analysisToTarget. */
                analysisToTarget.postConcat(sensorToTarget)
            }

            /** => Detect the image recursively, starting from index 0. */
            val bitmap = rotate(image.toBitmap(), imageProxy.imageInfo.rotationDegrees, _selector)
            detectRecursively(imageProxy, bitmap, 0, analysisToTarget, HashMap(), HashMap())
        }
    }

    private fun Image.toBitmap(): Bitmap
    {
        val planes = this.planes
        val yBuffer = planes[0].buffer
        val uBuffer = planes[1].buffer
        val vBuffer = planes[2].buffer
        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()
        val nv21 = ByteArray(ySize + uSize + vSize) // U and V are swapped
        yBuffer[nv21, 0, ySize]
        vBuffer[nv21, ySize, vSize]
        uBuffer[nv21, ySize + vSize, uSize]
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, this.width, this.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 100, out)
        val imageBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }

    @SuppressLint("RestrictedApi")
    fun rotateRect(rect: RectF, rotationDegrees: Int): RectF
    {
        fun within360(degrees: Int): Int
        {
            return (degrees % 360 + 360) % 360
        }

        Preconditions.checkArgument(rotationDegrees % 90 == 0, "Invalid rotation degrees: $rotationDegrees")
        return if (TransformUtils.is90or270(within360(rotationDegrees))) RectF(0f, 0f, rect.height(), rect.width()) else rect
    }

    fun defaultTargetResolution(): Size
    {
        var size = DEFAULT_SIZE
        for (detector in _detectors)
        {
            val detectorSize = getTargetResolution(detector.detectorType)
            if (detectorSize.height * detectorSize.width > size.width * size.height) size = detectorSize
        }
        return size
    }

    private fun getTargetResolution(detectorType: Int): Size
    {
        return when (detectorType)
        {
            Detector.TYPE_BARCODE_SCANNING, Detector.TYPE_TEXT_RECOGNITION -> Size(1280, 720)
            else                                                           -> DEFAULT_SIZE
        }
    }

    override fun getTargetCoordinateSystem(): Int
    {
        return _targetCoordinateSystem
    }

    override fun updateTransform(matrix: Matrix?)
    {
        _sensorToTarget = if (matrix == null) null else Matrix(matrix)
    }

    @OptIn(androidx.camera.core.ExperimentalGetImage::class)
    private fun detectRecursively(imageProxy: ImageProxy, bitmap: Bitmap?, detectorIndex: Int, transform: Matrix, values: MutableMap<Detector<*>, Any>, throwable: MutableMap<Detector<*>, Throwable?>)
    {
        val image = imageProxy.image
        if (image == null)
        {
            Log.e(TAG, "Image is null.")
            imageProxy.close()
            return
        }

        if (detectorIndex > _detectors.size - 1)
        {
            imageProxy.close()
            _executor.execute { _consumer.accept(ResultImageAnalyzerWrapper(values, bitmap, imageProxy.imageInfo.timestamp, throwable)) }
            return
        }

        val detector = _detectors[detectorIndex]
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val mlKitTask: Task<MutableList<Face>>
        try
        {
            mlKitTask = detector.process(image, rotationDegrees, transform)
        }
        catch (e: Exception)
        {
            throwable[detector] = RuntimeException("Failed to process the image.", e)
            detectRecursively(imageProxy, bitmap, detectorIndex + 1, transform, values, throwable)
            return
        }
        mlKitTask.addOnCompleteListener(_executor) { task ->

            /** => Record the return value and exception. */
            if (task.isCanceled)
            {
                throwable[detector] = CancellationException("The task is canceled.")
            }
            else if (task.isSuccessful)
            {
                values[detector] = task.result
            }
            else
            {
                throwable[detector] = task.exception
            }

            /** => Go to the next detector.*/
            detectRecursively(imageProxy, bitmap, detectorIndex + 1, transform, values, throwable)
        }
    }

    private fun rotate(bitmap: Bitmap, degree: Int, selector: Int): Bitmap
    {
        val matrix = Matrix()
        matrix.postRotate(degree.toFloat())
        if (selector == 0) matrix.postScale(-1f, 1f)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    companion object
    {
        private const val TAG = "ImageAnalyzer"
        private val DEFAULT_SIZE = Size(480, 360)
    }
}