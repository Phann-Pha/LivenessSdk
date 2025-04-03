package com.pha.liveness.face.liveness.sdk.core.interpreter

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import com.pha.liveness.face.liveness.sdk.core.interpreter.model.OcclusionResultModel
import com.google.mlkit.vision.face.Face
import com.pha.liveness.face.liveness.sdk.BuildConfig
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicBoolean

class FaceOcclusionDetector(private var context: Context) : AutoCloseable
{
    private val mapping = mapOf(
        HAND_OVER_FACE_INDEX to "hand_over_face",
        NORMAL_INDEX to "normal",
        WITH_MASK_INDEX to "with_mask"
    )

    private var ortSession: OrtSession? = null
    private val ortEnvironment = OrtEnvironment.getEnvironment()

    private val isModelLoaded = AtomicBoolean(false) // Flag to track if model loaded successfully

    private val resizedBitmap = Bitmap.createBitmap(IMAGE_SIZE, IMAGE_SIZE, Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(resizedBitmap)
    private val paint = Paint().apply { isFilterBitmap = true }

    init
    {
        onLoadModel()
    }

    private fun onLoadModel()
    {
        try
        {
            val byte = context.assets.open(BuildConfig.occlusion).readBytes()
            ortSession = ortEnvironment.createSession(byte, OrtSession.SessionOptions())
            isModelLoaded.set(true)
        }
        catch (e: Exception)
        {
            isModelLoaded.set(false)
        }
    }

    fun onValidationOcclusion(bitmap: Bitmap?, face: Face): OcclusionResultModel
    {
        return try
        {
            if (bitmap != null)
            {
                val bound = generateFace(face)?.boundingBox ?: Rect()
                val bmp = bitmap.crop(bound.left, bound.top, bound.width(), bound.height())
                onMaskValidation(bmp)
            }
            else
            {
                OcclusionResultModel("normal", 0.7f)
            }
        }
        catch (e: Exception)
        {
            OcclusionResultModel("normal", 0.7f)
        }
    }

    /**
     * Detect if face is occluded by mask or hand
     *
     * @param bitmap type as
     * @return OcclusionResultModel containing class name and confidence
     */
    private fun onMaskValidation(bitmap: Bitmap): OcclusionResultModel
    {
        if (bitmap.isRecycled)
        {
            throw Exception("detectFaceMask: Cannot process recycled bitmap")
        }

        /** If model failed to load, return normal with low confidence,
         *  this allows the pipeline to continue instead of failing
         * */
        if (!isModelLoaded.get() || ortSession == null)
        {
            return OcclusionResultModel("normal", 0.7f)
        }

        var inputTensor: OnnxTensor? = null

        try
        {
            if (bitmap.width != IMAGE_SIZE || bitmap.height != IMAGE_SIZE) // adapting size
            {
                canvas.drawBitmap(bitmap, Rect(0, 0, bitmap.width, bitmap.height), Rect(0, 0, IMAGE_SIZE, IMAGE_SIZE), paint)
            }
            else
            {
                canvas.drawBitmap(bitmap, 0f, 0f, null)
            }

            // create input tensor
            inputTensor = onImagePreprocessing(resizedBitmap)

            val session = ortSession ?: throw Exception("Session is null")
            val inputName = session.inputNames.iterator().next()
            val result = session.run(mapOf(inputName to inputTensor))
            val output = result[0].value

            val probabilities = output as? Array<*> ?: throw Exception("Invalid output format")
            val floatArray = probabilities[0] as? FloatArray ?: throw Exception("Invalid array format")

            // get highest probability class and confidence
            val maxEntry = floatArray.withIndex().maxByOrNull { it.value } ?: throw Exception("No max probability found")

            val (maxIndex, maxProb) = maxEntry

            /** Apply the custom condition:
             * If predicted class is "normal" but confidence < threshold,
             * choose either "with_mask" or "hand_over_face" based on highest probability
             */
            if (maxIndex == NORMAL_INDEX && maxProb < NORMAL_CONFIDENCE_THRESHOLD)
            {
                val maskProb = floatArray[WITH_MASK_INDEX]
                val handOverFaceProb = floatArray[HAND_OVER_FACE_INDEX]

                return if (maskProb > handOverFaceProb)
                {
                    OcclusionResultModel("with_mask", maskProb)
                }
                else
                {
                    OcclusionResultModel("hand_over_face", handOverFaceProb)
                }
            }

            // standard case, return the highest probability class
            return OcclusionResultModel(mapping[maxIndex] ?: "unknown", maxProb)
        }
        catch (e: Exception)
        {
            throw Exception("Error during occlusion detection: ${e.message}", e)
        }
        finally
        {
            inputTensor?.close()
        }
    }

    /**
     * @param bitmap Image to preprocess
     * @return OnnxTensor ready for inference
     */
    private fun onImagePreprocessing(bitmap: Bitmap): OnnxTensor
    {
        val floatBuffer = FloatBuffer.allocate(1 * 3 * IMAGE_SIZE * IMAGE_SIZE)
        val pixels = IntArray(IMAGE_SIZE * IMAGE_SIZE).also { bitmap.getPixels(it, 0, IMAGE_SIZE, 0, 0, IMAGE_SIZE, IMAGE_SIZE) }

        // Process in NCHW format (batch, channels, height, width)
        for (c in 0 until 3)
        {
            for (h in 0 until IMAGE_SIZE)
            {
                for (w in 0 until IMAGE_SIZE)
                {
                    val pixel = pixels[h * IMAGE_SIZE + w]
                    val value = when (c)
                    {
                        0    -> ((pixel shr 16) and 0xFF) / 255.0f // R
                        1    -> ((pixel shr 8) and 0xFF) / 255.0f  // G
                        2    -> (pixel and 0xFF) / 255.0f          // B
                        else -> 0.0f
                    }
                    floatBuffer.put(value)
                }
            }
        }

        floatBuffer.rewind()
        return OnnxTensor.createTensor(ortEnvironment, floatBuffer, longArrayOf(1, 3, IMAGE_SIZE.toLong(), IMAGE_SIZE.toLong()))
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

    override fun close()
    {
        try
        {
            ortSession?.close()
            ortSession = null
        }
        catch (e: Exception)
        {
            e.printStackTrace()
        }
    }

    companion object
    {
        private const val IMAGE_SIZE = 224
        private const val HAND_OVER_FACE_INDEX = 0
        private const val NORMAL_INDEX = 1
        private const val WITH_MASK_INDEX = 2
        private const val NORMAL_CONFIDENCE_THRESHOLD = 0.7f
    }
}