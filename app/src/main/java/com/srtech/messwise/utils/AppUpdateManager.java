package com.srtech.messwise.utils;

import android.app.Activity;
import android.app.DownloadManager;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.google.firebase.database.DataSnapshot;
import com.srtech.messwise.R;

import java.io.File;

/**
 * In-app APK updates without Play Store:
 * Firebase version_control → download APK → prompt install → delete APK after update.
 *
 * Expected Firebase node (example):
 * version_control/
 *   min_version_code: 5
 *   latest_version_code: 5
 *   apk_url: https://.../MessWise.apk   (preferred direct APK link)
 *   update_url: https://...             (website fallback)
 *   update_message: "..."
 */
public final class AppUpdateManager {

    public static final String DEFAULT_UPDATE_URL = "https://mess-wise.web.app";
    public static final String DEFAULT_APK_URL =
            "https://github.com/sayan21m/mess-wise/raw/main/app/release/MessWise.apk";

    private static final String PREFS_NAME = "AppUpdatePrefs";
    private static final String KEY_DISMISSED_OPTIONAL_VERSION = "dismissed_optional_version";
    private static final String KEY_PENDING_APK_PATH = "pending_apk_path";
    private static final String KEY_PENDING_TARGET_VERSION = "pending_target_version";
    private static final String KEY_DOWNLOAD_ID = "download_id";
    private static final String KEY_AWAITING_INSTALL_PERMISSION = "awaiting_install_permission";
    private static final String APK_FILE_NAME = "MessWise-update.apk";

    private static boolean updateDialogVisible = false;
    private static boolean downloadInProgress = false;
    private static boolean downloadHandled = false;
    private static ProgressDialog progressDialog;
    private static BroadcastReceiver downloadReceiver;
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static Runnable progressPoller;
    private static long activeDownloadId = -1L;
    private static File activeApkFile;
    private static long activeTargetVersion = -1L;
    private static boolean activeForced = false;

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

    /**
     * Prefer a direct APK URL for in-app install.
     * Uses apk_url if set; otherwise update_url when it looks like an APK; else default GitHub APK.
     */
    @NonNull
    public static String readApkUrl(@NonNull DataSnapshot snapshot) {
        String apkUrl = snapshot.child("apk_url").getValue(String.class);
        if (apkUrl != null && !apkUrl.trim().isEmpty()) {
            return apkUrl.trim();
        }
        String updateUrl = readUpdateUrl(snapshot);
        if (updateUrl.toLowerCase().contains(".apk")) {
            return updateUrl;
        }
        return DEFAULT_APK_URL;
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

        cleanupInstalledUpdate(activity);
        maybeResumePendingInstall(activity);

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
        if (activity.isFinishing() || activity.isDestroyed() || updateDialogVisible || downloadInProgress) {
            return;
        }

        String message = readUpdateMessage(snapshot);
        if (message == null) {
            message = activity.getString(R.string.update_required_msg);
        }

        long targetVersion = Math.max(
                readRemoteVersion(snapshot, "min_version_code"),
                readRemoteVersion(snapshot, "latest_version_code"));
        String apkUrl = readApkUrl(snapshot);

        updateDialogVisible = true;
        new AlertDialog.Builder(activity)
                .setTitle(R.string.update_required_title)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton(R.string.update_now, (dialog, which) -> {
                    updateDialogVisible = false;
                    startApkDownload(activity, apkUrl, targetVersion, true);
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

        if (activity.isFinishing() || activity.isDestroyed() || updateDialogVisible || downloadInProgress) {
            if (onLater != null) {
                onLater.run();
            }
            return;
        }

        String message = readUpdateMessage(snapshot);
        if (message == null) {
            message = activity.getString(R.string.update_optional_msg);
        }

        String apkUrl = readApkUrl(snapshot);

        updateDialogVisible = true;
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.update_available_title)
                .setMessage(message)
                .setCancelable(true)
                .setPositiveButton(R.string.update_now, (d, which) ->
                        startApkDownload(activity, apkUrl, latestVersion, false))
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

    /** Call from Activity.onStart to delete leftover APKs and resume installs after Settings. */
    public static void onAppForeground(@NonNull Activity activity) {
        cleanupInstalledUpdate(activity);
        maybeResumePendingInstall(activity);
    }

    private static void startApkDownload(
            @NonNull Activity activity,
            @NonNull String apkUrl,
            long targetVersion,
            boolean forced) {

        if (downloadInProgress) {
            return;
        }

        File updatesDir = getUpdatesDir(activity);
        if (!updatesDir.exists() && !updatesDir.mkdirs()) {
            Toast.makeText(activity, R.string.update_download_failed, Toast.LENGTH_LONG).show();
            fallbackOpenUrl(activity, apkUrl);
            return;
        }

        File apkFile = new File(updatesDir, APK_FILE_NAME);
        if (apkFile.exists()) {
            //noinspection ResultOfMethodCallIgnored
            apkFile.delete();
        }

        DownloadManager downloadManager =
                (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
        if (downloadManager == null) {
            Toast.makeText(activity, R.string.update_download_failed, Toast.LENGTH_LONG).show();
            fallbackOpenUrl(activity, apkUrl);
            return;
        }

        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(apkUrl));
            request.setTitle(activity.getString(R.string.app_name));
            request.setDescription(activity.getString(R.string.update_downloading));
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE);
            request.setAllowedOverMetered(true);
            request.setAllowedOverRoaming(true);
            request.setDestinationInExternalFilesDir(activity, "updates", APK_FILE_NAME);
            request.setMimeType("application/vnd.android.package-archive");

            long downloadId = downloadManager.enqueue(request);
            downloadInProgress = true;
            downloadHandled = false;
            activeDownloadId = downloadId;
            activeApkFile = apkFile;
            activeTargetVersion = targetVersion;
            activeForced = forced;

            getUpdatePrefs(activity).edit()
                    .putLong(KEY_DOWNLOAD_ID, downloadId)
                    .putString(KEY_PENDING_APK_PATH, apkFile.getAbsolutePath())
                    .putLong(KEY_PENDING_TARGET_VERSION, targetVersion)
                    .apply();

            showProgressDialog(activity);
            registerDownloadReceiver(activity, downloadId);
            startProgressPolling(activity, downloadId);
        } catch (Exception e) {
            downloadInProgress = false;
            Toast.makeText(activity, R.string.update_download_failed, Toast.LENGTH_LONG).show();
            fallbackOpenUrl(activity, apkUrl);
        }
    }

    private static void registerDownloadReceiver(@NonNull Activity activity, long downloadId) {
        unregisterDownloadReceiver(activity);

        downloadReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (id != downloadId) {
                    return;
                }
                handleDownloadFinished(activity, downloadId);
            }
        };

        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        // Must be EXPORTED to receive DownloadManager system broadcasts on Android 13+
        ContextCompat.registerReceiver(
                activity,
                downloadReceiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED);
    }

    private static void unregisterDownloadReceiver(@NonNull Context context) {
        if (downloadReceiver != null) {
            try {
                context.unregisterReceiver(downloadReceiver);
            } catch (Exception ignored) {
            }
            downloadReceiver = null;
        }
    }

    private static synchronized void handleDownloadFinished(
            @NonNull Activity activity,
            long downloadId) {

        if (downloadHandled || activeDownloadId != downloadId) {
            return;
        }
        downloadHandled = true;
        downloadInProgress = false;
        stopProgressPolling();
        dismissProgressDialog();
        unregisterDownloadReceiver(activity);

        DownloadManager dm = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
        if (dm == null) {
            Toast.makeText(activity, R.string.update_download_failed, Toast.LENGTH_LONG).show();
            return;
        }

        File apkFile = resolveDownloadedFile(activity, dm, downloadId);
        if (apkFile != null && apkFile.exists() && apkFile.length() > 0) {
            getUpdatePrefs(activity).edit()
                    .putString(KEY_PENDING_APK_PATH, apkFile.getAbsolutePath())
                    .apply();
            promptInstall(activity, apkFile, activeTargetVersion, activeForced);
            return;
        }

        Toast.makeText(activity, R.string.update_download_failed, Toast.LENGTH_LONG).show();
    }

    @Nullable
    private static File resolveDownloadedFile(
            @NonNull Context context,
            @NonNull DownloadManager dm,
            long downloadId) {

        DownloadManager.Query query = new DownloadManager.Query().setFilterById(downloadId);
        try (Cursor cursor = dm.query(query)) {
            if (cursor == null || !cursor.moveToFirst()) {
                return activeApkFile;
            }

            int statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
            int status = statusIndex >= 0 ? cursor.getInt(statusIndex) : -1;
            if (status != DownloadManager.STATUS_SUCCESSFUL) {
                return null;
            }

            int uriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI);
            if (uriIndex >= 0) {
                String localUri = cursor.getString(uriIndex);
                if (localUri != null && !localUri.isEmpty()) {
                    Uri uri = Uri.parse(localUri);
                    String path = uri.getPath();
                    if (path != null) {
                        File fromUri = new File(path);
                        if (fromUri.exists()) {
                            return fromUri;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }

        if (activeApkFile != null && activeApkFile.exists()) {
            return activeApkFile;
        }

        File expected = new File(getUpdatesDir(context), APK_FILE_NAME);
        return expected.exists() ? expected : null;
    }

    private static void showProgressDialog(@NonNull Activity activity) {
        dismissProgressDialog();
        if (activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        progressDialog = new ProgressDialog(activity);
        progressDialog.setTitle(R.string.update_available_title);
        progressDialog.setMessage(activity.getString(R.string.update_preparing));
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setMax(100);
        progressDialog.setIndeterminate(false);
        progressDialog.setCancelable(false);
        progressDialog.show();
    }

    private static void startProgressPolling(@NonNull Activity activity, long downloadId) {
        stopProgressPolling();
        progressPoller = new Runnable() {
            @Override
            public void run() {
                if (!downloadInProgress || downloadHandled) {
                    return;
                }
                DownloadManager dm = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
                if (dm == null) {
                    return;
                }
                DownloadManager.Query query = new DownloadManager.Query().setFilterById(downloadId);
                try (Cursor cursor = dm.query(query)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        int statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                        int status = statusIndex >= 0 ? cursor.getInt(statusIndex) : -1;

                        int bytesIdx = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR);
                        int totalIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES);
                        long downloaded = bytesIdx >= 0 ? cursor.getLong(bytesIdx) : 0;
                        long total = totalIdx >= 0 ? cursor.getLong(totalIdx) : -1;

                        if (progressDialog != null) {
                            if (total > 0) {
                                int percent = (int) Math.min(100, (downloaded * 100L) / total);
                                progressDialog.setIndeterminate(false);
                                progressDialog.setProgress(percent);
                                progressDialog.setMessage(
                                        activity.getString(R.string.update_download_progress, percent));
                            } else {
                                progressDialog.setIndeterminate(true);
                                progressDialog.setMessage(activity.getString(R.string.update_downloading));
                            }
                        }

                        if (status == DownloadManager.STATUS_SUCCESSFUL) {
                            handleDownloadFinished(activity, downloadId);
                            return;
                        }
                        if (status == DownloadManager.STATUS_FAILED) {
                            handleDownloadFinished(activity, downloadId);
                            return;
                        }
                    }
                } catch (Exception ignored) {
                }
                mainHandler.postDelayed(this, 400);
            }
        };
        mainHandler.post(progressPoller);
    }

    private static void stopProgressPolling() {
        if (progressPoller != null) {
            mainHandler.removeCallbacks(progressPoller);
            progressPoller = null;
        }
    }

    private static void dismissProgressDialog() {
        if (progressDialog != null) {
            try {
                if (progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }
            } catch (Exception ignored) {
            }
            progressDialog = null;
        }
    }

    private static void promptInstall(
            @NonNull Activity activity,
            @NonNull File apkFile,
            long targetVersion,
            boolean forced) {

        getUpdatePrefs(activity).edit()
                .putString(KEY_PENDING_APK_PATH, apkFile.getAbsolutePath())
                .putLong(KEY_PENDING_TARGET_VERSION, targetVersion)
                .apply();

        if (activity.isFinishing() || activity.isDestroyed()) {
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !activity.getPackageManager().canRequestPackageInstalls()) {
            getUpdatePrefs(activity).edit()
                    .putBoolean(KEY_AWAITING_INSTALL_PERMISSION, true)
                    .apply();
            new AlertDialog.Builder(activity)
                    .setTitle(R.string.update_install_permission_title)
                    .setMessage(R.string.update_install_permission_msg)
                    .setCancelable(!forced)
                    .setPositiveButton(R.string.update_open_settings, (d, w) ->
                            openInstallPermissionSettings(activity))
                    .show();
            return;
        }

        new AlertDialog.Builder(activity)
                .setTitle(R.string.update_install_title)
                .setMessage(R.string.update_install_msg)
                .setCancelable(!forced)
                .setPositiveButton(R.string.update_install, (d, w) ->
                        launchInstaller(activity, apkFile))
                .show();
    }

    private static void maybeResumePendingInstall(@NonNull Activity activity) {
        SharedPreferences prefs = getUpdatePrefs(activity);
        String path = prefs.getString(KEY_PENDING_APK_PATH, null);
        long target = prefs.getLong(KEY_PENDING_TARGET_VERSION, -1);
        boolean awaitingPermission = prefs.getBoolean(KEY_AWAITING_INSTALL_PERMISSION, false);

        if (path == null || target < 0) {
            return;
        }

        if (getCurrentVersionCode(activity) >= target) {
            cleanupInstalledUpdate(activity);
            return;
        }

        File apkFile = new File(path);
        if (!apkFile.exists()) {
            clearPendingInstall(activity);
            return;
        }

        if (downloadInProgress || updateDialogVisible || !awaitingPermission) {
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !activity.getPackageManager().canRequestPackageInstalls()) {
            return;
        }

        prefs.edit().putBoolean(KEY_AWAITING_INSTALL_PERMISSION, false).apply();
        updateDialogVisible = true;
        new AlertDialog.Builder(activity)
                .setTitle(R.string.update_install_title)
                .setMessage(R.string.update_install_msg)
                .setCancelable(true)
                .setPositiveButton(R.string.update_install, (d, w) ->
                        launchInstaller(activity, apkFile))
                .setOnDismissListener(d -> updateDialogVisible = false)
                .show();
    }

    private static void launchInstaller(@NonNull Context context, @NonNull File apkFile) {
        try {
            Uri apkUri = FileProvider.getUriForFile(
                    context,
                    context.getPackageName() + ".fileprovider",
                    apkFile);

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(context, R.string.update_download_failed, Toast.LENGTH_LONG).show();
            fallbackOpenUrl(context, DEFAULT_UPDATE_URL);
        }
    }

    private static void openInstallPermissionSettings(@NonNull Activity activity) {
        try {
            Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
            intent.setData(Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(intent);
        } catch (Exception e) {
            Intent intent = new Intent(Settings.ACTION_SECURITY_SETTINGS);
            activity.startActivity(intent);
        }
    }

    /**
     * Deletes downloaded APK after a successful update (or leftover files).
     * Install replaces the process, so cleanup runs on the next app launch.
     */
    public static void cleanupInstalledUpdate(@NonNull Context context) {
        SharedPreferences prefs = getUpdatePrefs(context);
        long target = prefs.getLong(KEY_PENDING_TARGET_VERSION, -1);
        String path = prefs.getString(KEY_PENDING_APK_PATH, null);
        int current = getCurrentVersionCode(context);

        boolean updated = target > 0 && current >= target;
        if (updated) {
            if (path != null) {
                File f = new File(path);
                if (f.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    f.delete();
                }
            }
            File dir = getUpdatesDir(context);
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    //noinspection ResultOfMethodCallIgnored
                    file.delete();
                }
            }
            clearPendingInstall(context);
        } else if (path != null && !new File(path).exists()) {
            clearPendingInstall(context);
        }
    }

    private static void clearPendingInstall(@NonNull Context context) {
        getUpdatePrefs(context).edit()
                .remove(KEY_PENDING_APK_PATH)
                .remove(KEY_PENDING_TARGET_VERSION)
                .remove(KEY_DOWNLOAD_ID)
                .remove(KEY_AWAITING_INSTALL_PERMISSION)
                .apply();
    }

    @NonNull
    private static File getUpdatesDir(@NonNull Context context) {
        File external = context.getExternalFilesDir(null);
        if (external != null) {
            return new File(external, "updates");
        }
        return new File(context.getFilesDir(), "updates");
    }

    private static void fallbackOpenUrl(@NonNull Context context, @NonNull String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception ignored) {
        }
    }

    public static void openUpdateUrl(@NonNull Context context, @NonNull String url) {
        fallbackOpenUrl(context, url);
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
