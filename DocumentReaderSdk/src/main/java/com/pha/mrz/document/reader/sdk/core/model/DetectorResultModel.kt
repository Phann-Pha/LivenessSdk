package com.pha.mrz.document.reader.sdk.core.model

import android.graphics.Bitmap
import com.google.android.gms.common.internal.Preconditions
import com.google.mlkit.vision.interfaces.Detector

data class DetectorResultModel(private val mValues: MutableMap<Detector<*>, Any>, private val rgb: Bitmap? = null, private val timestamp: Long, private val quadrilateral: Quadrilateral, private val previewSize: org.opencv.core.Size, private val mThrowable: MutableMap<Detector<*>, Throwable?>)
{
    fun <T> value(detector: Detector<T>): T?
    {
        checkDetectorExists(detector)
        return mValues[detector] as? T
    }

    fun bitmap(): Bitmap? = rgb

    fun timestamp(): Long = timestamp

    fun quadrilateral(): Quadrilateral = quadrilateral

    fun previewSize(): org.opencv.core.Size = previewSize

    fun throwable(detector: Detector<*>): Throwable?
    {
        checkDetectorExists(detector)
        return mThrowable[detector]
    }

    private fun checkDetectorExists(detector: Detector<*>)
    {
        Preconditions.checkArgument(mValues.containsKey(detector) || mThrowable.containsKey(detector), "The detector does not exist")
    }
}