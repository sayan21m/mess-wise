/**
 * Copyright (c) 2026 SR Tech. All rights reserved.
 * This project and its source code are the intellectual property of SR Tech.
 * Unauthorized copying, distribution, or modification is strictly prohibited.
 */
package com.srtech.messwise;

import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.srtech.messwise.utils.AppUpdateManager;
import com.srtech.messwise.utils.SecurityUtils;

import android.content.SharedPreferences;
import android.view.WindowManager;
import android.widget.Toast;

public class BaseActivity extends AppCompatActivity {

    private ValueEventListener versionListener;
    private boolean versionMonitorAttached = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        int theme = getSharedPreferences("UserPrefs", MODE_PRIVATE)
                .getInt("pref_theme", AppCompatDelegate.MODE_NIGHT_YES);
        AppCompatDelegate.setDefaultNightMode(theme);

        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (this instanceof SplashActivity) {
            return;
        }
        if (isDeviceRooted() || (!BuildConfig.DEBUG && isEmulator())) {
            Toast.makeText(this, R.string.error_device_rooted, Toast.LENGTH_LONG).show();
            finishAffinity();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
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
        if (versionMonitorAttached) {
            return;
        }

        versionListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                AppUpdateManager.handleVersionSnapshot(BaseActivity.this, snapshot, true);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        };

        FirebaseDatabase.getInstance()
                .getReference()
                .child("version_control")
                .addValueEventListener(versionListener);
        versionMonitorAttached = true;
    }

    private void stopVersionMonitor() {
        if (versionListener != null) {
            FirebaseDatabase.getInstance()
                    .getReference()
                    .child("version_control")
                    .removeEventListener(versionListener);
            versionListener = null;
        }
        versionMonitorAttached = false;
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
