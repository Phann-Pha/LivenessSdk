package com.pha.liveness.sdk.module.face_liveness

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.pha.liveness.face.liveness.sdk.CameraManager
import com.pha.liveness.face.liveness.sdk.core.analyzer.AnalyzeFaceValidator
import com.pha.liveness.face.liveness.sdk.core.util.DetectionConstance
import com.pha.liveness.face.liveness.sdk.task.FacingDetectionTask
import com.pha.liveness.face.liveness.sdk.task.LeftMoveDetectionTask
import com.pha.liveness.face.liveness.sdk.task.RightMoveDetectionTask
import com.pha.liveness.face.liveness.sdk.task.SmilingDetectionTask
import com.pha.liveness.face.liveness.sdk.util.CameraPermissionUtil
import com.pha.liveness.sdk.R
import com.pha.liveness.sdk.databinding.ActivityFaceLivenessBinding
import java.io.File

class FaceLivenessActivity : AppCompatActivity()
{
    private lateinit var activity: Activity
    private lateinit var binding: ActivityFaceLivenessBinding
    private lateinit var cameraManager: CameraManager
    
    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        activity = this
        enableEdgeToEdge()
        binding = DataBindingUtil.setContentView(activity, R.layout.activity_face_liveness)
        
        onInitCameraManager()
    }
    
    private var isCaptured = true
    private var fileMovement: File? = null
    
    @SuppressLint("SetTextI18n")
    private fun onInitCameraManager()
    {
        val liveFacingDetector = AnalyzeFaceValidator(FacingDetectionTask())
        val liveSmilingDetector = AnalyzeFaceValidator(FacingDetectionTask(), SmilingDetectionTask())
        val liveMovementDetector = AnalyzeFaceValidator(FacingDetectionTask(), LeftMoveDetectionTask(), RightMoveDetectionTask())
        
        cameraManager = CameraManager(activity, this, binding.preview, liveFacingDetector, onSuccessFacingLivenessDetector, onFailure)
        CameraPermissionUtil.checkCameraSelfPermission(activity) {
            cameraManager.start()
        }
    }
    
    @SuppressLint("SetTextI18n")
    private val onFailure: (code: Int) -> Unit = { code ->
        when (code)
        {
            AnalyzeFaceValidator.ERROR_MULTI_FACES -> binding.instruction.text = "Move closer"
            
            AnalyzeFaceValidator.ERROR_NO_FACE -> binding.instruction.text = "Move your face in the frame"
            
            AnalyzeFaceValidator.ERROR_OUT_OF_DETECTION_FRAME -> binding.instruction.text = "Move your face in center the frame"
            
            AnalyzeFaceValidator.IMAGE_QUALITY -> binding.instruction.text = "Reflection"
            
            AnalyzeFaceValidator.ERROR_COVER_FACE -> binding.instruction.text = "Somethings cover your face"
            
            AnalyzeFaceValidator.ANTI_SPOOF -> binding.instruction.text = "Recognize..."
            
            else -> binding.instruction.text = ""
        }
    }
    
    private val onSuccessFacingLivenessDetector: (task: String) -> Unit = { task ->
        when (task)
        {
            DetectionConstance.FACING_DETECTION ->
            {
                binding.progressingBar.visibility = View.VISIBLE
                binding.instruction.visibility = View.GONE
                cameraManager.onCaptureImage(activity) { _, _, file ->
                    cameraManager.pauseAnalyze()
                    binding.progressingBar.visibility = View.GONE
                    startActivity(ResultActivity.newInstance(activity, file.absolutePath))
                    finish()
                }
            }
        }
    }
    
    @SuppressLint("SetTextI18n")
    private val onSuccessSmilingLivenessDetector: (task: String) -> Unit = { task ->
        when (task)
        {
            DetectionConstance.FACING_DETECTION ->
            {
                binding.instruction.text = "Please Smile"
            }
            
            DetectionConstance.SMILE_DETECTION ->
            {
                binding.progressingBar.visibility = View.VISIBLE
                binding.instruction.visibility = View.GONE
                cameraManager.onCaptureImage(activity) { _, _, file ->
                    cameraManager.pauseAnalyze()
                    binding.progressingBar.visibility = View.GONE
                    startActivity(ResultActivity.newInstance(activity, file.absolutePath))
                    finish()
                }
            }
        }
    }
    
    @SuppressLint("SetTextI18n")
    private val onSuccessMovementLivenessDetector: (task: String) -> Unit = { task ->
        when (task)
        {
            DetectionConstance.FACING_DETECTION ->
            {
                binding.instruction.text = "Please Move Left"
                if (isCaptured)
                {
                    cameraManager.onCaptureImage(activity) { _, _, file ->
                        fileMovement = file
                        isCaptured = false
                    }
                }
            }
            
            DetectionConstance.MOVEMENT_LEFT_DETECTION ->
            {
                binding.instruction.text = "Please Move Right"
            }
            
            DetectionConstance.MOVEMENT_RIGHT_DETECTION ->
            {
                binding.progressingBar.visibility = View.VISIBLE
                binding.instruction.visibility = View.GONE
                
                cameraManager.pauseAnalyze()
                binding.progressingBar.visibility = View.GONE
                startActivity(ResultActivity.newInstance(activity, fileMovement?.absolutePath.toString()))
                finish()
            }
        }
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray)
    {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        CameraPermissionUtil.checkCameraGranted(activity, requestCode) {
            cameraManager.start()
        }
    }
}