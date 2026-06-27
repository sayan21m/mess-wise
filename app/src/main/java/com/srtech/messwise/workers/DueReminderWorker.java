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
import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
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

        SharedPreferences prefs = getApplicationContext().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        String messId = prefs.getString("messId", null);
        String userId = prefs.getString("userId", null);

        if (messId == null || userId == null) return Result.success();

        try {
            DataSnapshot configSnapshot = Tasks.await(FirebaseDatabase.getInstance().getReference()
                    .child(messId).child("config").child("reminders").get());

            if (configSnapshot.exists()) {
                Boolean enabled = configSnapshot.child("enabled").getValue(Boolean.class);
                Integer interval = configSnapshot.child("interval").getValue(Integer.class);
                Long lastSent = configSnapshot.child("last_sent").getValue(Long.class);

                if (enabled != null && enabled && interval != null && lastSent != null) {
                    long currentTime = System.currentTimeMillis();
                    long diff = currentTime - lastSent;
                    long intervalMillis = interval.longValue() * 60 * 60 * 1000;

                    if (diff >= intervalMillis) {
                        checkAndNotifyDues(messId, userId);
                        configSnapshot.getRef().child("last_sent").setValue(currentTime);
                    }
                }
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

                if (totalDue > 0) {
                    String memberUid = memberSnap.getKey();
                    String name = memberSnap.child("name").getValue(String.class);
                    String title = "Pending Due Reminder";
                    String message = "Hi " + name + ", you have a pending due of ₹" + String.format(Locale.getDefault(), "%.2f", totalDue) + ". Please clear it soon.";

                    // Push to Firebase for in-app record
                    DatabaseReference notiRef = FirebaseDatabase.getInstance().getReference().child(messId).child("notifications").push();
                    String id = notiRef.getKey();
                    NotificationModel n = new NotificationModel(id, title, message, "DUE_REMINDER", memberUid, System.currentTimeMillis());
                    if (id != null) {
                        notiRef.setValue(n);
                    }

                    // Show system notification ONLY for the current logged-in user
                    if (memberUid != null && memberUid.equals(currentUserId)) {
                        showNotification(title, message);
                    }
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
