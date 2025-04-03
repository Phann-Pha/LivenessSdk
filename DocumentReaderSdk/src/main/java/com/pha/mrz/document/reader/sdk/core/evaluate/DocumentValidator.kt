package com.pha.mrz.document.reader.sdk.core.evaluate

import com.google.mlkit.vision.objects.DetectedObject
import com.pha.mrz.document.reader.sdk.core.model.Quadrilateral
import com.pha.mrz.document.reader.sdk.core.task.DocumentDetectionTask
import org.opencv.core.Size
import java.util.Deque
import java.util.LinkedList

class DocumentValidator(vararg tasks: DocumentDetectionTask)
{
    companion object
    {
        private const val DOCUMENT_CACHE_SIZE = 5
        private const val NO_ERROR = -1
        const val ERROR_NO_DOCUMENT = 0
    }

    init
    {
        check(tasks.isNotEmpty()) { "no tasks" }
    }

    private val tasks = tasks.asList()
    private var taskIndex = 0
    private var lastTaskIndex = -1
    private var currentErrorState = NO_ERROR
    private val lastDocuments: Deque<DetectedObject> = LinkedList()
    private var listener: Listener? = null

    fun process(documents: List<DetectedObject>?, quadrilateral: Quadrilateral, previewSize: Size, timestamp: Long)
    {
        val task = tasks.getOrNull(taskIndex) ?: return
        if (taskIndex != lastTaskIndex)
        {
            lastTaskIndex = taskIndex
            task.start()
            listener?.onTaskStarted(task)
        }

        val document = filter(task, documents) ?: return
        if (task.process(document, quadrilateral , previewSize, timestamp))
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

    private fun getTasks(): List<DocumentDetectionTask>
    {
        return this.tasks
    }

    fun reset()
    {
        taskIndex = 0
        lastTaskIndex = -1
        lastDocuments.clear()
        getTasks().forEach { it.isTaskCompleted = false }
    }

    private fun filter(task: DocumentDetectionTask, documents: List<DetectedObject>?): DetectedObject?
    {
        if (documents.isNullOrEmpty() && lastDocuments.isEmpty())
        {
            changeErrorState(task, ERROR_NO_DOCUMENT)
            reset()
            return null
        }

        val document = documents?.firstOrNull() ?: lastDocuments.pollFirst()
        if (!documents.isNullOrEmpty())
        {
            lastDocuments.offerFirst(document)
            if (lastDocuments.size > DOCUMENT_CACHE_SIZE)
            {
                lastDocuments.pollLast()
            }
        }

        changeErrorState(task, NO_ERROR)
        return document
    }

    private fun changeErrorState(task: DocumentDetectionTask, newErrorState: Int)
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

    interface Listener
    {
        fun onTaskStarted(task: DocumentDetectionTask)

        fun onTaskCompleted(task: DocumentDetectionTask, isLastTask: Boolean)

        fun onTaskFailed(task: DocumentDetectionTask, code: Int)
    }
}