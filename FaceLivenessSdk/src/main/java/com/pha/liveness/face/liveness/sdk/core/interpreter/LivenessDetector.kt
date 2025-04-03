package com.pha.liveness.face.liveness.sdk.core.interpreter

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import com.pha.liveness.face.liveness.sdk.core.interpreter.model.LiveResultModel
import com.google.mlkit.vision.face.Face
import com.pha.liveness.face.liveness.sdk.BuildConfig
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.exp

/** Handles face live detection using ONNX runtime */
class LivenessDetector(private val context: Context) : AutoCloseable
{
    // ImageNet normalization values
    private val mean = floatArrayOf(0.485f, 0.456f, 0.406f)
    private val std = floatArrayOf(0.229f, 0.224f, 0.225f)

    // Initialize ONNX environment and session once
    private val ortEnv = OrtEnvironment.getEnvironment()
    private lateinit var session: OrtSession
    private lateinit var inputName: String

    // Pre-allocated buffers for better performance
    private val inputBuffer = ByteBuffer
        .allocateDirect(BATCH_SIZE * CHANNELS * INPUT_SIZE * INPUT_SIZE * 4)
        .apply { order(ByteOrder.nativeOrder()) }
    private val floatArray = FloatArray(BATCH_SIZE * CHANNELS * INPUT_SIZE * INPUT_SIZE)

    // Reusable bitmap for resizing
    private val resizedBitmap = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(resizedBitmap)
    private val paint = Paint().apply { isFilterBitmap = true }

    init
    {
        onInitializeSessionLiveDetector()
    }

    private fun onInitializeSessionLiveDetector()
    {
        try
        {
            val sessionOptions = OrtSession.SessionOptions()
            val byte = context.assets.open(BuildConfig.liveness).readBytes()
            session = ortEnv.createSession(byte, sessionOptions)
            inputName = session.inputNames.iterator().next()
        }
        catch (e: Exception)
        {
            e.printStackTrace()
        }
    }

    /**
     * Run face live detector on the provided bitmap
     *
     * @param bitmap The bitmap to analyze (should be a cropped face)
     * @return DetectionResult containing the result label ("Live" or "Spoof") and confidence
     */

    fun isLive(bitmap: Bitmap?, face: Face): LiveResultModel
    {
        return try
        {
            if (bitmap != null)
            {
                val bound = generateFace(face)?.boundingBox ?: Rect()
                val bmp = bitmap.crop(bound.left, bound.top, bound.width(), bound.height())
                onProcessLive(bmp)
            }
            else
            {
                LiveResultModel("spoof", 0.5f)
            }
        }
        catch (e: Exception)
        {
            LiveResultModel("spoof", 0.5f)
        }
    }

    private fun onProcessLive(bitmap: Bitmap): LiveResultModel
    {
        if (bitmap.isRecycled) throw Exception("Cannot process recycled bitmap")

        preprocessImage(bitmap) // image processing

        // Convert to proper format for OnnxTensor creation
        inputBuffer.asFloatBuffer().get(floatArray)
        val shape = longArrayOf(BATCH_SIZE.toLong(), CHANNELS.toLong(), INPUT_SIZE.toLong(), INPUT_SIZE.toLong())

        var inputTensor: OnnxTensor? = null

        try
        {
            // Create input tensor
            inputTensor = OnnxTensor.createTensor(ortEnv, FloatBuffer.wrap(floatArray), shape)

            // Run model inference
            val output = session.run(mapOf(inputName to inputTensor))
            val outputTensor = output[0] as OnnxTensor

            // Extract value
            val extra = when (val outputValue = outputTensor.value)
            {
                is FloatArray -> outputValue[0]
                is Array<*>   -> (outputValue[0] as FloatArray)[0]
                else          -> throw Exception("Unexpected output type: ${outputValue?.javaClass?.name}")
            }

            // Apply sigmoid to get confidence score
            val conf = 1.0f / (1.0f + exp(-extra))

            // Apply threshold for classification
            val label = if (conf > LIVE_THRESHOLD) "live" else "spoof"

            // Adjust confidence display (showing confidence in the prediction)
            val displayConf = if (label == "live") conf else 1f - conf

            return LiveResultModel(label, displayConf)
        }
        catch (e: Exception)
        {
            throw Exception("Error during live detection: ${e.message}", e)
        }
        finally
        {
            // Cleanup resources
            inputTensor?.close()

            // Reset buffer for next use
            inputBuffer.rewind()
        }
    }

    /**
     * Preprocess the bitmap for model input - matches Python's preprocessing pipeline
     * Optimized to reuse buffers and minimize allocations
     *
     * @param bitmap The input bitmap to preprocess
     */
    private fun preprocessImage(bitmap: Bitmap)
    {
        // Clear the buffer before reuse
        inputBuffer.rewind()

        // Resize the bitmap if needed
        val sourceBitmap = if (bitmap.width != INPUT_SIZE || bitmap.height != INPUT_SIZE)
        {
            canvas.drawBitmap(bitmap, Rect(0, 0, bitmap.width, bitmap.height), Rect(0, 0, INPUT_SIZE, INPUT_SIZE), paint)
            resizedBitmap
        }
        else
        {
            bitmap
        }

        // Get pixel values from the bitmap
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        sourceBitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        // Process in NCHW format (batch, channels, height, width)
        // All RED channel values, then all GREEN channel values, then all BLUE channel values

        for (i in 0 until INPUT_SIZE * INPUT_SIZE) // Process RED channel
        {
            val pixel = pixels[i]
            val r = ((pixel shr 16) and 0xFF) / 255.0f  // Normalize to [0,1]
            inputBuffer.putFloat((r - mean[0]) / std[0])     // Apply ImageNet normalization
        }

        for (i in 0 until INPUT_SIZE * INPUT_SIZE) // Process GREEN channel
        {
            val pixel = pixels[i]
            val g = ((pixel shr 8) and 0xFF) / 255.0f
            inputBuffer.putFloat((g - mean[1]) / std[1])
        }

        for (i in 0 until INPUT_SIZE * INPUT_SIZE)  // Process BLUE channel
        {
            val pixel = pixels[i]
            val b = (pixel and 0xFF) / 255.0f
            inputBuffer.putFloat((b - mean[2]) / std[2])
        }

        inputBuffer.rewind()
    }

    private fun generateFace(face: Face): Face?
    {
        return try
        {
            var finalFace: Face? = null
            val finalFaceSize = 0
            val faceSize = face.boundingBox.height() * face.boundingBox.width()
            if (faceSize > finalFaceSize)
            {
                finalFace = face
            }
            finalFace
        }
        catch (e: Exception)
        {
            e.printStackTrace()
            null
        }
    }

    private fun Bitmap.crop(left: Int, top: Int, width: Int, height: Int): Bitmap
    {
        return Bitmap.createBitmap(this, left, top, width, height)
    }


    /** Close and clean up resources */
    override fun close()
    {
        try
        {
            session.close()
        }
        catch (e: Exception)
        {
            e.printStackTrace()
        }
    }

    companion object
    {
        private const val INPUT_SIZE = 224
        private const val BATCH_SIZE = 1
        private const val CHANNELS = 3
        private const val LIVE_THRESHOLD = 0.5f
    }
}