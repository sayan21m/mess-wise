package com.srtech.messwise.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.OvershootInterpolator;

import androidx.core.content.ContextCompat;

import com.srtech.messwise.R;

public class AdminWheelMenuView extends View {

    private final Paint buttonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float revealProgress = 0f;

    private final String[] labels = {
            getContext().getString(R.string.common_members),
            getContext().getString(R.string.common_meals),
            getContext().getString(R.string.common_slots)
    };

    private final int[] icons = {
            R.drawable.ic_group,
            R.drawable.ic_meal,
            R.drawable.ic_summary
    };

    private final double[] angles = {
            225,
            270,
            315
    };

    private final RectF[] itemBounds = new RectF[labels.length];

    private OnWheelItemClickListener listener;

    public AdminWheelMenuView(Context context) {
        super(context);
        init();
    }

    public AdminWheelMenuView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        buttonPaint.setColor(Color.parseColor("#151A24"));

        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(sp(12));

        setLayerType(LAYER_TYPE_SOFTWARE, null);

        for (int i = 0; i < itemBounds.length; i++) {
            itemBounds[i] = new RectF();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float cx = getWidth() / 2f;
        float cy = getHeight(); // Origin at the bottom center
        float radius = dp(130) * revealProgress;
        float itemRadius = dp(34);

        int itemCount = Math.min(Math.min(labels.length, icons.length), angles.length);

        for (int i = 0; i < itemCount; i++) {
            float x = (float) (cx + Math.cos(Math.toRadians(angles[i])) * radius);
            float y = (float) (cy + Math.sin(Math.toRadians(angles[i])) * radius);

            buttonPaint.setShadowLayer(
                    dp(12),
                    0,
                    dp(4),
                    Color.argb(90, 79, 140, 255)
            );

            canvas.drawCircle(x, y, itemRadius, buttonPaint);

            Drawable drawable = ContextCompat.getDrawable(getContext(), icons[i]);
            if (drawable != null) {
                int size = (int) dp(24);
                drawable.setBounds(
                        (int) (x - size / 2f),
                        (int) (y - size / 2f),
                        (int) (x + size / 2f),
                        (int) (y + size / 2f)
                );
                drawable.setTint(Color.WHITE);
                drawable.draw(canvas);
            }

            canvas.drawText(labels[i], x, y + itemRadius + dp(18), textPaint);

            itemBounds[i].set(
                    x - itemRadius,
                    y - itemRadius,
                    x + itemRadius,
                    y + itemRadius
            );
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) {
            float x = event.getX();
            float y = event.getY();

            for (int i = 0; i < itemBounds.length; i++) {
                if (itemBounds[i] != null && itemBounds[i].contains(x, y)) {
                    if (listener != null) {
                        listener.onItemClick(i);
                    }
                    return true;
                }
            }
        }
        return true;
    }

    public void startOpenAnimation() {
        ValueAnimator animator = ValueAnimator.ofFloat(revealProgress, 1f);
        animator.setDuration(350);
        animator.setInterpolator(new OvershootInterpolator(1.15f));
        animator.addUpdateListener(a -> {
            revealProgress = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    public void startCloseAnimation(Runnable endAction) {
        ValueAnimator animator = ValueAnimator.ofFloat(revealProgress, 0f);
        animator.setDuration(250);
        animator.setInterpolator(new OvershootInterpolator(0.8f));
        animator.addUpdateListener(a -> {
            revealProgress = (float) a.getAnimatedValue();
            invalidate();
        });
        if (endAction != null) {
            animator.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    endAction.run();
                }
            });
        }
        animator.start();
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private float sp(float value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }

    public void setOnWheelItemClickListener(OnWheelItemClickListener listener) {
        this.listener = listener;
    }

    public interface OnWheelItemClickListener {
        void onItemClick(int index);
    }
}