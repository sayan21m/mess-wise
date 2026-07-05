package com.srtech.messwise.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.google.firebase.database.DataSnapshot;
import com.srtech.messwise.R;

public final class AppUpdateManager {

    public static final String DEFAULT_UPDATE_URL = "https://mess-wise.web.app";
    private static final String PREFS_NAME = "AppUpdatePrefs";
    private static final String KEY_DISMISSED_OPTIONAL_VERSION = "dismissed_optional_version";

    private static boolean updateDialogVisible = false;

    private AppUpdateManager() {
    }

    public static int getCurrentVersionCode(@NonNull Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                return (int) info.getLongVersionCode();
            }
            return info.versionCode;
        } catch (Exception ignored) {
            return 0;
        }
    }

    public static long readRemoteVersion(@NonNull DataSnapshot snapshot, @NonNull String key) {
        DataSnapshot node = snapshot.child(key);
        if (!node.exists()) {
            return 0L;
        }

        Object value = node.getValue();
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            try {
                return Long.parseLong(((String) value).trim());
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }
        return 0L;
    }

    @NonNull
    public static String readUpdateUrl(@NonNull DataSnapshot snapshot) {
        String url = snapshot.child("update_url").getValue(String.class);
        if (url == null || url.trim().isEmpty()) {
            return DEFAULT_UPDATE_URL;
        }
        return url.trim();
    }

    @Nullable
    public static String readUpdateMessage(@NonNull DataSnapshot snapshot) {
        String message = snapshot.child("update_message").getValue(String.class);
        if (message == null || message.trim().isEmpty()) {
            return null;
        }
        return message.trim();
    }

    public static boolean isForcedUpdateRequired(@NonNull Context context, @NonNull DataSnapshot snapshot) {
        long minVersion = readRemoteVersion(snapshot, "min_version_code");
        if (minVersion <= 0) {
            return false;
        }
        return getCurrentVersionCode(context) < minVersion;
    }

    public static boolean isOptionalUpdateAvailable(@NonNull Context context, @NonNull DataSnapshot snapshot) {
        long latestVersion = readRemoteVersion(snapshot, "latest_version_code");
        if (latestVersion <= 0) {
            return false;
        }

        int currentVersion = getCurrentVersionCode(context);
        long minVersion = readRemoteVersion(snapshot, "min_version_code");
        if (currentVersion < minVersion) {
            return false;
        }
        return currentVersion < latestVersion;
    }

    public static void handleVersionSnapshot(
            @NonNull Activity activity,
            @NonNull DataSnapshot snapshot,
            boolean allowOptionalUpdate) {

        if (activity.isFinishing() || activity.isDestroyed() || !snapshot.exists()) {
            return;
        }

        if (isForcedUpdateRequired(activity, snapshot)) {
            showForcedUpdateDialog(activity, snapshot);
            return;
        }

        if (allowOptionalUpdate && isOptionalUpdateAvailable(activity, snapshot)) {
            long latestVersion = readRemoteVersion(snapshot, "latest_version_code");
            if (!wasOptionalUpdateDismissed(activity, latestVersion)) {
                showOptionalUpdateDialog(activity, snapshot, latestVersion);
            }
        }
    }

    public static void showForcedUpdateDialog(@NonNull Activity activity, @NonNull DataSnapshot snapshot) {
        if (activity.isFinishing() || activity.isDestroyed() || updateDialogVisible) {
            return;
        }

        String message = readUpdateMessage(snapshot);
        if (message == null) {
            message = activity.getString(R.string.update_required_msg);
        }

        updateDialogVisible = true;
        new AlertDialog.Builder(activity)
                .setTitle(R.string.update_required_title)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton(R.string.update_now, (dialog, which) -> {
                    openUpdateUrl(activity, readUpdateUrl(snapshot));
                    updateDialogVisible = false;
                    activity.finishAffinity();
                })
                .setOnDismissListener(dialog -> updateDialogVisible = false)
                .show();
    }

    public static void showOptionalUpdateDialog(
            @NonNull Activity activity,
            @NonNull DataSnapshot snapshot,
            long latestVersion) {
        showOptionalUpdateDialog(activity, snapshot, latestVersion, null);
    }

    public static void showOptionalUpdateDialog(
            @NonNull Activity activity,
            @NonNull DataSnapshot snapshot,
            long latestVersion,
            @Nullable Runnable onLater) {

        if (activity.isFinishing() || activity.isDestroyed() || updateDialogVisible) {
            if (onLater != null) {
                onLater.run();
            }
            return;
        }

        String message = readUpdateMessage(snapshot);
        if (message == null) {
            message = activity.getString(R.string.update_optional_msg);
        }

        updateDialogVisible = true;
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.update_available_title)
                .setMessage(message)
                .setCancelable(true)
                .setPositiveButton(R.string.update_now, (d, which) -> {
                    openUpdateUrl(activity, readUpdateUrl(snapshot));
                    if (onLater != null) {
                        onLater.run();
                    }
                })
                .setNegativeButton(R.string.update_later, (d, which) -> {
                    markOptionalUpdateDismissed(activity, latestVersion);
                    if (onLater != null) {
                        onLater.run();
                    }
                })
                .create();

        dialog.setOnCancelListener(d -> {
            if (onLater != null) {
                onLater.run();
            }
        });
        dialog.setOnDismissListener(d -> updateDialogVisible = false);
        dialog.show();
    }

    public static boolean wasOptionalUpdateDismissed(@NonNull Context context, long latestVersion) {
        return getUpdatePrefs(context).getLong(KEY_DISMISSED_OPTIONAL_VERSION, -1L) == latestVersion;
    }

    public static void openUpdateUrl(@NonNull Context context, @NonNull String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception ignored) {
        }
    }

    private static void markOptionalUpdateDismissed(@NonNull Context context, long latestVersion) {
        getUpdatePrefs(context)
                .edit()
                .putLong(KEY_DISMISSED_OPTIONAL_VERSION, latestVersion)
                .apply();
    }

    private static SharedPreferences getUpdatePrefs(@NonNull Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
