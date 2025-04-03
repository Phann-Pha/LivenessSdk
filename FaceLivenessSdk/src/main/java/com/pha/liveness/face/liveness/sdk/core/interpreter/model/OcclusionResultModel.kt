package com.pha.liveness.face.liveness.sdk.core.interpreter.model

/***
 *  this class used for representing
 *  the result of the face occlusion detection task
 *
 *  @property label The prediction result label
 *  @property confidence The confidence level of the prediction (0.0 to 1.0)
 */
data class OcclusionResultModel(val label: String? = "normal", val confidence: Float? = 0.0f)
{
    fun isOccluded(): Boolean = label != "normal"
}