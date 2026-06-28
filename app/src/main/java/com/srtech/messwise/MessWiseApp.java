/**
 * Copyright (c) 2026 SR Tech. All rights reserved.
 * This project and its source code are the intellectual property of SR Tech.
 * Unauthorized copying, distribution, or modification is strictly prohibited.
 */
package com.srtech.messwise;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.android.gms.ads.MobileAds;
import com.srtech.messwise.utils.AdManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MessWiseApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        // Initialize AdMob
        new Thread(() -> {
            MobileAds.initialize(this, initializationStatus -> {
                Log.d("AdManager", "AdMob Initialized");
            });
        }).start();

        // Initialize AdManager
        AdManager.init(this);
    }
}
