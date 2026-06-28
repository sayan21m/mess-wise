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
import android.view.View;
import android.widget.Toast;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import androidx.appcompat.app.AppCompatActivity;

@SuppressLint("CustomSplashScreen")
public class SplashActivity extends BaseActivity {

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
            if (isDeviceRooted()) {
                Toast.makeText(this, "Security Alert: This device is rooted. For your safety, MessWise cannot run on rooted devices.", Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            checkAppVersion();
        }, 3000);
    }

    private void checkAppVersion() {
        com.google.firebase.database.FirebaseDatabase.getInstance().getReference().child("version_control")
                .addListenerForSingleValueEvent(new com.google.firebase.database.ValueEventListener() {
                    @Override
                    public void onDataChange(@androidx.annotation.NonNull com.google.firebase.database.DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            long minVersion = snapshot.child("min_version_code").getValue(Long.class) != null ? 
                                    snapshot.child("min_version_code").getValue(Long.class) : 0;
                            String updateUrl = snapshot.child("update_url").getValue(String.class);
                            
                            int currentVersion = 0;
                            try {
                                currentVersion = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
                            } catch (Exception ignored) {}

                            if (currentVersion < minVersion) {
                                showUpdateDialog(updateUrl != null ? updateUrl : "https://mess-wise.web.app");
                            } else {
                                proceedToLogin();
                            }
                        } else {
                            proceedToLogin();
                        }
                    }

                    @Override
                    public void onCancelled(@androidx.annotation.NonNull com.google.firebase.database.DatabaseError error) {
                        proceedToLogin();
                    }
                });
    }

    private void showUpdateDialog(String url) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.update_required_title)
                .setMessage(R.string.update_required_msg)
                .setCancelable(false)
                .setPositiveButton(R.string.update_now, (dialog, which) -> {
                    Intent intent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url));
                    startActivity(intent);
                    finish();
                })
                .show();
    }

    private void proceedToLogin() {
        startActivity(new Intent(SplashActivity.this, LoginActivity.class));
        finish();
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
    }
}
