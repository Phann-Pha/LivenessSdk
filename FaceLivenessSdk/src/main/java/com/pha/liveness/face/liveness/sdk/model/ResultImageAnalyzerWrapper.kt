package com.pha.liveness.face.liveness.sdk.model

import android.graphics.Bitmap
import com.google.android.gms.common.internal.Preconditions
import com.google.mlkit.vision.interfaces.Detector

class ResultImageAnalyzerWrapper(private val mValues: Map<Detector<*>, Any>, private var bitmap: Bitmap?, private var timestamp: Long, private val throwable: Map<Detector<*>, Throwable?>)
{
    /** @return as generic type **/
    fun <T> value(detector: Detector<T>): T?
    {
        checkDetectorExists(detector)
        return mValues[detector] as? T
    }

    /** @return original bitmap* */
    fun bitmap(): Bitmap? = bitmap

    /** @return timestamp of the detection* */
    fun timestamp(): Long = timestamp

    /** @return throwable exception **/
    fun throwable(detector: Detector<*>): Throwable?
    {
        checkDetectorExists(detector)
        return throwable[detector]
    }

    private fun checkDetectorExists(detector: Detector<*>)
    {
        val message = "The detector does not exist"
        Preconditions.checkArgument(mValues.containsKey(detector) || throwable.containsKey(detector), message)
    }
}