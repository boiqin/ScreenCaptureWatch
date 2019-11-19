package com.boiqin.screencapture;

import android.app.Activity;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.Display;
import android.view.Window;
import android.view.WindowManager;

import com.pingan.core.base.Debuger;
import com.pingan.core.base.GoModule;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by chenbo439 on 17/5/17.
 */

public class ScreenShotHelper {

    private ScreenShotHelper() throws ScreenShotException {
        throw new ScreenShotException("forbid new instance error");
    }

    public static ScreenShotListenManager getScreenShotListenManager() {
        return ScreenShotListenManager.instance;
    }


    public static class ScreenShotListenManager {

        private static final String TAG = "ScreenShotListenManager";


        private static ScreenShotListenManager instance = new ScreenShotListenManager(GoModule.getContext());
        private static Point sScreenRealSize;
        /**
         * 已回调过的路径
         */
        private final List<String> sHasCallbackPaths = new ArrayList<String>();
        /**
         * 运行在 UI 线程的 Handler, 用于运行监听器回调
         */
        private final Handler mUiHandler = new Handler(Looper.getMainLooper());
        private boolean mIsListening = false;
        private Context mContext;
        private long mStartListenTime;
        /**
         * 内部存储器内容观察者
         */
        private MediaContentObserver mInternalObserver;
        /**
         * 外部存储器内容观察者
         */
        private MediaContentObserver mExternalObserver;

        private ScreenShotListenManager(Context context) {
            if (context == null) {
                throw new IllegalArgumentException("The context must not be null.");
            }
            mContext = context;

            // 获取屏幕真实的分辨率
            if (sScreenRealSize == null) {
                sScreenRealSize = getRealScreenSize();
                if (sScreenRealSize != null) {
                    Debuger.logD(TAG, "Screen Real Size: " + sScreenRealSize.x + " * " + sScreenRealSize.y);
                } else {
                    Debuger.logW(TAG, "Get screen real size failed.");
                }
            }
        }

        private static void assertInMainThread() {
            if (Looper.myLooper() != Looper.getMainLooper()) {
                StackTraceElement[] elements = Thread.currentThread().getStackTrace();
                String methodMsg = null;
                if (elements != null && elements.length >= 4) {
                    methodMsg = elements[3].toString();
                }
                throw new IllegalStateException("Call the method must be in main thread: " + methodMsg);
            }
        }

        /**
         * 启动监听
         */
        public void startListen() {

            assertInMainThread();
            if (mIsListening) {
                return;
            }
            mIsListening = true;

            sHasCallbackPaths.clear();

            // 记录开始监听的时间戳
            mStartListenTime = System.currentTimeMillis();

            // 创建内容观察者
            mInternalObserver = new MediaContentObserver(MediaStore.Images.Media.INTERNAL_CONTENT_URI, mUiHandler);
            mExternalObserver = new MediaContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, mUiHandler);

            // 注册内容观察者
            if (mContext == null || mContext.getContentResolver() == null) {
                return;
            }
            try {
                mContext.getContentResolver().registerContentObserver(
                        MediaStore.Images.Media.INTERNAL_CONTENT_URI,
                        true,
                        mInternalObserver
                );
                mContext.getContentResolver().registerContentObserver(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        true,
                        mExternalObserver
                );
            } catch (Exception e) {
                Debuger.logE(e.getMessage());
            }

        }

        /**
         * 停止监听
         */
        public void stopListen() {

            mIsListening = false;

            assertInMainThread();

            // 注销内容观察者
            if (mInternalObserver != null) {
                try {
                    mContext.getContentResolver().unregisterContentObserver(mInternalObserver);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                mInternalObserver = null;
            }
            if (mExternalObserver != null) {
                try {
                    mContext.getContentResolver().unregisterContentObserver(mExternalObserver);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                mExternalObserver = null;
            }

            // 清空数据
            mStartListenTime = 0;
            sHasCallbackPaths.clear();
        }



        private Point getImageSize(String imagePath) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(imagePath, options);
            return new Point(options.outWidth, options.outHeight);
        }









        public void notifyOnShot(final String path) {
            mUiHandler.post(new Runnable() {
                @Override
                public void run() {
                    ScreenShotManager.getInstance().notifyOnShot(path);
                }
            });
        }

        /**
         * 媒体内容观察者(观察媒体数据库的改变)
         */
        private class MediaContentObserver extends ContentObserver {

            public MediaContentObserver(Uri contentUri, Handler handler) {
                super(handler);
            }

            @Override
            public void onChange(boolean selfChange, Uri uri) {
                super.onChange(selfChange, uri);
                handleMediaContentChange(uri);
            }
        }

    }

    class ScreenShotException extends Exception {

        public ScreenShotException(String msg) {
            super(msg);
            Debuger.logE(msg);
        }
    }


}
