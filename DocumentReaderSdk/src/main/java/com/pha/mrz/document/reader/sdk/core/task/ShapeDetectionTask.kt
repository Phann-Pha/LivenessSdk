package com.pha.mrz.document.reader.sdk.core.task

import com.google.mlkit.vision.objects.DetectedObject
import com.pha.mrz.document.reader.sdk.core.model.Quadrilateral
import com.pha.mrz.document.reader.sdk.util.ImageDetectionProperties
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import kotlin.math.max
import kotlin.math.min

class ShapeDetectionTask : DocumentDetectionTask
{
    companion object
    {
        private const val DOCUMENT_CAMERA_KEEP_TIME = 1500L
    }

    override var isTaskCompleted: Boolean = false

    override fun taskType(): String = DocumentDetectionConstance.SHAPE_DETECTION

    private var startTime = 0L

    override fun start()
    {
        startTime = System.currentTimeMillis()
    }

    override fun process(document: DetectedObject, quadrilateral: Quadrilateral, stdSize: Size, timestamp: Long): Boolean
    {
        if (isNotValidImage(quadrilateral.contour, quadrilateral.points, stdSize))
        {
            startTime = System.currentTimeMillis()
            return false
        }

        return System.currentTimeMillis() - startTime >= DOCUMENT_CAMERA_KEEP_TIME
    }

    private fun isNotValidImage(approx: MatOfPoint2f, points: Array<Point>, stdSize: Size): Boolean
    {
        // Attention: axis are swapped
        val previewWidth = stdSize.height.toFloat()
        val previewHeight = stdSize.width.toFloat()

        val resultWidth = max(previewWidth - points[0].y.toFloat(), previewWidth - points[1].y.toFloat()) - min(previewWidth - points[2].y.toFloat(), previewWidth - points[3].y.toFloat())
        val resultHeight = max(points[1].x.toFloat(), points[2].x.toFloat()) - min(points[0].x.toFloat(), points[3].x.toFloat())

        val imgDetectionPropsObj = ImageDetectionProperties(previewWidth.toDouble(), previewHeight.toDouble(), points[0], points[1], points[2], points[3], resultWidth.toInt(), resultHeight.toInt())
        return imgDetectionPropsObj.isNotValidImage(approx)
    }
}