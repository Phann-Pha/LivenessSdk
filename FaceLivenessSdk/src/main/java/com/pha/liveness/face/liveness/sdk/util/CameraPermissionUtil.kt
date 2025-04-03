package com.pha.liveness.face.liveness.sdk.util

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.channels.FileChannel

object CameraPermissionUtil
{
    private const val REQUEST_CODE_PERMISSIONS = 10
    private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)

    fun checkCameraSelfPermission(context: Activity, start: () -> (Unit) = {})
    {
        if (allPermissionsGranted(context))
        {
            start()
        }
        else
        {
            ActivityCompat.requestPermissions(context, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
    }

    fun checkCameraGranted(context: Activity, requestCode: Int, start: () -> (Unit) = {})
    {
        if (requestCode == REQUEST_CODE_PERMISSIONS)
        {
            if (allPermissionsGranted(context))
            {
                start()
            }
            else
            {
                Toast.makeText(context, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show()
                context.finish()
            }
        }
    }

    private fun allPermissionsGranted(context: Activity) = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    fun getImageDir(context: Activity, child: String): File
    {
        return if (Build.VERSION.SDK_INT >= 29)
        {
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), child)
        }
        else
        {
            moveImageFile(
                File(context.externalCacheDirs[0], child),
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), child)
            )
            val fileDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), child)
            if (!fileDir.exists()) fileDir.mkdirs()
            fileDir
        }
    }

    private fun moveImageFile(source: File, destination: File)
    {
        try
        {
            if (!source.exists()) return
            val old: FileChannel? = FileInputStream(source).channel
            val new: FileChannel? = FileOutputStream(destination).channel
            if (new != null && old != null) new.transferFrom(old, 0, old.size())
            old?.close()
            new?.close()
        }
        catch (ex: Exception)
        {
            ex.printStackTrace()
        }
    }
}