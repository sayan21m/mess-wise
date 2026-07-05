package com.srtech.messwise.workers;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.srtech.messwise.utils.SecurityUtils;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;
import com.srtech.messwise.NotificationsActivity;
import com.srtech.messwise.R;
import com.srtech.messwise.data_models.NotificationModel;

import java.util.Locale;
import java.util.concurrent.ExecutionException;

public class DueReminderWorker extends Worker {

    public DueReminderWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d("MessWiseWorker", "Checking due reminders in background...");

        SharedPreferences prefs = SecurityUtils.getSecurePrefs(getApplicationContext());
        String messId = prefs.getString("messId", null);
        String userId = prefs.getString("userId", null);

        if (messId == null || userId == null) return Result.success();

        try {
            DataSnapshot configSnapshot = Tasks.await(FirebaseDatabase.getInstance().getReference()
                    .child(messId).child("config").child("reminders").get());

            if (!configSnapshot.exists()) return Result.success();

            Boolean enabled = configSnapshot.child("enabled").getValue(Boolean.class);
            Integer interval = configSnapshot.child("interval").getValue(Integer.class);
            Long lastSent = configSnapshot.child("last_sent").getValue(Long.class);

            if (enabled == null || !enabled || interval == null || lastSent == null) {
                return Result.success();
            }

            long intervalMillis = interval.longValue() * 60L * 60L * 1000L;
            if (System.currentTimeMillis() - lastSent < intervalMillis) {
                return Result.success();
            }

            final TaskCompletionSource<Boolean> tcs = new TaskCompletionSource<>();
            configSnapshot.getRef().runTransaction(new Transaction.Handler() {
                @NonNull
                @Override
                public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                    Boolean en = currentData.child("enabled").getValue(Boolean.class);
                    Integer iv = currentData.child("interval").getValue(Integer.class);
                    Long ls = currentData.child("last_sent").getValue(Long.class);
                    if (en == null || !en || iv == null || ls == null) {
                        return Transaction.abort();
                    }
                    long ivMillis = iv.longValue() * 60L * 60L * 1000L;
                    if (System.currentTimeMillis() - ls >= ivMillis) {
                        currentData.child("last_sent").setValue(System.currentTimeMillis());
                        return Transaction.success(currentData);
                    }
                    return Transaction.abort();
                }

                @Override
                public void onComplete(@Nullable DatabaseError error, boolean committed, @Nullable DataSnapshot currentData) {
                    if (error != null) {
                        tcs.setException(error.toException());
                    } else {
                        tcs.setResult(committed);
                    }
                }
            });

            if (Boolean.TRUE.equals(Tasks.await(tcs.getTask()))) {
                checkAndNotifyDues(messId, userId);
            }
        } catch (ExecutionException | InterruptedException e) {
            Log.e("MessWiseWorker", "Error fetching config", e);
            return Result.retry();
        }

        return Result.success();
    }

    private void checkAndNotifyDues(String messId, String currentUserId) {
        try {
            DataSnapshot membersSnapshot = Tasks.await(FirebaseDatabase.getInstance().getReference()
                    .child(messId).child("member").get());

            for (DataSnapshot memberSnap : membersSnapshot.getChildren()) {
                double totalDue = 0;
                DataSnapshot history = memberSnap.child("due_history");
                for (DataSnapshot month : history.getChildren()) {
                    Object val = month.getValue();
                    if (val instanceof Number) totalDue += ((Number) val).doubleValue();
                }

                String memberUid = memberSnap.getKey();
                String notiId = "DUE_REMINDER_" + memberUid;

                if (totalDue > 0) {
                    String name = memberSnap.child("name").getValue(String.class);
                    if (name == null) name = "Member";
                    String title = "Pending Due Reminder";
                    String message = "Hi " + name + ", you have a pending due of ₹" + String.format(Locale.getDefault(), "%.2f", totalDue) + ". Please clear it soon.";

                    NotificationModel n = new NotificationModel(notiId, title, message, "DUE_REMINDER", memberUid, System.currentTimeMillis());
                    FirebaseDatabase.getInstance().getReference().child(messId).child("notifications").child(notiId).setValue(n);

                    if (memberUid != null && memberUid.equals(currentUserId)) {
                        showNotification(title, message);
                    }
                } else {
                    FirebaseDatabase.getInstance().getReference().child(messId).child("notifications").child(notiId).removeValue();
                }
            }
        } catch (ExecutionException | InterruptedException e) {
            Log.e("MessWiseWorker", "Error fetching members", e);
        }
    }

    private void showNotification(String title, String message) {
        String channelId = "messwise_alerts";
        NotificationManager manager = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, "MessWise Alerts", NotificationManager.IMPORTANCE_HIGH);
            manager.createNotificationChannel(channel);
        }

        Intent intent = new Intent(getApplicationContext(), NotificationsActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 0, intent, PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), channelId)
                .setSmallIcon(R.drawable.ic_notifications)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        manager.notify((int) System.currentTimeMillis(), builder.build());
    }
}
