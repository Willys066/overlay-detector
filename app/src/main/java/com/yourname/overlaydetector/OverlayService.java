package com.yourname.overlaydetector;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.view.WindowManager;

import androidx.core.app.NotificationCompat;

import org.opencv.android.OpenCVLoader;
import org.opencv.core.Scalar;

import java.util.ArrayList;
import java.util.List;

public class OverlayService extends Service {

    public static final String EXTRA_RESULT_CODE = "result_code";
    public static final String EXTRA_RESULT_DATA = "result_data";

    private static final String NOTIF_CHANNEL_ID = "overlay_detector_channel";
    private static final int    NOTIF_ID          = 42;

    private ScreenCaptureManager      mCaptureManager;
    private SilhouetteDetectionEngine mDetectionEngine;
    private OverlayView               mOverlayView;
    private WindowManager             mWindowManager;

    @Override
    public void onCreate() {
        super.onCreate();
        if (!OpenCVLoader.initDebug()) { stopSelf(); return; }
        startForeground(NOTIF_ID, buildNotification());
        mDetectionEngine = new SilhouetteDetectionEngine(this);
        setupEnemyProfiles();
        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        setupOverlayView();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        int    resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1);
        Intent resultData = intent.getParcelableExtra(EXTRA_RESULT_DATA);
        if (resultCode == -1 || resultData == null) { stopSelf(); return START_NOT_STICKY; }
        mCaptureManager = new ScreenCaptureManager(this, this::onFrame);
        mCaptureManager.start(resultCode, resultData);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mCaptureManager  != null) mCaptureManager.stop();
        if (mOverlayView     != null) mWindowManager.removeView(mOverlayView);
        if (mDetectionEngine != null) mDetectionEngine.release();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    private void setupEnemyProfiles() {
        mDetectionEngine.addEnemyProfile(new SilhouetteDetectionEngine.EnemyProfile(
            "Ennemi",
            new Scalar(0,   0,  20),
            new Scalar(180, 80, 100),
            800, 80_000, 1.5f, 5.0f
        ));
        mDetectionEngine.addEnemyProfile(new SilhouetteDetectionEngine.EnemyProfile(
            "Ennemi",
            new Scalar(0,  40, 100),
            new Scalar(25, 180, 255),
            200, 8_000, 0.8f, 4.0f
        ));
    }

    private void onFrame(Bitmap frame) {
        List<SilhouetteDetectionEngine.Detection> raw = mDetectionEngine.detect(frame);
        List<DetectionEngine.Detection> forOverlay = new ArrayList<>();
        for (SilhouetteDetectionEngine.Detection d : raw) {
            forOverlay.add(new DetectionEngine.Detection(d.label, d.bounds, d.confidence));
        }
        mOverlayView.updateDetections(forOverlay);
    }

    private void setupOverlayView() {
        mOverlayView = new OverlayView(this);
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            : WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT);
        mWindowManager.addView(mOverlayView, params);
    }

    private Notification buildNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                NOTIF_CHANNEL_ID, "Overlay Detector",
                NotificationManager.IMPORTANCE_LOW);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
                .createNotificationChannel(ch);
        }
        return new NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
            .setContentTitle("Overlay Detector actif")
            .setContentText("Détection silhouette en cours...")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build();
    }
}