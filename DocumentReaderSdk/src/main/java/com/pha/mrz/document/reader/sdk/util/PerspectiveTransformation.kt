package com.pha.mrz.document.reader.sdk.util

import com.pha.mrz.document.reader.sdk.util.MathUtils.getDistance
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.util.*

internal object PerspectiveTransformation
{
    fun transform(src: Mat, corners: MatOfPoint2f): Mat
    {
        val sortedCorners = sortCorners(corners)
        val size = getRectangleSize(sortedCorners)
        val result = Mat.zeros(size, src.type())
        val imageOutline = getOutline(result)
        val transformation = Imgproc.getPerspectiveTransform(sortedCorners, imageOutline)
        Imgproc.warpPerspective(src, result, transformation, size)
        return result
    }

    private fun getRectangleSize(rectangle: MatOfPoint2f): Size
    {
        val corners = rectangle.toArray()
        val top = getDistance(corners[0], corners[1])
        val right = getDistance(corners[1], corners[2])
        val bottom = getDistance(corners[2], corners[3])
        val left = getDistance(corners[3], corners[0])
        val averageWidth = (top + bottom) / 2f
        val averageHeight = (right + left) / 2f
        return Size(Point(averageWidth, averageHeight))
    }

    private fun getOutline(image: Mat): MatOfPoint2f
    {
        val topLeft = Point(0.toDouble(), 0.toDouble())
        val topRight = Point(image.cols().toDouble(), 0.toDouble())
        val bottomRight = Point(image.cols().toDouble(), image.rows().toDouble())
        val bottomLeft = Point(0.toDouble(), image.rows().toDouble())
        val points = arrayOf(topLeft, topRight, bottomRight, bottomLeft)
        val result = MatOfPoint2f()
        result.fromArray(*points)
        return result
    }

    private fun sortCorners(corners: MatOfPoint2f): MatOfPoint2f
    {
        val center = getMassCenter(corners)
        val points = corners.toList()
        val topPoints: MutableList<Point> = ArrayList()
        val bottomPoints: MutableList<Point> = ArrayList()
        for (point in points)
        {
            if (point.y < center.y)
            {
                topPoints.add(point)
            }
            else
            {
                bottomPoints.add(point)
            }
        }

        val topLeft = if (topPoints[0].x > topPoints[1].x)
        {
            topPoints[1]
        }
        else
        {
            topPoints[0]
        }

        val topRight = if (topPoints[0].x > topPoints[1].x)
        {
            topPoints[0]
        }
        else
        {
            topPoints[1]
        }

        val bottomLeft = if (bottomPoints[0].x > bottomPoints[1].x)
        {
            bottomPoints[1]
        }
        else
        {
            bottomPoints[0]
        }

        val bottomRight = if (bottomPoints[0].x > bottomPoints[1].x)
        {
            bottomPoints[0]
        }
        else
        {
            bottomPoints[1]
        }
        val result = MatOfPoint2f()
        val sortedPoints = arrayOf(topLeft, topRight, bottomRight, bottomLeft)
        result.fromArray(*sortedPoints)
        return result
    }

    private fun getMassCenter(points: MatOfPoint2f): Point
    {
        var xSum = 0.0
        var ySum = 0.0
        val pointList = points.toList()
        val len = pointList.size
        for (point in pointList)
        {
            xSum += point.x
            ySum += point.y
        }
        return Point(xSum / len, ySum / len)
    }
}