package com.srtech.messwise;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import androidx.appcompat.app.AppCompatActivity;

@SuppressLint("CustomSplashScreen")
public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        View logoContainer = findViewById(R.id.logoContainer);
        View progressBar = findViewById(R.id.progressBar);

        // Initial states
        logoContainer.setAlpha(0f);
        logoContainer.setScaleX(0.8f);
        logoContainer.setScaleY(0.8f);
        progressBar.setAlpha(0f);

        // Animations
        logoContainer.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(1200)
                .setInterpolator(new OvershootInterpolator())
                .start();

        progressBar.animate()
                .alpha(1f)
                .setStartDelay(800)
                .setDuration(800)
                .start();

        new Handler().postDelayed(() -> {
            startActivity(new Intent(SplashActivity.this, LoginActivity.class));
            finish();
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        }, 3000);
    }
}
