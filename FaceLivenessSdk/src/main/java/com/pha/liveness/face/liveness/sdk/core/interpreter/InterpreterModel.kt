package com.pha.liveness.face.liveness.sdk.core.interpreter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.face.Face
import com.pha.liveness.face.liveness.sdk.BuildConfig
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.image.ops.ResizeWithCropOrPadOp
import org.tensorflow.lite.support.label.TensorLabel
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer

object InterpreterModel
{
    private const val LABEL_MASK = "mask"
    private const val LABEL_NO_MASK = "no_mask"

    private const val IMAGE_MEAN = 127.5f
    private const val IMAGE_STD = 127.5f

    fun isMaskDetected(context: Context, bitmap: Bitmap?, face: Face): Boolean
    {
        var validator = false
        try
        {
            if (bitmap != null)
            {
                val bound = generateFace(face)?.boundingBox ?: Rect()
                val bmp = bitmap.crop(bound.left, bound.top, bound.width(), bound.height())
                val label = recognize(context, bmp)
                val mask = label[LABEL_MASK] ?: 0f
                val noMask = label[LABEL_NO_MASK] ?: 0f
                validator = mask > noMask
            }
        }
        catch (e: Exception)
        {
            e.printStackTrace()
        }

        return validator
    }

    private fun recognize(context: Context, bitmap: Bitmap): MutableMap<String, Float>
    {
        val modelFile = FileUtil.loadMappedFile(context, BuildConfig.mask_detector)
        val model = Interpreter(modelFile, Interpreter.Options())
        val labels = FileUtil.loadLabels(context, BuildConfig.mask_label)

        val imageDataType = model.getInputTensor(0).dataType()
        val inputShape = model.getInputTensor(0).shape()

        val outputDataType = model.getOutputTensor(0).dataType()
        val outputShape = model.getOutputTensor(0).shape()

        var inputImageBuffer = TensorImage(imageDataType)
        val outputBuffer = TensorBuffer.createFixedSize(outputShape, outputDataType)

        val cropSize = kotlin.math.min(bitmap.width, bitmap.height)
        val imageProcessor = ImageProcessor.Builder()
            .add(ResizeWithCropOrPadOp(cropSize, cropSize))
            .add(ResizeOp(inputShape[1], inputShape[2], ResizeOp.ResizeMethod.NEAREST_NEIGHBOR))
            .add(NormalizeOp(IMAGE_MEAN, IMAGE_STD))
            .build()

        inputImageBuffer.load(bitmap)
        inputImageBuffer = imageProcessor.process(inputImageBuffer)

        model.run(inputImageBuffer.buffer, outputBuffer.buffer.rewind())

        val labelOutput = TensorLabel(labels, outputBuffer)

        return labelOutput.mapWithFloatValue
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
}