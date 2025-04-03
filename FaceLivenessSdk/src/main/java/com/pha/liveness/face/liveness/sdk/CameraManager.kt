package com.pha.liveness.face.liveness.sdk

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import android.view.Surface
import androidx.annotation.OptIn
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.camera.view.TransformExperimental
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.pha.liveness.face.liveness.sdk.core.analyzer.AnalyzeFaceDelegator
import com.pha.liveness.face.liveness.sdk.core.analyzer.AnalyzeFaceValidator
import com.pha.liveness.face.liveness.sdk.core.listener.LivenessDetectionTask
import com.pha.liveness.face.liveness.sdk.util.BitmapUtils
import com.pha.liveness.face.liveness.sdk.util.CameraPermissionUtil
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraManager(private var context: Activity, private val lifecycleOwner: LifecycleOwner, private val viewFinder: PreviewView, private val liveDetector: AnalyzeFaceValidator, private val onSuccess: (task: String) -> Unit, private val onFailure: (code: Int) -> Unit)
{
    private var preview: Preview? = null
    private var camera: Camera? = null
    private var lensSelector = CameraSelector.LENS_FACING_FRONT
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null

    private var imageAnalyzer: ImageAnalysis? = null
    private var executorService: ExecutorService = Executors.newSingleThreadExecutor()

    /** func for stop camera and unbind all view*/
    fun pause()
    {
        cameraProvider?.unbindAll()
    }

    fun pauseAnalyze()
    {
        cameraProvider?.unbind(imageAnalyzer)
    }

    /** func for processing camera view */
    fun start()
    {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            preview = Preview.Builder()
                .setTargetRotation(Surface.ROTATION_0)
                .build()

            imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(executorService, analyzer()) }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build()

            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(lensSelector)
                .build()

            cameraConfigure(cameraProvider, cameraSelector, imageCapture, imageAnalyzer)
        }, ContextCompat.getMainExecutor(context))
    }

    /** func for changing face camera selector */
    fun selector()
    {
        cameraProvider?.unbindAll()
        lensSelector = if (lensSelector == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
        start()
    }

    private fun brightAdjustment()
    {
        val layout = context.window.attributes
        var oldBrightness = 126
        try
        {
            oldBrightness = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        }
        catch (e: Exception)
        {
            e.printStackTrace()
        }
        if (oldBrightness < 127) // 1 -> 255
        {
            layout.screenBrightness = 0.5f
        }
        context.window.attributes = layout
    }

    /** func for camera configuration like bind view */
    private fun cameraConfigure(provider: ProcessCameraProvider?, selector: CameraSelector, capture: ImageCapture?, analyzer: ImageAnalysis?)
    {
        try
        {
            provider?.unbindAll()
            camera?.cameraControl?.enableTorch(false)
            camera = provider?.bindToLifecycle(lifecycleOwner, selector, preview, capture, analyzer)
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
        return AnalyzeFaceDelegator(context, lensSelector, onDetectorListener())
    }

    private fun getExecutor(context: Context): Executor
    {
        return ContextCompat.getMainExecutor(context)
    }

    private fun onDetectorListener(): AnalyzeFaceValidator
    {
        val listener = object : AnalyzeFaceValidator.Listener
        {
            override fun onTaskStarted(task: LivenessDetectionTask)
            {
                brightAdjustment()
            }

            override fun onTaskCompleted(task: LivenessDetectionTask, isLastTask: Boolean)
            {
                onSuccess.invoke(task.taskType())
            }

            override fun onTaskFailed(task: LivenessDetectionTask, code: Int)
            {
                onFailure.invoke(code)
            }
        }
        return liveDetector.also { it.setListener(listener) }
    }

    fun onCaptureImage(context: Activity, name: String = "image", callback: (fileName: String, bitmap: Bitmap, file: File) -> Unit)
    {
        val metadata = ImageCapture.Metadata().also { it.isReversedHorizontal = CameraSelector.LENS_FACING_FRONT == lensSelector }
        val child = "/$name.jpg"
        val outputFileOptions = if (Build.VERSION.SDK_INT >= 29)
        {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            }
            ImageCapture.OutputFileOptions.Builder(context.contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                .setMetadata(metadata).build()
        }
        else
        {
            val outputFileDir = File(context.externalCacheDirs[0], child)
            ImageCapture.OutputFileOptions.Builder(outputFileDir).setMetadata(metadata).build()
        }

        imageCapture?.takePicture(outputFileOptions, getExecutor(context), object : ImageCapture.OnImageSavedCallback
        {
            override fun onImageSaved(output: ImageCapture.OutputFileResults)
            {
                try
                {
                    val mImageDir = CameraPermissionUtil.getImageDir(context, child)
                    if (!mImageDir.exists()) mImageDir.mkdirs()
                    val bitmap = BitmapUtils.getBitmapFromContentUri(context.contentResolver, Uri.fromFile(mImageDir))
                    bitmap?.let { image -> callback.invoke(name, image, mImageDir) }
                }
                catch (e: Exception)
                {
                    e.printStackTrace()
                }
            }

            override fun onError(e: ImageCaptureException)
            {
                e.printStackTrace()
            }
        })
    }
}


