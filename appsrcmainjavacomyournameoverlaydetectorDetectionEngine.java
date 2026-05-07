package com.yourname.overlaydetector;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.RectF;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

public class DetectionEngine {

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

    private static class Template {
        final String label;
        final Mat    mat;
        Template(String label, Mat mat) {
            this.label = label;
            this.mat   = mat;
        }
    }

    public float   confidenceThreshold = 0.80f;
    public boolean multiScale          = false;
    public float[] scales              = { 0.75f, 1.0f, 1.25f };
    public boolean useGray             = false;

    private final List<Template> mTemplates = new ArrayList<>();

    public DetectionEngine(Context context) {}

    public void addTemplate(String label, Bitmap bitmap) {
        Mat mat = new Mat();
        Utils.bitmapToMat(bitmap, mat);
        if (useGray) {
            Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGBA2GRAY);
        } else {
            Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGBA2BGR);
        }
        mTemplates.add(new Template(label, mat));
    }

    public void clearTemplates() {
        for (Template t : mTemplates) t.mat.release();
        mTemplates.clear();
    }

    public List<Detection> detect(Bitmap frame) {
        List<Detection> results = new ArrayList<>();
        if (mTemplates.isEmpty()) return results;

        Mat frameMat = new Mat();
        Utils.bitmapToMat(frame, frameMat);
        Mat frameSearch = new Mat();
        if (useGray) {
            Imgproc.cvtColor(frameMat, frameSearch, Imgproc.COLOR_RGBA2GRAY);
        } else {
            Imgproc.cvtColor(frameMat, frameSearch, Imgproc.COLOR_RGBA2BGR);
        }
        frameMat.release();

        for (Template tmpl : mTemplates) {
            if (multiScale) {
                for (float s : scales) detectSingleScale(frameSearch, tmpl, s, results);
            } else {
                detectSingleScale(frameSearch, tmpl, 1.0f, results);
            }
        }
        frameSearch.release();
        return nms(results, 0.4f);
    }

    private void detectSingleScale(Mat scene, Template tmpl, float scale,
                                    List<Detection> out) {
        Mat tpl = tmpl.mat;
        if (scale != 1.0f) {
            tpl = new Mat();
            Size sz = new Size(tmpl.mat.cols() * scale, tmpl.mat.rows() * scale);
            Imgproc.resize(tmpl.mat, tpl, sz, 0, 0, Imgproc.INTER_AREA);
        }
        if (tpl.cols() > scene.cols() || tpl.rows() > scene.rows()) {
            if (scale != 1.0f) tpl.release();
            return;
        }
        Mat result = new Mat();
        Imgproc.matchTemplate(scene, tpl, result, Imgproc.TM_CCOEFF_NORMED);
        findAllMatches(result, tpl.cols(), tpl.rows(), 1f / scale, tmpl.label, out);
        result.release();
        if (scale != 1.0f) tpl.release();
    }

    private void findAllMatches(Mat result, int tplW, int tplH,
                                 float scaleBack, String label,
                                 List<Detection> out) {
        Mat copy = result.clone();
        while (true) {
            Core.MinMaxLocResult mm = Core.minMaxLoc(copy);
            if (mm.maxVal < confidenceThreshold) break;
            Point loc = mm.maxLoc;
            float x = (float) loc.x, y = (float) loc.y;
            float w = tplW * scaleBack, h = tplH * scaleBack;
            out.add(new Detection(label, new RectF(x, y, x + w, y + h), (float) mm.maxVal));
            int maskX = Math.max(0, (int) loc.x - tplW / 2);
            int maskY = Math.max(0, (int) loc.y - tplH / 2);
            int maskW = Math.min(tplW, copy.cols() - maskX);
            int maskH = Math.min(tplH, copy.rows() - maskY);
            if (maskW > 0 && maskH > 0)
                copy.submat(maskY, maskY + maskH, maskX, maskX + maskW).setTo(new Scalar(0));
        }
        copy.release();
    }

    private List<Detection> nms(List<Detection> detections, float iouThreshold) {
        detections.sort((a, b) -> Float.compare(b.confidence, a.confidence));
        List<Detection> kept = new ArrayList<>();
        boolean[] suppressed = new boolean[detections.size()];
        for (int i = 0; i < detections.size(); i++) {
            if (suppressed[i]) continue;
            kept.add(detections.get(i));
            for (int j = i + 1; j < detections.size(); j++) {
                if (!suppressed[j] && iou(detections.get(i).bounds,
                                          detections.get(j).bounds) > iouThreshold)
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
}