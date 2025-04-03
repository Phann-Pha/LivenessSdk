package com.pha.liveness.sdk

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.pha.liveness.face.liveness.sdk.util.BitmapUtils
import com.pha.liveness.sdk.databinding.ActivityResultBinding
import java.io.File

class ResultActivity : AppCompatActivity()
{
    private lateinit var activity: Activity
    private lateinit var binding: ActivityResultBinding

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        activity = this
        enableEdgeToEdge()
        binding = DataBindingUtil.setContentView(activity, R.layout.activity_result)

        onInitIntentObject()
        onInitEventClickListener()
    }

    private fun onInitIntentObject()
    {
        try
        {
            val path = intent.extras?.getString(RESULT).toString()
            val file = File(path)
            val bitmap = BitmapUtils.getBitmapFromContentUri(activity.contentResolver, Uri.fromFile(file))
            if (bitmap != null)
            {
                binding.preview.setImageBitmap(bitmap)
                file.delete()
            }
        }
        catch (e: Exception)
        {
            e.printStackTrace()
        }
    }

    private fun onInitEventClickListener()
    {
        binding.btnRetry.setOnClickListener {
            startActivity(Intent(activity, MainActivity::class.java))
            finish()
        }
    }

    companion object
    {
        private const val RESULT = "result"

        fun newInstance(activity: Activity, result: String): Intent
        {
            return Intent(activity, ResultActivity::class.java).apply {
                putExtra(RESULT, result)
            }
        }
    }
}