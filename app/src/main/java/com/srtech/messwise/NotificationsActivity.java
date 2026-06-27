package com.srtech.messwise;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.srtech.messwise.data_models.NotificationModel;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NotificationsActivity extends AppCompatActivity {

    private Toolbar toolbar;
    private View emptyState;
    private RecyclerView rvNotifications;
    private NotificationAdapter adapter;
    private List<NotificationModel> notificationList = new ArrayList<>();
    
    private String userId, messId;
    private FirebaseDatabase db;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_notifications);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            getWindow().getAttributes().layoutInDisplayCutoutMode =
                    android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        getWindow().setNavigationBarColor(android.graphics.Color.TRANSPARENT);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            return insets;
        });

        initViews();
        setupFirebase();
        loadNotifications();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        emptyState = findViewById(R.id.emptyState);
        rvNotifications = findViewById(R.id.rvNotifications);

        rvNotifications.setLayoutManager(new LinearLayoutManager(this));
        adapter = new NotificationAdapter(notificationList);
        rvNotifications.setAdapter(adapter);
    }

    private void setupFirebase() {
        db = FirebaseDatabase.getInstance();
        prefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        userId = prefs.getString("userId", null);
        messId = prefs.getString("messId", null);
    }

    private void loadNotifications() {
        if (messId == null) return;

        // Single listener to manage both persistent and dynamic notifications
        db.getReference().child(messId).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (isDestroyed() || isFinishing()) return;
                
                notificationList.clear();

                // 0. Recalculate dynamic permission
                if (userId == null || userId.isEmpty()) {
                    userId = prefs.getString("userId", "");
                }
                
                // If userId is still empty, we can't filter correctly
                if (userId.isEmpty()) {
                    updateUI();
                    return;
                }
                
                DataSnapshot userNode = snapshot.child("member").child(userId);
                String userRole = userNode.child("role").getValue(String.class);
                final boolean isUserMainAdmin = userId.equals(snapshot.child("admin_uid").getValue(String.class));
                
                boolean canUserViewSummary = isUserMainAdmin;
                if (!isUserMainAdmin && userRole != null) {
                    Boolean summaryPerm = snapshot.child("config").child("role_permissions").child(userRole).child("view_meal_summary").getValue(Boolean.class);
                    if (summaryPerm != null && summaryPerm) canUserViewSummary = true;
                    // Also check if specifically "Meal Manager" - often defaults to true
                    if (userRole.equals("Meal Manager")) canUserViewSummary = true;
                }

                // 1. Dynamic Meal Summary (Only if allowed)
                if (canUserViewSummary) {
                    DataSnapshot membersSnap = snapshot.child("member");
                    int leaveCount = 0;
                    for (DataSnapshot mSnap : membersSnap.getChildren()) {
                        Boolean onLeave = mSnap.child("next_meal_leave").getValue(Boolean.class);
                        if (onLeave != null && onLeave) {
                            leaveCount++;
                        }
                    }

                    // Always show for managers so they can access the "Taking Meal" list,
                    // but mention if there are no leaves.
                    String slotName = "Upcoming Meal";
                    DataSnapshot slotsSnap = snapshot.child("meal_slots");
                    if (slotsSnap.exists()) {
                        slotName = getUpcomingSlotName(slotsSnap);
                    }

                    String summaryMsg = leaveCount > 0 
                        ? leaveCount + " members are on leave for " + slotName + "."
                        : "All members are taking " + slotName + " today!";

                    notificationList.add(new NotificationModel(
                            "meal_summary",
                            slotName + " Summary",
                            summaryMsg,
                            "MEAL_SUMMARY",
                            System.currentTimeMillis()
                    ));
                }

                // 2. Persistent Notifications from DB
                DataSnapshot persistentNotis = snapshot.child("notifications");
                for (DataSnapshot ds : persistentNotis.getChildren()) {
                    NotificationModel model = ds.getValue(NotificationModel.class);
                    if (model != null) {
                        model.setId(ds.getKey());
                        
                        // Filter Logic:
                        // 1. If it's targeted specifically to THIS user
                        // 2. If it's a global notification (no targetUid)
                        String tUid = model.getTargetUid();
                        boolean isForMe = tUid != null && !tUid.isEmpty() && tUid.equals(userId);
                        boolean isGlobal = tUid == null || tUid.isEmpty();
                        
                        if (isForMe || isGlobal) {
                            notificationList.add(model);
                        }
                    }
                }

                // Sort by time descending
                Collections.sort(notificationList, (n1, n2) -> Long.compare(n2.getTimestamp(), n1.getTimestamp()));
                updateUI();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private String getUpcomingSlotName(DataSnapshot slotsSnapshot) {
        Calendar now = Calendar.getInstance();
        int currentMins = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.ENGLISH);

        String bestSlotName = "Next Meal";
        int minDiff = Integer.MAX_VALUE;

        for (DataSnapshot ds : slotsSnapshot.getChildren()) {
            String timeStr = ds.child("time").getValue(String.class);
            String name = ds.child("name").getValue(String.class);
            if (timeStr != null && name != null) {
                try {
                    Date d = sdf.parse(timeStr);
                    if (d != null) {
                        Calendar cal = Calendar.getInstance();
                        cal.setTime(d);
                        int slotMins = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
                        int diff = slotMins - currentMins;

                        if (diff > 0 && diff < minDiff) {
                            minDiff = diff;
                            bestSlotName = name;
                        }
                    }
                } catch (Exception ignored) {}
            }
        }
        
        if (bestSlotName.equals("Next Meal") && slotsSnapshot.hasChildren()) {
            bestSlotName = slotsSnapshot.getChildren().iterator().next().child("name").getValue(String.class);
        }
        return bestSlotName;
    }

    private void updateUI() {
        if (notificationList.isEmpty()) {
            rvNotifications.setVisibility(View.GONE);
            emptyState.setVisibility(View.VISIBLE);
        } else {
            rvNotifications.setVisibility(View.VISIBLE);
            emptyState.setVisibility(View.GONE);
            adapter.notifyDataSetChanged();
        }
    }

    private void showSummaryDialog() {
        // Logic to show the existing summary dialog
        db.getReference().child(messId).child("member").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                int total = (int) snapshot.getChildrenCount();
                ArrayList<String> leaves = new ArrayList<>();
                ArrayList<String> takings = new ArrayList<>();
                ArrayList<String> uids = new ArrayList<>();
                for (DataSnapshot m : snapshot.getChildren()) {
                    Boolean onLeave = m.child("next_meal_leave").getValue(Boolean.class);
                    if (onLeave != null && onLeave) {
                        leaves.add(m.child("name").getValue(String.class));
                        uids.add(m.getKey());
                    } else {
                        takings.add(m.child("name").getValue(String.class));
                    }
                }
                displaySummaryDialog(total, leaves, takings, uids);
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void displaySummaryDialog(int total, ArrayList<String> leaveNames, ArrayList<String> takingNames, ArrayList<String> leaveUids) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_daily_summary, null);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dialog.getWindow().setGravity(android.view.Gravity.BOTTOM);
            dialog.getWindow().getAttributes().windowAnimations = R.style.DialogAnimation;
        }

        TextView tvTotalTaking = dialogView.findViewById(R.id.tvTotalTakingMeal);
        TextView tvTotalOnLeave = dialogView.findViewById(R.id.tvTotalOnLeave);
        TextView tvListTitle = dialogView.findViewById(R.id.tvListTitle);
        RecyclerView rv = dialogView.findViewById(R.id.rvLeavesList);
        View btnClear = dialogView.findViewById(R.id.btnClearNotifications);
        View btnTaking = dialogView.findViewById(R.id.btnShowTaking);
        View btnLeave = dialogView.findViewById(R.id.btnShowLeave);
        View empty = dialogView.findViewById(R.id.dialogEmptyState);
        TextView tvEmpty = dialogView.findViewById(R.id.tvEmptyMsg);

        tvTotalOnLeave.setText(String.valueOf(leaveNames.size()));
        tvTotalTaking.setText(String.valueOf(takingNames.size()));

        ArrayList<String> current = new ArrayList<>(leaveNames);
        rv.setLayoutManager(new LinearLayoutManager(this));
        
        RecyclerView.Adapter<RecyclerView.ViewHolder> adapter = new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup p, int vt) {
                return new RecyclerView.ViewHolder(LayoutInflater.from(p.getContext()).inflate(android.R.layout.simple_list_item_1, p, false)) {};
            }
            @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int pos) {
                TextView t = h.itemView.findViewById(android.R.id.text1);
                t.setText(current.get(pos));
                t.setTextColor(android.graphics.Color.WHITE);
                t.setTextSize(14f);
            }
            @Override public int getItemCount() { return current.size(); }
        };
        rv.setAdapter(adapter);

        Runnable updateEmpty = () -> {
            if (current.isEmpty()) {
                rv.setVisibility(View.GONE);
                empty.setVisibility(View.VISIBLE);
                tvEmpty.setText(tvListTitle.getText().toString().contains("LEAVE") ? "No one is on leave!" : "No one is eating?");
            } else {
                rv.setVisibility(View.VISIBLE);
                empty.setVisibility(View.GONE);
            }
        };
        updateEmpty.run();

        btnTaking.setOnClickListener(v -> {
            current.clear(); current.addAll(takingNames);
            tvListTitle.setText("MEMBERS TAKING MEAL");
            adapter.notifyDataSetChanged();
            updateEmpty.run();
        });

        btnLeave.setOnClickListener(v -> {
            current.clear(); current.addAll(leaveNames);
            tvListTitle.setText("MEMBERS ON LEAVE");
            adapter.notifyDataSetChanged();
            updateEmpty.run();
        });

        btnClear.setOnClickListener(v -> {
            for (String uid : leaveUids) {
                db.getReference().child(messId).child("member").child(uid).child("next_meal_leave").removeValue();
                db.getReference().child(messId).child("member").child(uid).child("pending_leave_slot").removeValue();
            }
            dialog.dismiss();
        });

        dialogView.findViewById(R.id.btnCloseSummary).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.ViewHolder> {
        private List<NotificationModel> list;
        NotificationAdapter(List<NotificationModel> list) { this.list = list; }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_notification_card, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            NotificationModel model = list.get(position);
            holder.tvTitle.setText(model.getTitle());
            holder.tvMessage.setText(model.getMessage());
            
            long diff = System.currentTimeMillis() - model.getTimestamp();
            String timeStr = diff < 3600000 ? (diff / 60000 + "m ago") : (diff / 3600000 + "h ago");
            holder.tvTime.setText(timeStr);

            if (model.getType().equals("MEAL_SUMMARY")) {
                holder.ivIcon.setImageResource(R.drawable.ic_summary);
                holder.ivIcon.setColorFilter(getColor(R.color.teal_accent));
                holder.itemView.setOnClickListener(v -> showSummaryDialog());
            } else if (model.getType().equals("DUE_REMINDER")) {
                holder.ivIcon.setImageResource(R.drawable.ic_receipt);
                holder.ivIcon.setColorFilter(getColor(R.color.dark_error));
            }
        }

        @Override
        public int getItemCount() { return list.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvTitle, tvMessage, tvTime;
            ImageView ivIcon;
            ViewHolder(View v) {
                super(v);
                tvTitle = v.findViewById(R.id.tvTitle);
                tvMessage = v.findViewById(R.id.tvMessage);
                tvTime = v.findViewById(R.id.tvTime);
                ivIcon = v.findViewById(R.id.ivIcon);
            }
        }
    }
}
