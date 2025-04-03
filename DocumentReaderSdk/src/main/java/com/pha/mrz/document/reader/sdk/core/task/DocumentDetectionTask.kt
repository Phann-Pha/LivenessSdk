package com.pha.mrz.document.reader.sdk.core.task

import com.google.mlkit.vision.objects.DetectedObject
import com.pha.mrz.document.reader.sdk.core.model.Quadrilateral
import com.pha.mrz.document.reader.sdk.core.task.DocumentDetectionConstance
import org.opencv.core.Size

interface DocumentDetectionTask
{
    var isTaskCompleted: Boolean
    
    fun taskType(): String = DocumentDetectionConstance.DETECTION

    fun start()
    {

    }
    
    /** @return true if task completed */
    fun process(document: DetectedObject, quadrilateral: Quadrilateral, stdSize: Size, timestamp: Long): Boolean
}