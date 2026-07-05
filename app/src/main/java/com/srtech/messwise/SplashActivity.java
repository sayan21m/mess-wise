/**
 * Copyright (c) 2026 SR Tech. All rights reserved.
 * This project and its source code are the intellectual property of SR Tech.
 * Unauthorized copying, distribution, or modification is strictly prohibited.
 */
package com.srtech.messwise;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Toast;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.srtech.messwise.utils.AppUpdateManager;

@SuppressLint("CustomSplashScreen")
public class SplashActivity extends BaseActivity {

    private final Handler splashHandler = new Handler(Looper.getMainLooper());
    private final Runnable splashRunnable = () -> {
        if (isFinishing() || isDestroyed()) return;
        if (isDeviceRooted() || (!BuildConfig.DEBUG && isEmulator())) {
            Toast.makeText(this, R.string.error_device_rooted, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        checkAppVersion();
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        View logoContainer = findViewById(R.id.logoContainer);
        View progressBar = findViewById(R.id.progressBar);

        logoContainer.setAlpha(0f);
        logoContainer.setScaleX(0.8f);
        logoContainer.setScaleY(0.8f);
        progressBar.setAlpha(0f);

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

        splashHandler.postDelayed(splashRunnable, 3000);
    }

    @Override
    protected void onDestroy() {
        splashHandler.removeCallbacks(splashRunnable);
        super.onDestroy();
    }

    private void checkAppVersion() {
        FirebaseDatabase.getInstance()
                .getReference()
                .child("version_control")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (isFinishing() || isDestroyed()) return;

                        if (!snapshot.exists()) {
                            proceedToLogin();
                            return;
                        }

                        if (AppUpdateManager.isForcedUpdateRequired(SplashActivity.this, snapshot)) {
                            AppUpdateManager.showForcedUpdateDialog(SplashActivity.this, snapshot);
                            return;
                        }

                        if (AppUpdateManager.isOptionalUpdateAvailable(SplashActivity.this, snapshot)) {
                            long latestVersion = AppUpdateManager.readRemoteVersion(snapshot, "latest_version_code");
                            if (!AppUpdateManager.wasOptionalUpdateDismissed(SplashActivity.this, latestVersion)) {
                                AppUpdateManager.showOptionalUpdateDialog(
                                        SplashActivity.this,
                                        snapshot,
                                        latestVersion,
                                        SplashActivity.this::proceedToLogin);
                                return;
                            }
                        }

                        proceedToLogin();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        if (!isFinishing() && !isDestroyed()) {
                            proceedToLogin();
                        }
                    }
                });
    }

    private void proceedToLogin() {
        startActivity(new Intent(SplashActivity.this, LoginActivity.class));
        finish();
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
    }
}
