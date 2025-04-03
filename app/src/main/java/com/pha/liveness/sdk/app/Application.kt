package com.pha.liveness.sdk.app

import android.app.Application

class Application: Application()
{
    override fun onCreate()
    {
        super.onCreate()
        System.loadLibrary("opencv_java4")
    }
}