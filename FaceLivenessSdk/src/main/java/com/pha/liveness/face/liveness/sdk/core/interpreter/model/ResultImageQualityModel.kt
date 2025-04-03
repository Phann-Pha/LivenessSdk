package com.pha.liveness.face.liveness.sdk.core.interpreter.model

data class ResultImageQualityModel(val brightness: Float = 0.0f, val sharpness: Float = 0.0f)
{
    private val brightnessWeight = 0.3f
    private val sharpnessWeight = 0.3f
    private val thresholds = 0.5f // Minimum acceptable overall score

    fun isAcceptable(): Boolean
    {
        val overallScore = (brightness * brightnessWeight + sharpness * sharpnessWeight).coerceIn(0.0f, 1.0f)
        return overallScore >= thresholds
    }
}
