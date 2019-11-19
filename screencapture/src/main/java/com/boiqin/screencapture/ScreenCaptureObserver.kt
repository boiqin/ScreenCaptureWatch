package com.boiqin.screencapture

import android.content.ContentResolver
import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.graphics.BitmapFactory
import android.graphics.Point
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.provider.MediaStore
import android.text.TextUtils
import android.util.Log
import android.view.WindowManager
import java.util.*


/**
 * Created by boiqin on 11/19/19.
 */
class ScreenCaptureObserver(
    private val context: Context,
    handler: Handler,
    private val mContentResolver: ContentResolver,
    private val mListener: ScreenCaptureWatch.Listener
) : ContentObserver(handler) {
    companion object{
        private val TAG = ScreenCaptureObserver::class.java.simpleName
        private val MEDIA_PROJECTIONS = arrayOf(MediaStore.Images.ImageColumns.DATA,
            MediaStore.Images.ImageColumns.DATE_TAKEN,
            MediaStore.Images.ImageColumns.WIDTH,
            MediaStore.Images.ImageColumns.HEIGHT)
//            MediaStore.Images.Media._ID,
//            MediaStore.Images.Media.DISPLAY_NAME,
//            MediaStore.Images.Media.DATA)
        private val MEDIA_EXTERNAL_URI_STRING =
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI.toString()
        private const val FILE_NAME_PREFIX = "screenshot"
        private const val PATH_SCREENSHOT = "screenshots/"

        /**
         * 截屏依据中的路径判断关键字
         */
        private val KEYWORDS = arrayOf(
            "screenshot", "screen_shot", "screen-shot", "screen shot",
            "screencapture", "screen_capture", "screen-capture", "screen capture",
            "screencap", "screen_cap", "screen-cap", "screen cap", "截屏"
        )
    }

    /**
     * 已回调过的路径
     */
    private val sHasCallbackPaths: ArrayList<String> = ArrayList()
    private val mStartListenTime: Long = 0


    override fun onChange(selfChange: Boolean, uri: Uri) {
        super.onChange(selfChange, uri)
        handleMediaContentChange(uri)
    }

    /**
     * 处理媒体数据库的内容改变
     */
    private fun handleMediaContentChange(uri: Uri) {
        var cursor: Cursor? = null
        try { // 数据改变时查询数据库中最后加入的一条数据
            cursor = mContentResolver.query(uri,
                MEDIA_PROJECTIONS,
                null,
                null,
                MediaStore.Images.ImageColumns.DATE_ADDED + " desc limit 1"
            )
            if (cursor == null) {
                return
            }
            if (!cursor.moveToFirst()) {
                return
            }
            // 获取各列的索引
            val dataIndex = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA)
            val dateTakenIndex =
                cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATE_TAKEN)
            val widthIndex = cursor.getColumnIndex(MediaStore.Images.ImageColumns.WIDTH)
            val heightIndex = cursor.getColumnIndex(MediaStore.Images.ImageColumns.HEIGHT)

            // 获取行数据
            val data = cursor.getString(dataIndex)
            val dateTaken = cursor.getLong(dateTakenIndex)
            val width = cursor.getInt(widthIndex)
            val height = cursor.getInt(heightIndex)
            // 处理获取到的第一行数据
            handleMediaRowData(data, dateTaken, width, height)
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        } finally {
            if (cursor != null && !cursor.isClosed) {
                cursor.close()
            }
        }
    }

    /**
     * 处理获取到的一行数据
     */
    private fun handleMediaRowData(
        data: String,
        dateTaken: Long,
        width: Int,
        height: Int
    ) {
        if (checkScreenShot(data, dateTaken, width, height)) {
            if (!checkCallback(data)) {
                ThreadPools.get().execute(Runnable {
                    var tryCount = 2
                    while (tryCount > 0) {
                        try {
                            Thread.sleep(600)
                            val options = BitmapFactory.Options()
                            options.inJustDecodeBounds = true
                            BitmapFactory.decodeFile(data, options)
                            if (options.outWidth > 0 || options.outHeight > 0) {
                                notifyOnShot(data)
                                break
                            }
                        } catch (e: Exception) {
                            if(BuildConfig.DEBUG) {
                                Log.e(TAG, e.message?:e.toString())
                            }
                        }
                        tryCount--
                    }
                })
            }
        } else { // 如果在观察区间媒体数据库有数据改变，又不符合截屏规则，则输出到 log 待分析
            if(BuildConfig.DEBUG) {
                Log.e(
                    TAG,
                    "Media content changed, but not screenshot: path = " + data
                            + "; size = " + width + " * " + height + "; date = " + dateTaken
                )
            }
        }
    }

    /**
     * 判断是否已回调过, 某些手机ROM截屏一次会发出多次内容改变的通知; <br></br>
     * 删除一个图片也会发通知, 同时防止删除图片时误将上一张符合截屏规则的图片当做是当前截屏.
     */
    private fun checkCallback(imagePath: String): Boolean {
        if (sHasCallbackPaths.contains(imagePath)) {
            return true
        }
        // 大概缓存15~20条记录便可
        if (sHasCallbackPaths.size >= 20) {
            sHasCallbackPaths.filterIndexed { index, _ ->
                index >= 5 }
        }
        sHasCallbackPaths.add(imagePath)
        return false
    }

    /**
     * 判断指定的数据行是否符合截屏条件
     */
    private fun checkScreenShot(
        data: String,
        dateTaken: Long,
        width: Int,
        height: Int
    ): Boolean {
        /*
         * 判断依据一: 时间判断
         */
        // 如果加入数据库的时间在开始监听之前, 或者与当前时间相差大于10秒, 则认为当前没有截屏
        var data = data
        if (dateTaken < mStartListenTime || System.currentTimeMillis() - dateTaken > 10 * 1000) {
            return false
        }
        /*
         * 判断依据二: 尺寸判断
         */
        val screenSize = getScreenSize()
        if (screenSize != null) { // 如果图片尺寸超出屏幕, 则认为当前没有截屏
            if (!(width <= screenSize.x && height <= screenSize.y || height <= screenSize.x && width <= screenSize.y)) {
                return false
            }
        }
        /*
         * 判断依据三: 路径判断
         */
        if (TextUtils.isEmpty(data)) {
            return false
        }
        data = data.toLowerCase()
        // 判断图片路径是否含有指定的关键字之一, 如果有, 则认为当前截屏了
        for (keyWork in KEYWORDS) {
            if (data.contains(keyWork)) {
                return true
            }
        }
        return false
    }

    /**
     * 获取屏幕分辨率
     */
    private fun getScreenSize(): Point? {
        val screenSize = Point()
        try {
            val wm =
                context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val defaultDisplay = wm.defaultDisplay
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                defaultDisplay.getRealSize(screenSize)
            } else {
                screenSize.x = wm.defaultDisplay.width
                screenSize.y = wm.defaultDisplay.height
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return screenSize
    }

}