package com.pha.mrz.document.reader.sdk

import android.app.Activity
import android.provider.Settings
import android.view.View
import android.view.View.MeasureSpec
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.camera.view.TransformExperimental
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.pha.mrz.document.reader.sdk.core.evaluate.DocumentDelegator
import com.pha.mrz.document.reader.sdk.core.evaluate.DocumentValidator
import com.pha.mrz.document.reader.sdk.core.task.DocumentDetectionTask
import com.pha.mrz.document.reader.sdk.util.view.ScanCanvasView
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class CameraManager(private val activity: Activity, private val lifecycleOwner: LifecycleOwner, private val liveDetector: DocumentValidator, private val viewFinder: PreviewView, private val instruction: TextView, private val reference: View, private val scanCanvasView: ScanCanvasView)
{
    private var preview: Preview? = null
    private var camera: Camera? = null
    private var selector = CameraSelector.LENS_FACING_BACK
    private var cameraProvider: ProcessCameraProvider? = null

    private var imageCapture: ImageCapture? = null
    private lateinit var imageAnalyzer: ImageAnalysis
    private lateinit var mPreviewSize: android.util.Size

    fun pause()
    {
        cameraProvider?.unbindAll()
    }

    fun start()
    {
        viewFinder.post {
            viewFinder.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED)
            mPreviewSize = android.util.Size(viewFinder.width, viewFinder.height)

            val cameraProviderFuture = ProcessCameraProvider.getInstance(activity)
            cameraProviderFuture.addListener(
                {
                    cameraProvider = cameraProviderFuture.get()
                    preview = Preview.Builder()
                        .setTargetResolution(mPreviewSize)
                        .build()

                    val aspectRatio: Float = mPreviewSize.width / mPreviewSize.height.toFloat()
                    val width = IMAGE_ANALYSIS_SCALE_WIDTH
                    val height = (width / aspectRatio).roundToInt()

                    imageAnalyzer = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setTargetResolution(android.util.Size(width, height))
                        .build()
                        .also { it.setAnalyzer(Executors.newSingleThreadExecutor(), analyzer()) }

                    val cameraSelector = CameraSelector.Builder()
                        .requireLensFacing(selector)
                        .build()

                    imageCapture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .build()

                    configuration(cameraProvider, cameraSelector)
                }, ContextCompat.getMainExecutor(activity)
            )
        }
    }

    private fun configuration(process: ProcessCameraProvider?, selector: CameraSelector)
    {
        try
        {
            process?.unbindAll()
            camera?.cameraControl?.enableTorch(false)
            camera = process?.bindToLifecycle(lifecycleOwner, selector, preview, imageAnalyzer, imageCapture)
            preview?.surfaceProvider = viewFinder.surfaceProvider
        }
        catch (e: Exception)
        {
            e.printStackTrace()
        }
    }

    @OptIn(TransformExperimental::class)
    private fun analyzer(): ImageAnalysis.Analyzer
    {
        return DocumentDelegator(buildLiveDetector(), selector, viewFinder, reference, scanCanvasView)
    }

    private fun buildLiveDetector(): DocumentValidator
    {
        val listener = object : DocumentValidator.Listener
        {
            override fun onTaskStarted(task: DocumentDetectionTask)
            {
                brightnessAdjustment()
            }

            override fun onTaskCompleted(task: DocumentDetectionTask, isLastTask: Boolean)
            {
                instruction.text = "Passed"
                pause()
            }

            override fun onTaskFailed(task: DocumentDetectionTask, code: Int)
            {
                when (code)
                {
                    DocumentValidator.ERROR_NO_DOCUMENT           ->
                    {
                        instruction.text = "Scan Document"
                    }

                    else                                          ->
                    {
                        instruction.text = ""
                    }
                }
            }
        }

        return liveDetector.also { it.setListener(listener) }
    }

    private fun brightnessAdjustment()
    {
        val layout = activity.window.attributes
        var oldBrightness = 126
        try
        {
            oldBrightness = Settings.System.getInt(activity.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        }
        catch (e: Exception)
        {
            e.printStackTrace()
        }
        if (oldBrightness < 127) // 1 -> 255
        {
            layout.screenBrightness = 0.5f
        }
        activity.window.attributes = layout
    }

    companion object
    {
        private const val IMAGE_ANALYSIS_SCALE_WIDTH = 480
    }
}