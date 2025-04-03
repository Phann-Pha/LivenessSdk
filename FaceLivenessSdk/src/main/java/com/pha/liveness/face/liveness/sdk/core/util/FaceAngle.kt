package com.pha.liveness.face.liveness.sdk.core.util

class FaceAngle(private var coordinateX: Float, private var coordinateY: Float, private var coordinateZ: Float)
{
    private val x = this.coordinateX
    private val y = this.coordinateY
    private val z = this.coordinateZ

    // ---------------------------------------------------

    fun superDown(): Boolean = if (x < -36f) true else false

    fun down(): Boolean = x > -36f && x < -12f

    fun frontalVertical(): Boolean = x > -12f && x < 12f

    fun up(): Boolean = x > 12f && x < 36f

    fun superUp(): Boolean = if (x > 36f) true else false

    // -------------------------------------------------------

    fun superLeft(): Boolean = if (y < -36f) true else false

    fun left(): Boolean = y > -36f && y < -12f

    fun frontalHorizontal(): Boolean = y > -12f && y < 12f

    fun right(): Boolean = y > 12f && y < 36f

    fun superRight(): Boolean = if (y > 36f) true else false

    // -----------------------------------------------------

    fun superTiltRight(): Boolean = if (z < -36f) true else false

    fun tiltRight(): Boolean = z > -36f && z < -12f

    fun tiltFrontal(): Boolean = z > -12f && z < 12f

    fun tiltLeft(): Boolean = z > 12f && z < 36f

    fun superTiltLeft(): Boolean = if (z > 36f) true else false

}