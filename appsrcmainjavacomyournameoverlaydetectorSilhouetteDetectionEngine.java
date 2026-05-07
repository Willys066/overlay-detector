package com.yourname.overlaydetector;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.RectF;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

public class SilhouetteDetectionEngine {

    public static class Detection {
        public final String label;
        public final RectF  bounds;
        public final float  confidence;

        Detection(String label, RectF bounds, float confidence) {
            this.label      = label;
            this.bounds     = bounds;
            this.confidence = confidence;
        }
    }

    public static class EnemyProfile {
        public final String label;
        public final Scalar hsvLow;
        public final Scalar hsvHigh;
        public final int    minArea;
        public final int    maxArea;
        public final float  minAspectRatio;
        public final float  maxAspectRatio;

        public EnemyProfile(String label,
                            Scalar hsvLow, Scalar hsvHigh,
                            int minArea, int maxArea,
                            float minAspectRatio, float maxAspectRatio) {
            this.label          = label;
            this.hsvLow         = hsvLow;
            this.hsvHigh        = hsvHigh;
            this.minArea        = minArea;
            this.maxArea        = maxArea;
            this.minAspectRatio = minAspectRatio;
            this.maxAspectRatio = maxAspectRatio;
        }
    }

    public float boxPaddingFactor = 0.15f;
    public float nmsIouThreshold  = 0.35f;

    private final List<EnemyProfile> mProfiles   = new ArrayList<>();
    private Mat mHsv       = new Mat();
    private Mat mMask      = new Mat();
    private Mat mMaskClean = new Mat();
    private Mat mKernel    = new Mat();

    public SilhouetteDetectionEngine(Context context) {
        mKernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(5, 5));
    }

    public void addEnemyProfile(EnemyProfile profile) { mProfiles.add(profile); }
    public void clearProfiles() { mProfiles.clear(); }

    public List<Detection> detect(Bitmap frame) {
        List<Detection> all = new ArrayList<>();
        if (mProfiles.isEmpty()) return all;

        Mat bgr = new Mat();
        Utils.bitmapToMat(frame, bgr);
        Imgproc.cvtColor(bgr, bgr, Imgproc.COLOR_RGBA2BGR);
        Imgproc.cvtColor(bgr, mHsv, Imgproc.COLOR_BGR2HSV);
        bgr.release();

        for (EnemyProfile profile : mProfiles) detectProfile(mHsv, profile, all);
        return nms(all);
    }

    private void detectProfile(Mat hsv, EnemyProfile profile, List<Detection> out) {
        Core.inRange(hsv, profile.hsvLow, profile.hsvHigh, mMask);
        Imgproc.morphologyEx(mMask, mMaskClean, Imgproc.MORPH_CLOSE, mKernel);
        Imgproc.morphologyEx(mMaskClean, mMaskClean, Imgproc.MORPH_OPEN, mKernel);

        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(mMaskClean.clone(), contours, hierarchy,
            Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
        hierarchy.release();

        for (MatOfPoint contour : contours) {
            double area = Imgproc.contourArea(contour);
            if (area < profile.minArea || area > profile.maxArea) continue;
            Rect rect    = Imgproc.boundingRect(contour);
            float aspect = rect.width == 0 ? 0 : (float) rect.height / rect.width;
            if (aspect < profile.minAspectRatio || aspect > profile.maxAspectRatio) continue;
            int padX = (int) (rect.width  * boxPaddingFactor);
            int padY = (int) (rect.height * boxPaddingFactor);
            float left   = Math.max(0, rect.x - padX);
            float top    = Math.max(0, rect.y - padY);
            float right  = Math.min(hsv.cols(), rect.x + rect.width  + padX);
            float bottom = Math.min(hsv.rows(), rect.y + rect.height + padY);
            float confidence = Math.min(1f, (float) area / profile.maxArea);
            out.add(new Detection(profile.label, new RectF(left, top, right, bottom), confidence));
        }
    }

    private List<Detection> nms(List<Detection> detections) {
        detections.sort((a, b) -> Float.compare(b.confidence, a.confidence));
        List<Detection> kept = new ArrayList<>();
        boolean[] suppressed = new boolean[detections.size()];
        for (int i = 0; i < detections.size(); i++) {
            if (suppressed[i]) continue;
            kept.add(detections.get(i));
            for (int j = i + 1; j < detections.size(); j++) {
                if (!suppressed[j] &&
                    iou(detections.get(i).bounds, detections.get(j).bounds) > nmsIouThreshold)
                    suppressed[j] = true;
            }
        }
        return kept;
    }

    private float iou(RectF a, RectF b) {
        float il = Math.max(a.left, b.left), it = Math.max(a.top, b.top);
        float ir = Math.min(a.right, b.right), ib = Math.min(a.bottom, b.bottom);
        if (ir <= il || ib <= it) return 0f;
        float inter = (ir - il) * (ib - it);
        return inter / (a.width() * a.height() + b.width() * b.height() - inter);
    }

    public void release() {
        mHsv.release(); mMask.release(); mMaskClean.release(); mKernel.release();
    }
}