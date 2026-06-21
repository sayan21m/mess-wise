package com.srtech.messwise;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

public class DuckView extends View {

    private Paint bodyPaint, headPaint, beakPaint, eyePaint,
            helmetPaint, helmetStrapPaint, batPaint, batHandlePaint,
            legPaint, shadowPaint;

    private float walkCycle = 0f;   // 0 to 1 repeating
    private float duckX = 0f;       // horizontal position
    private float screenWidth = 0f;

    private ValueAnimator walkAnimator;

    public DuckView(Context context) {
        super(context);
        init();
    }

    public DuckView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        bodyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bodyPaint.setColor(Color.parseColor("#FFD700")); // golden

        headPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        headPaint.setColor(Color.parseColor("#FFC200")); // slightly darker golden

        beakPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        beakPaint.setColor(Color.parseColor("#FF8C00")); // orange beak

        eyePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        eyePaint.setColor(Color.BLACK);

        helmetPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        helmetPaint.setColor(Color.parseColor("#1565C0")); // blue helmet

        helmetStrapPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        helmetStrapPaint.setColor(Color.parseColor("#0D47A1"));
        helmetStrapPaint.setStyle(Paint.Style.STROKE);
        helmetStrapPaint.setStrokeWidth(4f);

        batPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        batPaint.setColor(Color.parseColor("#8B4513")); // brown bat

        batHandlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        batHandlePaint.setColor(Color.parseColor("#D2691E"));

        legPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        legPaint.setColor(Color.parseColor("#FF8C00"));
        legPaint.setStyle(Paint.Style.STROKE);
        legPaint.setStrokeWidth(6f);
        legPaint.setStrokeCap(Paint.Cap.ROUND);

        shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadowPaint.setColor(Color.parseColor("#22000000"));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        screenWidth = w;
        duckX = -150f;
        startAnimation();
    }

    private void startAnimation() {
        if (walkAnimator != null) walkAnimator.cancel();

        walkAnimator = ValueAnimator.ofFloat(0f, 1f);
        walkAnimator.setDuration(800);
        walkAnimator.setRepeatCount(ValueAnimator.INFINITE);
        walkAnimator.setInterpolator(new LinearInterpolator());
        walkAnimator.addUpdateListener(animation -> {
            walkCycle = (float) animation.getAnimatedValue();
            duckX += 2.5f;
            if (duckX > screenWidth + 150f) duckX = -150f;
            invalidate();
        });
        walkAnimator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (walkAnimator != null) walkAnimator.cancel();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float cx = duckX;
        float groundY = getHeight() * 0.72f;

        float bodyW = 80f, bodyH = 55f;
        float headR = 28f;
        float headCx = cx + 38f;
        float headCy = groundY - bodyH - headR + 10f;

        // Shadow
        canvas.drawOval(new RectF(cx - bodyW / 2 + 5, groundY + 4, cx + bodyW / 2 + 5, groundY + 16), shadowPaint);

        // Legs
        float legSwing = (float) Math.sin(walkCycle * 2 * Math.PI) * 18f;
        // Left leg
        canvas.drawLine(cx - 12f, groundY, cx - 18f + legSwing, groundY + 22f, legPaint);
        // Foot left
        canvas.drawLine(cx - 18f + legSwing, groundY + 22f, cx - 32f + legSwing, groundY + 22f, legPaint);

        // Right leg (opposite phase)
        float legSwing2 = -legSwing;
        canvas.drawLine(cx + 5f, groundY, cx + 5f + legSwing2, groundY + 22f, legPaint);
        // Foot right
        canvas.drawLine(cx + 5f + legSwing2, groundY + 22f, cx + 19f + legSwing2, groundY + 22f, legPaint);

        // Body
        canvas.drawOval(new RectF(cx - bodyW / 2, groundY - bodyH, cx + bodyW / 2, groundY), bodyPaint);

        // Wing (slightly animated flap)
        float wingFlap = (float) Math.sin(walkCycle * 4 * Math.PI) * 5f;
        Paint wingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        wingPaint.setColor(Color.parseColor("#FFB800"));
        canvas.drawOval(new RectF(cx - bodyW / 2 + 8f, groundY - bodyH + 18f - wingFlap,
                cx + 10f, groundY - 10f + wingFlap), wingPaint);

        // Tail feathers
        Path tail = new Path();
        tail.moveTo(cx - bodyW / 2, groundY - bodyH / 2);
        tail.lineTo(cx - bodyW / 2 - 22f, groundY - bodyH / 2 - 15f);
        tail.lineTo(cx - bodyW / 2 - 14f, groundY - bodyH / 2 + 5f);
        tail.close();
        canvas.drawPath(tail, headPaint);

        // Neck connecting body to head
        Paint neckPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        neckPaint.setColor(Color.parseColor("#FFC200"));
        canvas.drawOval(new RectF(headCx - 16f, headCy + headR - 10f, headCx + 16f, groundY - bodyH + 18f), neckPaint);

        // Head
        canvas.drawCircle(headCx, headCy, headR, headPaint);

        // Eye white
        Paint eyeWhitePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        eyeWhitePaint.setColor(Color.WHITE);
        canvas.drawCircle(headCx + 12f, headCy - 5f, 9f, eyeWhitePaint);
        // Eye pupil
        canvas.drawCircle(headCx + 14f, headCy - 4f, 4.5f, eyePaint);
        // Eye shine
        Paint shinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shinePaint.setColor(Color.WHITE);
        canvas.drawCircle(headCx + 16f, headCy - 6f, 1.5f, shinePaint);

        // Beak
        Path beak = new Path();
        beak.moveTo(headCx + headR - 4f, headCy - 2f);
        beak.lineTo(headCx + headR + 22f, headCy + 4f);
        beak.lineTo(headCx + headR + 16f, headCy + 12f);
        beak.lineTo(headCx + headR - 4f, headCy + 8f);
        beak.close();
        canvas.drawPath(beak, beakPaint);

        // ---- CRICKET HELMET ----
        // Main dome
        RectF helmetRect = new RectF(headCx - headR - 4f, headCy - headR - 12f,
                headCx + headR + 4f, headCy + 8f);
        canvas.drawArc(helmetRect, 180f, 180f, false, helmetPaint);

        // Helmet brim
        Paint brimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        brimPaint.setColor(Color.parseColor("#0D47A1"));
        canvas.drawRect(headCx - headR - 8f, headCy + 5f, headCx + headR + 8f, headCy + 13f, brimPaint);

        // Helmet visor / grill (face guard)
        Paint grillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        grillPaint.setColor(Color.parseColor("#1976D2"));
        grillPaint.setStyle(Paint.Style.STROKE);
        grillPaint.setStrokeWidth(3f);
        // 3 vertical bars of grill in front
        canvas.drawLine(headCx + headR - 2f, headCy + 13f, headCx + headR + 10f, headCy + 30f, grillPaint);
        canvas.drawLine(headCx + headR + 6f, headCy + 13f, headCx + headR + 16f, headCy + 30f, grillPaint);
        canvas.drawLine(headCx + headR + 14f, headCy + 13f, headCx + headR + 22f, headCy + 28f, grillPaint);
        // Horizontal bar connecting grill
        canvas.drawLine(headCx + headR - 2f, headCy + 22f, headCx + headR + 24f, headCy + 22f, grillPaint);

        // Helmet chin strap
        canvas.drawArc(new RectF(headCx - headR + 4f, headCy, headCx + headR - 4f, headCy + 20f),
                0f, 180f, false, helmetStrapPaint);

        // ---- CRICKET BAT ----
        canvas.save();
        // Bat held in wing area
        canvas.rotate(-30f, cx + 30f, groundY - bodyH / 2);

        // Bat blade (wide part)
        Paint bladePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bladePaint.setColor(Color.parseColor("#DEB887"));
        canvas.drawRoundRect(new RectF(cx + 22f, groundY - bodyH - 20f, cx + 44f, groundY - bodyH + 45f),
                4f, 4f, bladePaint);

        // Bat edge lines
        Paint edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        edgePaint.setColor(Color.parseColor("#8B4513"));
        edgePaint.setStyle(Paint.Style.STROKE);
        edgePaint.setStrokeWidth(2f);
        canvas.drawRect(cx + 22f, groundY - bodyH - 20f, cx + 44f, groundY - bodyH + 45f, edgePaint);
        // Ridge line down center of bat
        canvas.drawLine(cx + 33f, groundY - bodyH - 18f, cx + 33f, groundY - bodyH + 43f, edgePaint);

        // Bat handle
        batHandlePaint.setStrokeWidth(7f);
        batHandlePaint.setStyle(Paint.Style.STROKE);
        batHandlePaint.setStrokeCap(Paint.Cap.ROUND);
        canvas.drawLine(cx + 33f, groundY - bodyH - 20f, cx + 33f, groundY - bodyH - 55f, batHandlePaint);

        // Handle grip (dark wrap)
        Paint gripPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gripPaint.setColor(Color.parseColor("#333333"));
        gripPaint.setStyle(Paint.Style.STROKE);
        gripPaint.setStrokeWidth(9f);
        gripPaint.setStrokeCap(Paint.Cap.ROUND);
        canvas.drawLine(cx + 33f, groundY - bodyH - 28f, cx + 33f, groundY - bodyH - 50f, gripPaint);

        canvas.restore();
    }
}
