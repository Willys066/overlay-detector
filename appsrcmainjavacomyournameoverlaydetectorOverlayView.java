package com.yourname.overlaydetector;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class OverlayView extends View {

    private static final int   COLOR_BOX      = Color.argb(220, 255, 50,  50);
    private static final int   COLOR_LABEL_BG = Color.argb(180, 0,   0,   0);
    private static final int   COLOR_LABEL_FG = Color.WHITE;
    private static final float STROKE_WIDTH   = 4f;
    private static final float TEXT_SIZE      = 32f;
    private static final float CORNER_RADIUS  = 8f;

    private volatile List<DetectionEngine.Detection> mDetections = new ArrayList<>();

    private final Paint mBoxPaint;
    private final Paint mBoxFillPaint;
    private final Paint mLabelBgPaint;
    private final Paint mTextPaint;
    private final RectF mTmpRect = new RectF();

    public OverlayView(Context context) {
        super(context);

        mBoxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mBoxPaint.setStyle(Paint.Style.STROKE);
        mBoxPaint.setColor(COLOR_BOX);
        mBoxPaint.setStrokeWidth(STROKE_WIDTH);

        mBoxFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mBoxFillPaint.setStyle(Paint.Style.FILL);
        mBoxFillPaint.setColor(Color.argb(30, 255, 50, 50));

        mLabelBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mLabelBgPaint.setStyle(Paint.Style.FILL);
        mLabelBgPaint.setColor(COLOR_LABEL_BG);

        mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mTextPaint.setColor(COLOR_LABEL_FG);
        mTextPaint.setTextSize(TEXT_SIZE);
        mTextPaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
    }

    public void updateDetections(List<DetectionEngine.Detection> detections) {
        mDetections = new ArrayList<>(detections);
        postInvalidate();
    }

    public void clear() {
        mDetections = new ArrayList<>();
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        for (DetectionEngine.Detection d : mDetections) drawDetection(canvas, d);
    }

    private void drawDetection(Canvas canvas, DetectionEngine.Detection d) {
        RectF box = d.bounds;
        canvas.drawRoundRect(box, CORNER_RADIUS, CORNER_RADIUS, mBoxFillPaint);
        canvas.drawRoundRect(box, CORNER_RADIUS, CORNER_RADIUS, mBoxPaint);
        drawCornerAccents(canvas, box);

        String label  = d.label.toUpperCase() + "  " + String.format("%.2f", d.confidence);
        float  textW  = mTextPaint.measureText(label);
        float  textH  = mTextPaint.getTextSize();
        float  padX   = 10f, padY = 6f;

        float labelLeft   = box.left;
        float labelBottom = box.top;
        float labelTop    = labelBottom - textH - padY * 2;
        float labelRight  = labelLeft + textW + padX * 2;

        if (labelTop < 0) {
            labelTop    = box.bottom;
            labelBottom = labelTop + textH + padY * 2;
        }

        mTmpRect.set(labelLeft, labelTop, labelRight, labelBottom);
        canvas.drawRoundRect(mTmpRect, 4f, 4f, mLabelBgPaint);
        canvas.drawText(label, labelLeft + padX, labelBottom - padY, mTextPaint);
    }

    private void drawCornerAccents(Canvas canvas, RectF box) {
        float len = Math.min(box.width(), box.height()) * 0.18f;
        Paint p   = new Paint(mBoxPaint);
        p.setStrokeWidth(STROKE_WIDTH * 2f);
        p.setColor(Color.WHITE);

        canvas.drawLine(box.left,  box.top,    box.left + len,  box.top,          p);
        canvas.drawLine(box.left,  box.top,    box.left,        box.top + len,    p);
        canvas.drawLine(box.right, box.top,    box.right - len, box.top,          p);
        canvas.drawLine(box.right, box.top,    box.right,       box.top + len,    p);
        canvas.drawLine(box.left,  box.bottom, box.left + len,  box.bottom,       p);
        canvas.drawLine(box.left,  box.bottom, box.left,        box.bottom - len, p);
        canvas.drawLine(box.right, box.bottom, box.right - len, box.bottom,       p);
        canvas.drawLine(box.right, box.bottom, box.right,       box.bottom - len, p);
    }
}