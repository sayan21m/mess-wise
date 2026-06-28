/**
 * Copyright (c) 2026 SR Tech. All rights reserved.
 * This project and its source code are the intellectual property of SR Tech.
 * Unauthorized copying, distribution, or modification is strictly prohibited.
 */
package com.srtech.messwise.utils;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.appopen.AppOpenAd;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.srtech.messwise.MainActivity;
import com.srtech.messwise.SplashActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class AdManager implements Application.ActivityLifecycleCallbacks {

    private static final String TAG = "AdManager";
    // Production IDs
     private static final String AD_UNIT_APP_OPEN = "ca-app-pub-7189372192433975/5871072065";
     private static final String AD_UNIT_INTERSTITIAL = "ca-app-pub-7189372192433975/4861242726";
    
    // Test IDs
//    private static final String AD_UNIT_APP_OPEN = "ca-app-pub-3940256099942544/9257395923";
//    private static final String AD_UNIT_INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712";

    private static final int MAX_ADS_PER_DAY = 5;

    private static AdManager instance;
    private AppOpenAd appOpenAd = null;
    private InterstitialAd interstitialAd = null;
    private boolean isShowingAd = false;
    private long loadTime = 0;
    private Activity currentActivity;
    private final SharedPreferences prefs;
    private final Application application;

    private AdManager(Application application) {
        this.application = application;
        this.prefs = application.getSharedPreferences("AdPrefs", Context.MODE_PRIVATE);
        application.registerActivityLifecycleCallbacks(this);
        fetchAppOpenAd();
        loadInterstitialAd(application);
    }

    public static void init(Application application) {
        if (instance == null) {
            instance = new AdManager(application);
        }
    }

    public static AdManager getInstance() {
        return instance;
    }

    private boolean canShowAd() {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        String lastAdDate = prefs.getString("last_ad_date", "");
        int adCount = prefs.getInt("ad_count", 0);

        if (!today.equals(lastAdDate)) {
            prefs.edit().putString("last_ad_date", today).putInt("ad_count", 0).apply();
            return true;
        }

        return adCount < MAX_ADS_PER_DAY;
    }

    private void incrementAdCount() {
        int adCount = prefs.getInt("ad_count", 0);
        prefs.edit().putInt("ad_count", adCount + 1).apply();
    }

    /**
     * App Open Ad Logic
     */
    public void fetchAppOpenAd() {
        if (isAdAvailable()) return;

        Log.d(TAG, "Fetching App Open Ad...");
        AppOpenAd.load(
                application,
                AD_UNIT_APP_OPEN,
                new AdRequest.Builder().build(),
                new AppOpenAd.AppOpenAdLoadCallback() {
                    @Override
                    public void onAdLoaded(@NonNull AppOpenAd ad) {
                        appOpenAd = ad;
                        loadTime = new Date().getTime();
                        Log.d(TAG, "App Open Ad Loaded Successfully");
                    }

                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                        Log.e(TAG, "App Open Ad Failed to Load: " + loadAdError.getMessage() + " (Code: " + loadAdError.getCode() + ")");
                    }
                }
        );
    }

    private boolean isAdAvailable() {
        return appOpenAd != null && (new Date().getTime() - loadTime) < 4 * 3600000;
    }

    public void showAppOpenAdIfAvailable() {
        if (!isShowingAd && isAdAvailable() && canShowAd()) {
            Log.d(TAG, "Showing App Open Ad");
            appOpenAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                @Override
                public void onAdDismissedFullScreenContent() {
                    appOpenAd = null;
                    isShowingAd = false;
                    fetchAppOpenAd();
                }

                @Override
                public void onAdFailedToShowFullScreenContent(AdError adError) {
                    isShowingAd = false;
                }

                @Override
                public void onAdShowedFullScreenContent() {
                    isShowingAd = true;
                    incrementAdCount();
                }
            });
            if (currentActivity != null) {
                appOpenAd.show(currentActivity);
            } else {
                Log.e(TAG, "Failed to show App Open Ad: currentActivity is null");
                isShowingAd = false;
                fetchAppOpenAd();
            }
        } else {
            fetchAppOpenAd();
        }
    }

    /**
     * Interstitial Ad Logic
     */
    public void loadInterstitialAd(Context context) {
        InterstitialAd.load(context, AD_UNIT_INTERSTITIAL, new AdRequest.Builder().build(),
                new InterstitialAdLoadCallback() {
                    @Override
                    public void onAdLoaded(@NonNull InterstitialAd ad) {
                        interstitialAd = ad;
                        Log.d(TAG, "Interstitial Ad Loaded");
                    }

                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                        interstitialAd = null;
                        Log.e(TAG, "Interstitial Ad Failed to Load: " + loadAdError.getMessage());
                    }
                });
    }

    public void showInterstitialAd(Activity activity, OnAdClosedListener listener) {
        if (interstitialAd != null && canShowAd()) {
            interstitialAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                @Override
                public void onAdDismissedFullScreenContent() {
                    interstitialAd = null;
                    loadInterstitialAd(activity);
                    if (listener != null) listener.onAdClosed();
                }

                @Override
                public void onAdFailedToShowFullScreenContent(AdError adError) {
                    interstitialAd = null;
                    if (listener != null) listener.onAdClosed();
                }

                @Override
                public void onAdShowedFullScreenContent() {
                    incrementAdCount();
                }
            });
            interstitialAd.show(activity);
        } else {
            if (listener != null) listener.onAdClosed();
            loadInterstitialAd(activity);
        }
    }

    public interface OnAdClosedListener {
        void onAdClosed();
    }

    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {}

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
        currentActivity = activity;
    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        currentActivity = activity;
        // Show App Open Ad when app is opened/resumed
        // We include all major entry activities
        showAppOpenAdIfAvailable();
    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {}

    @Override
    public void onActivityStopped(@NonNull Activity activity) {}

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {}

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
        if (currentActivity == activity) {
            currentActivity = null;
        }
    }
}
