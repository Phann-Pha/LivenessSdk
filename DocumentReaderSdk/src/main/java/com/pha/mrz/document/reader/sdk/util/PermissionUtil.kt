package com.pha.mrz.document.reader.sdk.util

import android.app.Activity
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

object PermissionUtil
{
    fun checkCameraSelfPermission(context: Activity, startCamera: () -> (Unit))
    {
        if (allPermissionsGranted(context))
        {
            startCamera()
        }
        else
        {
            ActivityCompat.requestPermissions(context, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
    }
    
    fun checkCameraGranted(context: Activity, requestCode: Int, startCamera: () -> (Unit))
    {
        if (requestCode == REQUEST_CODE_PERMISSIONS)
        {
            if (allPermissionsGranted(context))
            {
                startCamera()
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
    
    private const val REQUEST_CODE_PERMISSIONS = 10
    private val REQUIRED_PERMISSIONS = arrayOf(android.Manifest.permission.CAMERA)
}