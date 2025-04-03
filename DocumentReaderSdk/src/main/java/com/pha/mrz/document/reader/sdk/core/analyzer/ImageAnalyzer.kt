package com.pha.mrz.document.reader.sdk.core.analyzer

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.YuvImage
import android.media.Image
import android.util.Size
import android.view.View
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
import com.google.mlkit.vision.interfaces.Detector
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetector
import com.pha.mrz.document.reader.sdk.core.model.DetectorResultModel
import com.pha.mrz.document.reader.sdk.core.model.Quadrilateral
import com.pha.mrz.document.reader.sdk.util.OpenCvNativeBridge
import com.pha.mrz.document.reader.sdk.util.extensions.toMat
import java.io.ByteArrayOutputStream
import java.util.concurrent.CancellationException
import java.util.concurrent.Executor

@TransformExperimental
class ImageAnalyzer(detectors: List<ObjectDetector>, targetCoordinateSystem: Int, executor: Executor, selector: Int, openCv: OpenCvNativeBridge, frame: View, reference: View, consumer: Consumer<DetectorResultModel>) : ImageAnalysis.Analyzer
{
    private val mDetectors: List<ObjectDetector>
    private val mTargetCoordinateSystem: Int

    private val mConsumer: Consumer<DetectorResultModel>

    private val mImageAnalysisTransformFactory: ImageProxyTransformFactory
    private val mExecutor: Executor

    private var mSensorToTarget: Matrix? = null

    private val mSelector: Int
    private val frame: View
    private val reference: View

    private val openCv: OpenCvNativeBridge

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

        mDetectors = ArrayList(detectors)
        mTargetCoordinateSystem = targetCoordinateSystem
        mConsumer = consumer
        mExecutor = executor
        mImageAnalysisTransformFactory = ImageProxyTransformFactory()
        mImageAnalysisTransformFactory.isUsingRotationDegrees = true
        mSelector = selector
        this.frame = frame
        this.reference = reference
        this.openCv = openCv
    }

    @OptIn(ExperimentalGetImage::class)
    @SuppressLint("RestrictedApi")
    override fun analyze(imageProxy: ImageProxy)
    {
        val image = imageProxy.image
        if (image != null && image.format == ImageFormat.YUV_420_888)
        {
            val analysisToTarget = Matrix()
            if (mTargetCoordinateSystem != ImageAnalysis.COORDINATE_SYSTEM_ORIGINAL)
            {
                val sensorToTarget = mSensorToTarget
                if (sensorToTarget == null)
                {
                    imageProxy.close()
                    return
                }

                val sensorToAnalysis = Matrix(imageProxy.imageInfo.sensorToBufferTransformMatrix)

                /** Calculate the rotation added by ML Kit. */
                val sourceRect = RectF(0f, 0f, imageProxy.width.toFloat(), imageProxy.height.toFloat())
                val bufferRect: RectF = TransformUtils.rotateRect(sourceRect, imageProxy.imageInfo.rotationDegrees)
                val analysisToMlKitRotation: Matrix = getRectToRect(sourceRect, bufferRect, imageProxy.imageInfo.rotationDegrees)

                /** Concat the MLKit transformation with sensor to Analysis. */
                sensorToAnalysis.postConcat(analysisToMlKitRotation)

                /** Invert to get analysis to sensor. */
                sensorToAnalysis.invert(analysisToTarget)

                /** Concat sensor to target to get analysisToTarget. */
                analysisToTarget.postConcat(sensorToTarget)
            }

            // Detect the image recursively, starting from index 0.
            val original = image.toRgbBitmap()
            val rotated = rotate(original, imageProxy.imageInfo.rotationDegrees, mSelector)
            val final = cropImage(rotated, frame, reference)

            val mat = final.toMat()
            val previewSize = mat.size()
            val quadrilateral = openCv.detectLargestQuadrilateral(mat)
            mat.release()
            if (quadrilateral != null)
            {
                detectRecursively(imageProxy, final, 0, analysisToTarget, HashMap(), quadrilateral, previewSize, HashMap())
            }
            else
            {
                imageProxy.close()
            }
        }
        else
        {
            imageProxy.close()
        }
    }

    private fun cropImage(bitmap: Bitmap, frame: View, reference: View): Bitmap
    {
        try
        {
            val heightOriginal = frame.height
            val widthOriginal = frame.width
            val heightFrame = reference.height
            val widthFrame = reference.width
            val leftFrame = reference.left
            val topFrame = reference.top
            val heightReal = bitmap.height
            val widthReal = bitmap.width
            val widthFinal = widthFrame * widthReal / widthOriginal
            val heightFinal = heightFrame * heightReal / heightOriginal
            val leftFinal = leftFrame * widthReal / widthOriginal
            val topFinal = topFrame * heightReal / heightOriginal
            val bitmapFinal = Bitmap.createBitmap(bitmap, leftFinal, topFinal, widthFinal, heightFinal)
            val stream = ByteArrayOutputStream()
            bitmapFinal.compress(Bitmap.CompressFormat.JPEG, 100, stream)
            return bitmapFinal
        }
        catch (e: Exception)
        {
            return bitmap
        }
    }

    @OptIn(ExperimentalGetImage::class)
    private fun detectRecursively(imageProxy: ImageProxy, rgb: Bitmap? = null, detectorIndex: Int, transform: Matrix, values: MutableMap<Detector<*>, Any>, quadrilateral: Quadrilateral, previewSize: org.opencv.core.Size, throwable: MutableMap<Detector<*>, Throwable?>)
    {
        val image = imageProxy.image
        if (image == null)
        {
            imageProxy.close()
            return
        }

        if (detectorIndex > mDetectors.size - 1)
        {
            imageProxy.close()
            mExecutor.execute { mConsumer.accept(DetectorResultModel(values, rgb, imageProxy.imageInfo.timestamp, quadrilateral, previewSize, throwable)) }
            return
        }

        val detector = mDetectors[detectorIndex]
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val mlKitTask: Task<MutableList<DetectedObject>>

        try
        {
            mlKitTask = detector.process(image, rotationDegrees, transform)
        }
        catch (e: Exception)
        {
            throwable[detector] = RuntimeException("Failed to process the image.", e)
            detectRecursively(imageProxy, rgb, detectorIndex + 1, transform, values, quadrilateral, previewSize, throwable)
            return
        }

        mlKitTask.addOnCompleteListener(mExecutor) { task ->
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

            detectRecursively(imageProxy, rgb, detectorIndex + 1, transform, values, quadrilateral, previewSize, throwable)
        }
    }

    override fun getDefaultTargetResolution(): Size
    {
        var size: Size = DEFAULT_SIZE
        for (detector in mDetectors)
        {
            val detectorSize = getTargetResolution(detector.detectorType)
            if (detectorSize.height * detectorSize.width > size.width * size.height)
            {
                size = detectorSize
            }
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
        return mTargetCoordinateSystem
    }

    override fun updateTransform(matrix: Matrix?)
    {
        mSensorToTarget = if (matrix == null) null else Matrix(matrix)
    }

    private fun Image.toRgbBitmap(): Bitmap
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

    private fun rotate(bitmap: Bitmap, degree: Int, selector: Int): Bitmap
    {
        val matrix = Matrix()
        matrix.postRotate(degree.toFloat())
        if (selector == 0)
        {
            matrix.postScale(-1f, 1f)
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    companion object
    {
        private val DEFAULT_SIZE = Size(480, 360)
    }
}
