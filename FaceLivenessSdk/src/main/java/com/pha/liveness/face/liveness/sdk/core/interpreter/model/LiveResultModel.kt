package com.pha.liveness.face.liveness.sdk.core.interpreter.model

/***
 *  this class used for representing
 *  the result of the face live task
 *
 *  @property label The prediction result label
 *  @property confidence The confidence level of the prediction (0.0 to 1.0)
 */
data class LiveResultModel(val label: String? = "spoof", val confidence: Float? = 0.0f)
{
    fun live(): Boolean = label?.lowercase() == "live"
}