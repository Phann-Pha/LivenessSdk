package com.pha.liveness.face.liveness.sdk.core.analyzer

import android.graphics.Bitmap
import com.google.mlkit.vision.face.Face
import com.pha.liveness.face.liveness.sdk.core.interpreter.FaceOcclusionDetector
import com.pha.liveness.face.liveness.sdk.core.interpreter.ImageQualityAnalyzer
import com.pha.liveness.face.liveness.sdk.core.interpreter.LivenessDetector
import com.pha.liveness.face.liveness.sdk.core.listener.LivenessDetectionTask
import com.pha.liveness.face.liveness.sdk.core.util.FacialDetectionUtil
import java.util.Deque
import java.util.LinkedList

class AnalyzeFaceValidator(vararg tasks: LivenessDetectionTask)
{
    interface Listener
    {
        fun onTaskStarted(task: LivenessDetectionTask)

        fun onTaskCompleted(task: LivenessDetectionTask, isLastTask: Boolean)

        fun onTaskFailed(task: LivenessDetectionTask, code: Int)
    }

    companion object
    {
        private const val FACE_CACHE_SIZE = 5
        private const val NO_ERROR = -1
        const val ERROR_NO_FACE = 0
        const val ERROR_MULTI_FACES = 1
        const val ERROR_OUT_OF_DETECTION_FRAME = 2
        const val ERROR_COVER_FACE = 3
        const val IMAGE_QUALITY = 4
        const val ANTI_SPOOF = 5
    }

    init
    {
        check(tasks.isNotEmpty()) { "no tasks" }
    }

    private val tasks = tasks.asList()
    private var taskIndex = 0
    private var lastTaskIndex = -1
    private var currentErrorState = NO_ERROR
    private val lastFaces: Deque<Face> = LinkedList()
    private var listener: Listener? = null

    fun process(imageQuality: ImageQualityAnalyzer, occlusionDetector: FaceOcclusionDetector, livenessDetector: LivenessDetector, bitmap: Bitmap?, faces: MutableList<Face>?, detectionSize: Int, timestamp: Long)
    {
        val task = tasks.getOrNull(taskIndex) ?: return
        if (taskIndex != lastTaskIndex)
        {
            lastTaskIndex = taskIndex
            task.start()
            listener?.onTaskStarted(task)
        }

        val face = filter(imageQuality, occlusionDetector, livenessDetector, bitmap, task, faces, detectionSize) ?: return
        if (task.process(imageQuality, occlusionDetector, livenessDetector, bitmap, face, timestamp))
        {
            task.isTaskCompleted = true
            listener?.onTaskCompleted(task, taskIndex == tasks.lastIndex)
            taskIndex++
        }
    }

    fun setListener(listener: Listener?)
    {
        this.listener = listener
    }

    private fun getTasks(): List<LivenessDetectionTask>
    {
        return this.tasks
    }

    fun reset()
    {
        taskIndex = 0
        lastTaskIndex = -1
        lastFaces.clear()
        getTasks().forEach { it.isTaskCompleted = false }
    }

    private fun filter(imageQuality: ImageQualityAnalyzer, occlusionDetector: FaceOcclusionDetector, livenessDetector: LivenessDetector, bitmap: Bitmap?, task: LivenessDetectionTask, faces: MutableList<Face>?, detectionSize: Int): Face?
    {
        if (faces.isNullOrEmpty() && lastFaces.isEmpty()) // no face
        {
            changeErrorState(task, ERROR_NO_FACE)
            reset()
            return null
        }

        if (faces != null && faces.size > 1)
        {
            changeErrorState(task, ERROR_MULTI_FACES)
            reset()
            return null
        }

        val face = faces?.firstOrNull() ?: lastFaces.pollFirst()
        /*if (!FacialDetectionUtil.isFaceInDetectionRect(face, detectionSize))
        {
            changeErrorState(task, ERROR_OUT_OF_DETECTION_FRAME)
            reset()
            return null
        }*/

        if (!imageQuality.onCheckImageQuality(bitmap).isAcceptable())
        {
            changeErrorState(task, IMAGE_QUALITY)
            reset()
            return null
        }

        if (occlusionDetector.onValidationOcclusion(bitmap, face).isOccluded())
        {
            changeErrorState(task, ERROR_COVER_FACE)
            reset()
            return null
        }

        if (!livenessDetector.isLive(bitmap, face).live())
        {
            changeErrorState(task, ANTI_SPOOF)
            reset()
            return null
        }

        if (!faces.isNullOrEmpty())
        {
            lastFaces.offerFirst(face)
            if (lastFaces.size > FACE_CACHE_SIZE)
            {
                lastFaces.pollLast()
            }
        }

        changeErrorState(task, NO_ERROR)
        return face
    }

    private fun changeErrorState(task: LivenessDetectionTask, newErrorState: Int)
    {
        if (newErrorState != currentErrorState)
        {
            currentErrorState = newErrorState
            if (currentErrorState != NO_ERROR)
            {
                listener?.onTaskFailed(task, currentErrorState)
            }
        }
    }
}