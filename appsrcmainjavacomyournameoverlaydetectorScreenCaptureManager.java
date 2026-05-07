package com.yourname.overlaydetector;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import java.nio.ByteBuffer;

public class ScreenCaptureManager {

    public interface FrameCallback {
        void onFrame(Bitmap frame);
    }

    private static final String VIRTUAL_DISPLAY_NAME = "OverlayDetectorCapture";

    private final Context       mContext;
    private final FrameCallback mCallback;
    private MediaProjection     mProjection;
    private VirtualDisplay      mVirtualDisplay;
    private ImageReader         mImageReader;
    private HandlerThread       mHandlerThread;
    private Handler             mHandler;

    private int mWidth, mHeight, mDensity;

    public ScreenCaptureManager(Context context, FrameCallback callback) {
        mContext  = context;
        mCallback = callback;
    }

    public void start(int resultCode, Intent resultData) {
        WindowManager wm = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics metrics = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(metrics);
        mWidth   = metrics.widthPixels;
        mHeight  = metrics.heightPixels;
        mDensity = metrics.densityDpi;

        mHandlerThread = new HandlerThread("ScreenCaptureThread");
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());

        MediaProjectionManager mgr =
            (MediaProjectionManager) mContext.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        mProjection = mgr.getMediaProjection(resultCode, resultData);

        mImageReader = ImageReader.newInstance(mWidth, mHeight, PixelFormat.RGBA_8888, 2);
        mImageReader.setOnImageAvailableListener(this::processImage, mHandler);

        mVirtualDisplay = mProjection.createVirtualDisplay(
            VIRTUAL_DISPLAY_NAME, mWidth, mHeight, mDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            mImageReader.getSurface(), null, mHandler);
    }

    private void processImage(ImageReader reader) {
        Image image = null;
        try {
            image = reader.acquireLatestImage();
            if (image == null) return;

            Image.Plane[] planes = image.getPlanes();
            ByteBuffer buffer    = planes[0].getBuffer();
            int rowStride        = planes[0].getRowStride();
            int pixelStride      = planes[0].getPixelStride();
            int paddedWidth      = rowStride / pixelStride;

            Bitmap bitmapWithPadding = Bitmap.createBitmap(
                paddedWidth, mHeight, Bitmap.Config.ARGB_8888);
            bitmapWithPadding.copyPixelsFromBuffer(buffer);

            Bitmap frame = Bitmap.createBitmap(bitmapWithPadding, 0, 0, mWidth, mHeight);
            if (frame != bitmapWithPadding) bitmapWithPadding.recycle();

            mCallback.onFrame(frame);
            frame.recycle();

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (image != null) image.close();
        }
    }

    public void stop() {
        if (mVirtualDisplay  != null) { mVirtualDisplay.release();   mVirtualDisplay  = null; }
        if (mProjection      != null) { mProjection.stop();          mProjection      = null; }
        if (mImageReader     != null) { mImageReader.close();        mImageReader     = null; }
        if (mHandlerThread   != null) { mHandlerThread.quitSafely(); mHandlerThread   = null; }
    }
}