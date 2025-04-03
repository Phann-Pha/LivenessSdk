package com.pha.liveness.sdk.module.document

import android.app.Activity
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.pha.liveness.sdk.R
import com.pha.liveness.sdk.databinding.ActivityDocumentReaderBinding
import com.pha.mrz.document.reader.sdk.CameraManager
import com.pha.mrz.document.reader.sdk.core.evaluate.DocumentValidator
import com.pha.mrz.document.reader.sdk.core.task.ShapeDetectionTask
import com.pha.mrz.document.reader.sdk.util.PermissionUtil

class DocumentReaderActivity : AppCompatActivity()
{
    private lateinit var activity: Activity
    private lateinit var binding: ActivityDocumentReaderBinding
    
    private lateinit var cameraManager: CameraManager
    private val liveDetector = DocumentValidator(ShapeDetectionTask())
    
    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        activity = this
        binding = DataBindingUtil.setContentView(activity, R.layout.activity_document_reader)
        
        onInitCameraManager()
        onInitEventClickListener()
    }
    
    private fun onInitCameraManager()
    {
        cameraManager = CameraManager(activity, this, liveDetector, binding.viewFinder, binding.instruction, binding.shapeOverlay, binding.scanCanvasView)
        PermissionUtil.checkCameraSelfPermission(activity) { cameraManager.start() }
    }
    
    private fun onInitEventClickListener()
    {
        binding.instruction.setOnClickListener {
            cameraManager.start()
            liveDetector.reset()
        }
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray, deviceId: Int)
    {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults, deviceId)
        PermissionUtil.checkCameraGranted(activity, requestCode) {
            cameraManager.start()
        }
    }
}