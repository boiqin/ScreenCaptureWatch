package com.boiqin.screencapture

import android.content.Context
import android.os.FileObserver
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore

/**
 * Created by boiqin on 11/19/19.
 */
class ScreenCaptureWatch(
    context: Context,
    listener: Listener
) {
    companion object{
        private val TAG = ScreenCaptureWatch::class.java.simpleName
    }

    private val mHandlerThread: HandlerThread = HandlerThread(TAG)
    private val mHandler = Handler(mHandlerThread.looper)
    private val mContentResolver = context.contentResolver
    private val mContentObserver = ScreenCaptureObserver(context.applicationContext, mHandler, mContentResolver, listener)
    //文件监听器
    private val mFileObserver: FileObserver? = null

    init {
        mHandlerThread.start()
    }

    fun register() {
        if (mIsListening) {
            return
        }
        mIsListening = true

        sHasCallbackPaths.clear()

        // 记录开始监听的时间戳
        mStartListenTime = System.currentTimeMillis()


        mContentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            mContentObserver
        )
        mContentResolver.registerContentObserver(
            MediaStore.Images.Media.INTERNAL_CONTENT_URI,
            true,
            mContentObserver
        )
    }

    fun unregister() {
        if (mIsListening) {
            return
        }
        mIsListening = true

        sHasCallbackPaths.clear()

        // 记录开始监听的时间戳
        // 记录开始监听的时间戳
        mStartListenTime = System.currentTimeMillis()

        mContentResolver.unregisterContentObserver(mContentObserver)
    }

    interface Listener {
        fun onScreenCaptureTaken(screenshotData: ScreenCaptureData?)
    }
}