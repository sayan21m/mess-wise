/**
 * Copyright (c) 2026 SR Tech. All rights reserved.
 * This project and its source code are the intellectual property of SR Tech.
 * Unauthorized copying, distribution, or modification is strictly prohibited.
 */
package com.srtech.messwise;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import com.srtech.messwise.utils.SecurityUtils;
import android.content.SharedPreferences;
import android.view.WindowManager;
import android.content.Intent;
import android.net.Uri;
import androidx.annotation.NonNull;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class BaseActivity extends AppCompatActivity {

    private ValueEventListener versionListener;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // Apply theme before super.onCreate
        int theme = getSharedPreferences("UserPrefs", MODE_PRIVATE)
                .getInt("pref_theme", AppCompatDelegate.MODE_NIGHT_YES);
        AppCompatDelegate.setDefaultNightMode(theme);
        
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Don't start real-time listener on SplashActivity to avoid conflict with initial check
        if (!(this instanceof SplashActivity)) {
            startVersionMonitor();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopVersionMonitor();
    }

    private void startVersionMonitor() {
        versionListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    long minVersion = snapshot.child("min_version_code").getValue(Long.class) != null ? 
                            snapshot.child("min_version_code").getValue(Long.class) : 0;
                    String updateUrl = snapshot.child("update_url").getValue(String.class);

                    int currentVersion = 0;
                    try {
                        currentVersion = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
                    } catch (Exception ignored) {}

                    if (currentVersion < minVersion) {
                        showRealtimeUpdateDialog(updateUrl != null ? updateUrl : "https://mess-wise.web.app");
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        FirebaseDatabase.getInstance().getReference().child("version_control").addValueEventListener(versionListener);
    }

    private void stopVersionMonitor() {
        if (versionListener != null) {
            FirebaseDatabase.getInstance().getReference().child("version_control").removeEventListener(versionListener);
        }
    }

    private void showRealtimeUpdateDialog(String url) {
        if (isFinishing() || isDestroyed()) return;
        
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.update_required_title)
                .setMessage(R.string.update_required_msg)
                .setCancelable(false)
                .setPositiveButton(R.string.update_now, (dialog, which) -> {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(intent);
                    finishAffinity(); // Close all activities
                })
                .show();
    }

    protected void setScreenSecurity(boolean enabled) {
        if (enabled) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                    WindowManager.LayoutParams.FLAG_SECURE);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }
    }

    protected SharedPreferences getPrefs() {
        return getSharedPreferences("UserPrefs", MODE_PRIVATE);
    }

    protected SharedPreferences getSecurePrefs() {
        return SecurityUtils.getSecurePrefs(this);
    }

    protected boolean isEmulator() {
        return Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || "google_sdk".equals(Build.PRODUCT);
    }

    protected boolean isDeviceRooted() {
        String[] paths = {
            "/system/app/Superuser.apk", "/sbin/su", "/system/bin/su", "/system/xbin/su",
            "/data/local/xbin/su", "/data/local/bin/su", "/system/sd/xbin/su",
            "/system/bin/failsafe/su", "/data/local/su"
        };
        for (String path : paths) {
            if (new java.io.File(path).exists()) return true;
        }
        return false;
    }
}
