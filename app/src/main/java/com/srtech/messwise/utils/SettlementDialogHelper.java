package com.srtech.messwise.utils;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.FirebaseDatabase;
import com.srtech.messwise.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Fullscreen month-settlement UI: one-tap UPI (Firebase upi_id) + offline mark-paid,
 * with post-UPI confirmation via secure prefs (UPI apps rarely return results).
 */
public final class SettlementDialogHelper {

    private static final String PREF_PENDING_TO = "pending_settle_to";
    private static final String PREF_PENDING_TO_NAME = "pending_settle_to_name";
    private static final String PREF_PENDING_AMOUNT = "pending_settle_amount";
    private static final String PREF_PENDING_MONTH = "pending_settle_month";
    private static final String PREF_PENDING_FROM = "pending_settle_from";
    private static final String PREF_PENDING_MESS = "pending_settle_mess";
    private static final String PREF_PENDING_METHOD = "pending_settle_method";

    private SettlementDialogHelper() {}

    public static String previousMonthKey() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.MONTH, -1);
        return new SimpleDateFormat("yyyy-MM", Locale.ENGLISH).format(cal.getTime());
    }

    public static String currentMonthKey() {
        return new SimpleDateFormat("yyyy-MM", Locale.ENGLISH).format(Calendar.getInstance().getTime());
    }

    public static String monthDisplay(String monthKey) {
        try {
            return new SimpleDateFormat("MMMM yyyy", Locale.ENGLISH)
                    .format(new SimpleDateFormat("yyyy-MM", Locale.ENGLISH).parse(monthKey));
        } catch (Exception e) {
            return monthKey;
        }
    }

    @Nullable
    public static String resolveSettleableMonthKey(@NonNull DataSnapshot messSnapshot,
                                                   @Nullable String userId) {
        String current = currentMonthKey();
        String prev = previousMonthKey();

        SettlementUtils.SettlementSnapshot prevSnap =
                SettlementUtils.fromMessSnapshot(messSnapshot, prev, userId);
        if (!prevSnap.rowsForMe().isEmpty() || prevSnap.unmatchedDebt > 0.5) {
            return prev;
        }

        java.util.TreeSet<String> pastMonths = new java.util.TreeSet<>(java.util.Collections.reverseOrder());
        DataSnapshot members = messSnapshot.child("member");
        for (DataSnapshot m : members.getChildren()) {
            for (DataSnapshot month : m.child("due_history").getChildren()) {
                String key = month.getKey();
                if (key != null && key.compareTo(current) < 0) {
                    pastMonths.add(key);
                }
            }
        }
        for (String key : pastMonths) {
            if (key.equals(prev)) continue;
            SettlementUtils.SettlementSnapshot snap =
                    SettlementUtils.fromMessSnapshot(messSnapshot, key, userId);
            if (!snap.rowsForMe().isEmpty() || snap.unmatchedDebt > 0.5) {
                return key;
            }
        }
        return prev;
    }

    public static void show(@NonNull FragmentActivity activity,
                            @NonNull String messId,
                            @NonNull String userId,
                            @NonNull String monthKey) {
        FirebaseDatabase.getInstance().getReference().child(messId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (activity.isFinishing() || activity.isDestroyed()) return;
                    SettlementUtils.SettlementSnapshot snap =
                            SettlementUtils.fromMessSnapshot(snapshot, monthKey, userId);
                    showLoaded(activity, messId, userId, snap);
                })
                .addOnFailureListener(e ->
                        Toast.makeText(activity, R.string.settlement_failed, Toast.LENGTH_SHORT).show());
    }

    public static void showForSettleableMonth(@NonNull FragmentActivity activity,
                                              @NonNull String messId,
                                              @NonNull String userId) {
        FirebaseDatabase.getInstance().getReference().child(messId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (activity.isFinishing() || activity.isDestroyed()) return;
                    String monthKey = resolveSettleableMonthKey(snapshot, userId);
                    if (monthKey == null) monthKey = previousMonthKey();
                    SettlementUtils.SettlementSnapshot snap =
                            SettlementUtils.fromMessSnapshot(snapshot, monthKey, userId);
                    if (snap.rowsForMe().isEmpty() && snap.unmatchedDebt <= 0.5
                            && Math.abs(snap.myDue) <= 0.5) {
                        Toast.makeText(activity, R.string.due_settle_none, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    showLoaded(activity, messId, userId, snap);
                })
                .addOnFailureListener(e ->
                        Toast.makeText(activity, R.string.settlement_failed, Toast.LENGTH_SHORT).show());
    }

    private static void showLoaded(@NonNull FragmentActivity activity,
                                   @NonNull String messId,
                                   @NonNull String userId,
                                   @NonNull SettlementUtils.SettlementSnapshot snap) {
        View dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_month_settlement, null);
        Dialog dialog = new Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.setContentView(dialogView);

        ViewCompat.setOnApplyWindowInsetsListener(dialogView, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left + v.getPaddingLeft(), bars.top + v.getPaddingTop(),
                    bars.right + v.getPaddingRight(), bars.bottom + v.getPaddingBottom());
            return WindowInsetsCompat.CONSUMED;
        });

        TextView title = dialogView.findViewById(R.id.tvDialogTitle);
        TextView status = dialogView.findViewById(R.id.tvSettlementStatus);
        TextView empty = dialogView.findViewById(R.id.tvEmpty);
        RecyclerView rv = dialogView.findViewById(R.id.rvPayees);
        ProgressBar progress = dialogView.findViewById(R.id.progressSettlement);

        title.setText(activity.getString(R.string.settlement_month_title, monthDisplay(snap.monthKey)));

        String amt = String.format(Locale.getDefault(), "%.0f", Math.abs(snap.myDue));
        if (snap.myDue > 0.5) {
            String statusText = activity.getString(R.string.settlement_you_owe, amt);
            if (snap.unmatchedDebt > 0.5) {
                statusText = statusText + "\n" + activity.getString(R.string.settlement_shortfall,
                        String.format(Locale.getDefault(), "%.0f", snap.unmatchedDebt));
            }
            status.setText(statusText);
        } else if (snap.myDue < -0.5) {
            status.setText(activity.getString(R.string.settlement_you_receive, amt));
        } else {
            status.setText(R.string.summary_settlement_subtitle);
        }

        List<SettlementUtils.Payee> payees = new ArrayList<>(snap.rowsForMe());
        boolean isDebtor = snap.myDue > 0.5 && !snap.myPayments.isEmpty();
        boolean isCreditor = snap.myDue < -0.5 && !snap.myReceipts.isEmpty();

        if (payees.isEmpty()) {
            empty.setVisibility(View.VISIBLE);
            if (snap.myDue > 0.5 && snap.creditors.isEmpty()) {
                empty.setText(R.string.settlement_no_surplus);
            } else {
                empty.setText(R.string.summary_settlement_empty);
            }
            rv.setVisibility(View.GONE);
        } else {
            empty.setVisibility(View.GONE);
            rv.setVisibility(View.VISIBLE);
            rv.setLayoutManager(new LinearLayoutManager(activity));
            rv.setAdapter(new PayeeAdapter(payees, isDebtor, isCreditor, userId,
                    new RowActions() {
                        @Override
                        public void onPayUpi(SettlementUtils.Payee payee, double amount) {
                            launchUpiPay(activity, messId, userId, payee, amount, snap.monthKey, dialog);
                        }

                        @Override
                        public void onMarkOffline(SettlementUtils.Payee payee, double amount, boolean receiverConfirming) {
                            String fromUid = receiverConfirming ? payee.uid : userId;
                            String toUid = receiverConfirming ? userId : payee.uid;
                            String otherName = payee.name;
                            confirmOffline(activity, messId, fromUid, toUid, otherName, amount,
                                    snap.monthKey, dialog, progress, receiverConfirming);
                        }
                    }));
        }

        dialogView.findViewById(R.id.btnClose).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private static void launchUpiPay(@NonNull FragmentActivity activity,
                                     @NonNull String messId,
                                     @NonNull String userId,
                                     @NonNull SettlementUtils.Payee payee,
                                     double amount,
                                     @NonNull String monthKey,
                                     @Nullable Dialog settlementDialog) {
        if (!payee.hasUpi()) {
            Toast.makeText(activity, R.string.settlement_upi_not_configured, Toast.LENGTH_SHORT).show();
            return;
        }
        Intent upi = SettlementUtils.buildUpiPayIntent(
                payee.upiId, payee.name, amount, "MessWise Settlement");
        if (upi == null) {
            Toast.makeText(activity, R.string.settlement_upi_not_configured, Toast.LENGTH_SHORT).show();
            return;
        }
        // Do not block on resolveActivity — it often false-negatives on Android 11+ even when
        // GPay / PhonePe are installed. Launch chooser and handle ActivityNotFoundException.
        savePending(activity, messId, userId, payee, amount, monthKey, SettlementUtils.METHOD_UPI);
        if (settlementDialog != null) settlementDialog.dismiss();
        try {
            activity.startActivity(Intent.createChooser(upi, activity.getString(R.string.summary_pay_via_upi)));
        } catch (android.content.ActivityNotFoundException e) {
            clearPending(activity);
            Toast.makeText(activity, R.string.settlement_upi_missing_app, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            clearPending(activity);
            Toast.makeText(activity, R.string.settlement_upi_missing_app, Toast.LENGTH_SHORT).show();
        }
    }

    private static void confirmOffline(@NonNull FragmentActivity activity,
                                       @NonNull String messId,
                                       @NonNull String fromUid,
                                       @NonNull String toUid,
                                       @NonNull String otherName,
                                       double amount,
                                       @NonNull String monthKey,
                                       @Nullable Dialog settlementDialog,
                                       @Nullable ProgressBar progress,
                                       boolean receiverConfirming) {
        String amtText = String.format(Locale.getDefault(), "%.0f", amount);
        String message = receiverConfirming
                ? activity.getString(R.string.settlement_offline_receive_confirm_msg, amtText, otherName)
                : activity.getString(R.string.settlement_offline_pay_confirm_msg, amtText, otherName);

        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.settlement_mark_offline)
                .setMessage(message)
                .setPositiveButton(R.string.settlement_confirm_offline, (d, w) -> {
                    if (progress != null) progress.setVisibility(View.VISIBLE);
                    SettlementUtils.recordSettlement(messId, fromUid, toUid, amount, monthKey,
                            SettlementUtils.METHOD_OFFLINE,
                            new SettlementUtils.RecordListener() {
                                @Override
                                public void onSuccess() {
                                    if (progress != null) progress.setVisibility(View.GONE);
                                    if (settlementDialog != null) settlementDialog.dismiss();
                                    Toast.makeText(activity, R.string.settlement_cleared, Toast.LENGTH_SHORT).show();
                                }

                                @Override
                                public void onAlreadySettled() {
                                    if (progress != null) progress.setVisibility(View.GONE);
                                    if (settlementDialog != null) settlementDialog.dismiss();
                                    Toast.makeText(activity, R.string.settlement_already_done, Toast.LENGTH_SHORT).show();
                                }

                                @Override
                                public void onFailure() {
                                    if (progress != null) progress.setVisibility(View.GONE);
                                    Toast.makeText(activity, R.string.settlement_failed, Toast.LENGTH_SHORT).show();
                                }
                            });
                })
                .setNegativeButton(R.string.common_cancel, null)
                .show();
    }

    public static boolean hasPendingConfirmation(@NonNull Context context) {
        SharedPreferences prefs = SecurityUtils.getSecurePrefs(context);
        return prefs.getString(PREF_PENDING_TO, null) != null
                && prefs.getFloat(PREF_PENDING_AMOUNT, 0f) > 0f;
    }

    public static void checkPendingConfirmation(@NonNull FragmentActivity activity) {
        if (!hasPendingConfirmation(activity)) return;
        if (activity.isFinishing() || activity.isDestroyed()) return;

        SharedPreferences prefs = SecurityUtils.getSecurePrefs(activity);
        String toUid = prefs.getString(PREF_PENDING_TO, null);
        String toName = prefs.getString(PREF_PENDING_TO_NAME, "Member");
        String monthKey = prefs.getString(PREF_PENDING_MONTH, currentMonthKey());
        String fromUid = prefs.getString(PREF_PENDING_FROM, null);
        String messId = prefs.getString(PREF_PENDING_MESS, null);
        String method = prefs.getString(PREF_PENDING_METHOD, SettlementUtils.METHOD_UPI);
        float amount = prefs.getFloat(PREF_PENDING_AMOUNT, 0f);
        if (fromUid == null || messId == null || toUid == null || amount <= 0) {
            clearPending(activity);
            return;
        }

        String amtText = String.format(Locale.getDefault(), "%.0f", amount);
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.settlement_pay_confirm_title)
                .setMessage(activity.getString(R.string.settlement_pay_confirm_msg, amtText, toName))
                .setPositiveButton(R.string.settlement_paid, (d, w) -> {
                    SettlementUtils.recordSettlement(messId, fromUid, toUid, amount, monthKey, method,
                            new SettlementUtils.RecordListener() {
                                @Override
                                public void onSuccess() {
                                    clearPending(activity);
                                    Toast.makeText(activity, R.string.settlement_cleared, Toast.LENGTH_SHORT).show();
                                }

                                @Override
                                public void onAlreadySettled() {
                                    clearPending(activity);
                                    Toast.makeText(activity, R.string.settlement_already_done, Toast.LENGTH_SHORT).show();
                                }

                                @Override
                                public void onFailure() {
                                    Toast.makeText(activity, R.string.settlement_failed, Toast.LENGTH_SHORT).show();
                                }
                            });
                })
                .setNegativeButton(R.string.settlement_not_paid, (d, w) -> clearPending(activity))
                .setCancelable(false)
                .show();
    }

    private static void savePending(Context context, String messId, String fromUid,
                                    SettlementUtils.Payee payee, double amount, String monthKey,
                                    String method) {
        SecurityUtils.getSecurePrefs(context).edit()
                .putString(PREF_PENDING_MESS, messId)
                .putString(PREF_PENDING_FROM, fromUid)
                .putString(PREF_PENDING_TO, payee.uid)
                .putString(PREF_PENDING_TO_NAME, payee.name)
                .putString(PREF_PENDING_MONTH, monthKey)
                .putString(PREF_PENDING_METHOD, method)
                .putFloat(PREF_PENDING_AMOUNT, (float) amount)
                .apply();
    }

    private static void clearPending(Context context) {
        SecurityUtils.getSecurePrefs(context).edit()
                .remove(PREF_PENDING_MESS)
                .remove(PREF_PENDING_FROM)
                .remove(PREF_PENDING_TO)
                .remove(PREF_PENDING_TO_NAME)
                .remove(PREF_PENDING_MONTH)
                .remove(PREF_PENDING_METHOD)
                .remove(PREF_PENDING_AMOUNT)
                .apply();
    }

    private interface RowActions {
        void onPayUpi(SettlementUtils.Payee payee, double amount);
        void onMarkOffline(SettlementUtils.Payee payee, double amount, boolean receiverConfirming);
    }

    private static class PayeeAdapter extends RecyclerView.Adapter<PayeeAdapter.VH> {
        private final List<SettlementUtils.Payee> list;
        private final boolean isDebtor;
        private final boolean isCreditor;
        private final String userId;
        private final RowActions actions;

        PayeeAdapter(List<SettlementUtils.Payee> list, boolean isDebtor, boolean isCreditor,
                     String userId, RowActions actions) {
            this.list = list;
            this.isDebtor = isDebtor;
            this.isCreditor = isCreditor;
            this.userId = userId;
            this.actions = actions;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_settlement_payee, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            SettlementUtils.Payee p = list.get(position);
            h.tvName.setText(p.name);
            h.tvAmount.setText(String.format(Locale.getDefault(), "₹%,.0f", p.amount));
            String initials = p.name.length() >= 2
                    ? p.name.substring(0, 2).toUpperCase(Locale.getDefault())
                    : p.name.toUpperCase(Locale.getDefault());
            h.tvInitials.setText(initials);

            boolean isSelf = p.uid.equals(userId);
            h.btnPay.setVisibility(View.GONE);
            h.btnOffline.setVisibility(View.GONE);

            if (isSelf) {
                h.tvHint.setVisibility(View.VISIBLE);
                h.tvHint.setText(R.string.settlement_cannot_pay_self);
                return;
            }

            if (isDebtor) {
                if (p.hasUpi()) {
                    h.tvHint.setVisibility(View.VISIBLE);
                    h.tvHint.setText(p.upiId);
                    h.btnPay.setVisibility(View.VISIBLE);
                    h.btnPay.setOnClickListener(v -> actions.onPayUpi(p, p.amount));
                } else {
                    h.tvHint.setVisibility(View.VISIBLE);
                    h.tvHint.setText(R.string.summary_no_upi_hint);
                }
                h.btnOffline.setVisibility(View.VISIBLE);
                h.btnOffline.setText(R.string.settlement_mark_offline);
                h.btnOffline.setOnClickListener(v -> actions.onMarkOffline(p, p.amount, false));
            } else if (isCreditor) {
                h.tvHint.setVisibility(View.VISIBLE);
                h.tvHint.setText(R.string.settlement_expected_from);
                h.btnOffline.setVisibility(View.VISIBLE);
                h.btnOffline.setText(R.string.settlement_confirm_received);
                h.btnOffline.setOnClickListener(v -> actions.onMarkOffline(p, p.amount, true));
            } else {
                h.tvHint.setVisibility(View.GONE);
            }
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            final TextView tvName, tvAmount, tvHint, tvInitials;
            final MaterialButton btnPay, btnOffline;

            VH(@NonNull View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tvName);
                tvAmount = itemView.findViewById(R.id.tvAmount);
                tvHint = itemView.findViewById(R.id.tvHint);
                tvInitials = itemView.findViewById(R.id.tvInitials);
                btnPay = itemView.findViewById(R.id.btnPayUpi);
                btnOffline = itemView.findViewById(R.id.btnMarkOffline);
            }
        }
    }
}
