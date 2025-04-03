package com.pha.liveness.face.liveness.sdk.core.util

import android.graphics.PointF
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceLandmark
import kotlin.math.acos
import kotlin.math.sqrt

object FacialDetectionUtil
{
    fun isFacing(face: Face): Boolean = face.headEulerAngleZ < 7.78f && face.headEulerAngleZ > -7.78f
            && face.headEulerAngleY < 11.8f && face.headEulerAngleY > -11.8f
            && face.headEulerAngleX < 19.8f && face.headEulerAngleX > -19.8f

    fun isEyesOpen(face: Face): Boolean
    {
        val leftEyes = face.leftEyeOpenProbability ?: 0f
        val rightEyes = face.rightEyeOpenProbability ?: 0f

        return leftEyes >= 0.5f || rightEyes >= 0.5f
    }

    fun isMouthOpened(face: Face): Boolean
    {
        val left = face.getLandmark(FaceLandmark.MOUTH_LEFT)?.position ?: return false
        val right = face.getLandmark(FaceLandmark.MOUTH_RIGHT)?.position ?: return false
        val bottom = face.getLandmark(FaceLandmark.MOUTH_BOTTOM)?.position ?: return false

        // Square of lengths be a2, b2, c2
        val a2 = lengthSquare(right, bottom)
        val b2 = lengthSquare(left, bottom)
        val c2 = lengthSquare(left, right)

        // length of sides be a, b, c
        val a = sqrt(a2)
        val b = sqrt(b2)

        // From Cosine law
        val gamma = acos((a2 + b2 - c2) / (2 * a * b))

        // Converting to degrees
        val gammaDeg = gamma * 180 / Math.PI
        return gammaDeg < 115f
    }

    private fun lengthSquare(a: PointF, b: PointF): Float
    {
        val x = a.x - b.x
        val y = a.y - b.y
        return x * x + y * y
    }

    fun isFaceInDetectionRect(face: Face, detectionSize: Int): Boolean
    {
        val fRect = face.boundingBox
        val fx = fRect.centerX()
        val fy = fRect.centerY()
        val gridSize = detectionSize / 8
        if (fx < gridSize * 2 || fx > gridSize * 6 || fy < gridSize * 2 || fy > gridSize * 6)
        {
            return false
        }

        val fw = fRect.width()
        val fh = fRect.height()
        return !(fw < gridSize * 3 || fw > gridSize * 6 || fh < gridSize * 3 || fh > gridSize * 6)
    }
}