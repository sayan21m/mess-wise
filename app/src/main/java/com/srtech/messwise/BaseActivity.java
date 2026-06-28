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

public class BaseActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // Apply theme before super.onCreate
        int theme = getSharedPreferences("UserPrefs", MODE_PRIVATE)
                .getInt("pref_theme", AppCompatDelegate.MODE_NIGHT_YES);
        AppCompatDelegate.setDefaultNightMode(theme);
        
        super.onCreate(savedInstanceState);
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
