package com.srtech.messwise.utils;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Month-end settlement: members with positive due pay members with surplus (negative due).
 * Transfers are greedily matched so each debtor only sees their assigned payees/amounts.
 * Member UPI IDs are AES-GCM encrypted before storage at {@code member/{uid}/upi_id}.
 */
public final class SettlementUtils {

    public static final String METHOD_UPI = "upi";
    public static final String METHOD_OFFLINE = "offline";
    public static final String STATUS_COMPLETED = "completed";
    public static final String UPI_FIELD = "upi_id";
    /** Deterministic lock path: settlement_transfers/{monthKey_fromUid_toUid} */
    public static final String TRANSFERS_NODE = "settlement_transfers";

    /** Callback for {@link #recordSettlement}. */
    public interface RecordListener {
        void onSuccess();
        void onAlreadySettled();
        void onFailure();
    }

    private SettlementUtils() {}

    @NonNull
    public static String transferKey(@NonNull String monthKey,
                                     @NonNull String fromUid,
                                     @NonNull String toUid) {
        return monthKey + "_" + fromUid + "_" + toUid;
    }

    /** True if this payer→receiver pair for the month was already recorded (lock or legacy history). */
    public static boolean isTransferSettled(@NonNull DataSnapshot messSnapshot,
                                            @NonNull String monthKey,
                                            @NonNull String fromUid,
                                            @NonNull String toUid) {
        if (messSnapshot.child(TRANSFERS_NODE).child(transferKey(monthKey, fromUid, toUid)).exists()) {
            return true;
        }
        for (DataSnapshot s : messSnapshot.child("settlements").getChildren()) {
            String payer = s.child("payerUid").getValue(String.class);
            String receiver = s.child("receiverUid").getValue(String.class);
            String mk = s.child("monthKey").getValue(String.class);
            String status = s.child("status").getValue(String.class);
            if (fromUid.equals(payer) && toUid.equals(receiver) && monthKey.equals(mk)
                    && (status == null || STATUS_COMPLETED.equalsIgnoreCase(status))) {
                return true;
            }
        }
        return false;
    }

    public static class Payee {
        public final String uid;
        public final String name;
        public final double amount;
        @Nullable public final String upiId;

        public Payee(String uid, String name, double amount, @Nullable String upiId) {
            this.uid = uid;
            this.name = name;
            this.amount = amount;
            this.upiId = upiId;
        }

        public boolean hasUpi() {
            String normalized = normalizeUpi(upiId);
            return normalized != null && normalized.matches("^[\\w.\\-]{2,256}@[\\w.\\-]{2,64}$");
        }
    }

    /** One directed payment after matching debtors → creditors. */
    public static class Transfer {
        public final String fromUid;
        public final String fromName;
        public final String toUid;
        public final String toName;
        public final double amount;

        public Transfer(String fromUid, String fromName, String toUid, String toName, double amount) {
            this.fromUid = fromUid;
            this.fromName = fromName;
            this.toUid = toUid;
            this.toName = toName;
            this.amount = amount;
        }
    }

    public static class SettlementSnapshot {
        public final String monthKey;
        public final List<Payee> creditors = new ArrayList<>();
        public final List<Payee> debtors = new ArrayList<>();
        public final List<Payee> myPayments = new ArrayList<>();
        public final List<Payee> myReceipts = new ArrayList<>();
        public final List<Transfer> allTransfers = new ArrayList<>();
        public double myDue;
        public double unmatchedDebt;
        public String myUid;

        public SettlementSnapshot(String monthKey) {
            this.monthKey = monthKey;
        }

        public boolean hasPending() {
            return !creditors.isEmpty() || !debtors.isEmpty();
        }

        public List<Payee> rowsForMe() {
            if (myDue > 0.5) return myPayments;
            if (myDue < -0.5) return myReceipts;
            return Collections.emptyList();
        }
    }

    @Nullable
    public static String readUpiId(@NonNull String messId, @NonNull DataSnapshot memberSnap) {
        return UpiCrypto.decrypt(messId, memberSnap.child(UPI_FIELD).getValue(String.class));
    }

    /** @deprecated Prefer {@link #readUpiId(String, DataSnapshot)} with messId for AES decrypt. */
    @Nullable
    public static String readUpiId(@NonNull DataSnapshot memberSnap) {
        String messId = memberSnap.getRef().getParent() != null
                && memberSnap.getRef().getParent().getParent() != null
                ? memberSnap.getRef().getParent().getParent().getKey()
                : null;
        if (messId == null) {
            // Fallback: treat as legacy plaintext only
            return normalizeUpi(memberSnap.child(UPI_FIELD).getValue(String.class));
        }
        return readUpiId(messId, memberSnap);
    }

    @Nullable
    public static String normalizeUpi(@Nullable String upi) {
        if (upi == null) return null;
        String t = upi.trim();
        if (t.isEmpty()) return null;
        // Never treat ciphertext as a displayable VPA
        if (UpiCrypto.isEncryptedPayload(t)) return null;
        return t;
    }

    /** Save or clear a member's UPI ID (AES-encrypted) under {@code member/{uid}/upi_id}. */
    public static void setMemberUpi(@NonNull String messId,
                                    @NonNull String uid,
                                    @Nullable String upi,
                                    @Nullable Runnable onSuccess,
                                    @Nullable Runnable onFailure) {
        if (messId.isEmpty() || uid.isEmpty()) {
            if (onFailure != null) onFailure.run();
            return;
        }
        String normalized = normalizeUpi(upi);
        if (upi != null && !upi.trim().isEmpty() && !isValidUpiId(upi)) {
            if (onFailure != null) onFailure.run();
            return;
        }
        try {
            String toStore = normalized == null ? null : UpiCrypto.encrypt(messId, normalized);
            FirebaseDatabase.getInstance().getReference()
                    .child(messId).child("member").child(uid).child(UPI_FIELD)
                    .setValue(toStore)
                    .addOnSuccessListener(unused -> {
                        if (onSuccess != null) onSuccess.run();
                    })
                    .addOnFailureListener(e -> {
                        if (onFailure != null) onFailure.run();
                    });
        } catch (Exception e) {
            if (onFailure != null) onFailure.run();
        }
    }

    /** Build settlement from due_history; decrypts Firebase UPI for payees. */
    @NonNull
    public static SettlementSnapshot fromMessSnapshot(@NonNull DataSnapshot messSnapshot,
                                                      @NonNull String monthKey,
                                                      @Nullable String currentUserId) {
        SettlementSnapshot snap = new SettlementSnapshot(monthKey);
        snap.myUid = currentUserId;

        String messId = messSnapshot.getKey();
        if (messId == null) messId = "";

        Map<String, String> upiByUid = new HashMap<>();
        DataSnapshot members = messSnapshot.child("member");
        for (DataSnapshot mSnap : members.getChildren()) {
            String uid = mSnap.getKey();
            if (uid == null) continue;
            String name = mSnap.child("name").getValue(String.class);
            if (name == null || name.trim().isEmpty()) name = "Member";

            String upi = readUpiId(messId, mSnap);
            if (upi != null) upiByUid.put(uid, upi);

            double due = FinanceUtils.parseAmount(mSnap.child("due_history").child(monthKey).getValue());

            if (currentUserId != null && uid.equals(currentUserId)) {
                snap.myDue = due;
            }

            if (due > 0.5) {
                snap.debtors.add(new Payee(uid, name, due, upi));
            } else if (due < -0.5) {
                snap.creditors.add(new Payee(uid, name, Math.abs(due), upi));
            }
        }

        Collections.sort(snap.creditors, (a, b) -> Double.compare(b.amount, a.amount));
        Collections.sort(snap.debtors, (a, b) -> Double.compare(a.amount, b.amount));

        List<Payee> matchDebtors = scaleDebtorsToAvailableCredit(snap.debtors, snap.creditors);
        for (Transfer t : matchTransfers(matchDebtors, snap.creditors)) {
            // Hide pairs already recorded so payer and receiver can't both confirm again
            if (!isTransferSettled(messSnapshot, monthKey, t.fromUid, t.toUid)) {
                snap.allTransfers.add(t);
            }
        }

        if (currentUserId != null) {
            for (Transfer t : snap.allTransfers) {
                if (currentUserId.equals(t.fromUid)) {
                    // Payee is the creditor — use their Firebase UPI for one-tap pay
                    snap.myPayments.add(new Payee(t.toUid, t.toName, t.amount, upiByUid.get(t.toUid)));
                } else if (currentUserId.equals(t.toUid)) {
                    snap.myReceipts.add(new Payee(t.fromUid, t.fromName, t.amount, upiByUid.get(t.fromUid)));
                }
            }
            if (snap.myDue > 0.5) {
                double assigned = 0;
                for (Payee p : snap.myPayments) assigned += p.amount;
                snap.unmatchedDebt = Math.max(0, Math.round((snap.myDue - assigned) * 100.0) / 100.0);
            }
        }
        return snap;
    }

    @NonNull
    static List<Payee> scaleDebtorsToAvailableCredit(@NonNull List<Payee> debtors,
                                                     @NonNull List<Payee> creditors) {
        if (debtors.isEmpty() || creditors.isEmpty()) return debtors;
        double totalDebt = 0;
        double totalCredit = 0;
        for (Payee d : debtors) totalDebt += d.amount;
        for (Payee c : creditors) totalCredit += c.amount;
        if (totalDebt <= 0.5 || totalCredit <= 0.5 || totalDebt <= totalCredit + 0.5) {
            return debtors;
        }
        double scale = totalCredit / totalDebt;
        List<Payee> scaled = new ArrayList<>(debtors.size());
        for (Payee d : debtors) {
            double amt = Math.round(d.amount * scale * 100.0) / 100.0;
            if (amt > 0.009) {
                scaled.add(new Payee(d.uid, d.name, amt, d.upiId));
            }
        }
        return scaled;
    }

    @NonNull
    public static List<Transfer> matchTransfers(@NonNull List<Payee> debtors,
                                                @NonNull List<Payee> creditors) {
        List<Transfer> transfers = new ArrayList<>();
        if (debtors.isEmpty() || creditors.isEmpty()) return transfers;

        double[] debtLeft = new double[debtors.size()];
        double[] creditLeft = new double[creditors.size()];
        for (int i = 0; i < debtors.size(); i++) debtLeft[i] = debtors.get(i).amount;
        for (int i = 0; i < creditors.size(); i++) creditLeft[i] = creditors.get(i).amount;

        int cIdx = 0;
        for (int d = 0; d < debtors.size(); d++) {
            Payee debtor = debtors.get(d);
            while (debtLeft[d] > 0.5 && cIdx < creditors.size()) {
                while (cIdx < creditors.size() && creditLeft[cIdx] <= 0.5) cIdx++;
                if (cIdx >= creditors.size()) break;

                Payee creditor = creditors.get(cIdx);
                double pay = Math.min(debtLeft[d], creditLeft[cIdx]);
                pay = Math.round(pay * 100.0) / 100.0;
                if (pay <= 0.009) break;

                transfers.add(new Transfer(
                        debtor.uid, debtor.name,
                        creditor.uid, creditor.name,
                        pay));
                debtLeft[d] -= pay;
                creditLeft[cIdx] -= pay;
                if (creditLeft[cIdx] <= 0.5) cIdx++;
            }
        }
        return transfers;
    }

    @NonNull
    public static Payee withUpi(@NonNull Payee payee, @Nullable String upiId) {
        return new Payee(payee.uid, payee.name, payee.amount, normalizeUpi(upiId));
    }

    public static boolean isValidUpiId(@Nullable String upi) {
        if (upi == null) return false;
        String t = upi.trim();
        if (t.isEmpty()) return true; // empty = clear / optional
        return t.matches("^[\\w.\\-]{2,256}@[\\w.\\-]{2,64}$");
    }

    @Nullable
    public static Intent buildUpiPayIntent(@NonNull String upiId,
                                           @NonNull String payeeName,
                                           double amount,
                                           @NonNull String note) {
        if (amount <= 0 || TextUtils.isEmpty(upiId)) return null;
        String am = String.format(Locale.US, "%.2f", amount);
        Uri uri = Uri.parse("upi://pay").buildUpon()
                .appendQueryParameter("pa", upiId.trim())
                .appendQueryParameter("pn", payeeName)
                .appendQueryParameter("am", am)
                .appendQueryParameter("cu", "INR")
                .appendQueryParameter("tn", note)
                .build();
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(uri);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        return intent;
    }

    /**
     * Best-effort check for UPI handlers. On Android 11+ {@code resolveActivity} is often
     * null even when GPay/PhonePe are installed — prefer launching via chooser and catching
     * {@link android.content.ActivityNotFoundException} instead of blocking on this alone.
     */
    public static boolean canLaunchUpi(@NonNull Context context, @NonNull Intent upiIntent) {
        try {
            PackageManager pm = context.getPackageManager();
            List<ResolveInfo> apps = pm.queryIntentActivities(upiIntent, PackageManager.MATCH_DEFAULT_ONLY);
            if (apps != null && !apps.isEmpty()) return true;
            return upiIntent.resolveActivity(pm) != null;
        } catch (Exception e) {
            // Visibility / OEM quirks — let the caller try startActivity
            return true;
        }
    }

    /**
     * Record a completed settlement payment (UPI or offline).
     * Uses a deterministic transfer lock so payer and receiver cannot both apply the same transfer.
     */
    public static void recordSettlement(@NonNull String messId,
                                        @NonNull String fromUid,
                                        @NonNull String toUid,
                                        double amount,
                                        @NonNull String monthKey,
                                        @NonNull String paymentMethod,
                                        @Nullable Runnable onSuccess,
                                        @Nullable Runnable onFailure) {
        recordSettlement(messId, fromUid, toUid, amount, monthKey, paymentMethod, new RecordListener() {
            @Override public void onSuccess() { if (onSuccess != null) onSuccess.run(); }
            @Override public void onAlreadySettled() { if (onFailure != null) onFailure.run(); }
            @Override public void onFailure() { if (onFailure != null) onFailure.run(); }
        });
    }

    public static void recordSettlement(@NonNull String messId,
                                        @NonNull String fromUid,
                                        @NonNull String toUid,
                                        double amount,
                                        @NonNull String monthKey,
                                        @NonNull String paymentMethod,
                                        @Nullable RecordListener listener) {
        if (amount <= 0 || messId.isEmpty() || fromUid.equals(toUid)) {
            if (listener != null) listener.onFailure();
            return;
        }
        String method = METHOD_OFFLINE.equals(paymentMethod) ? METHOD_OFFLINE : METHOD_UPI;
        String key = transferKey(monthKey, fromUid, toUid);

        DatabaseReference root = FirebaseDatabase.getInstance().getReference().child(messId);
        root.get().addOnSuccessListener(messSnap -> {
            if (isTransferSettled(messSnap, monthKey, fromUid, toUid)) {
                if (listener != null) listener.onAlreadySettled();
                return;
            }

            DataSnapshot fromMember = messSnap.child("member").child(fromUid);
            DataSnapshot toMember = messSnap.child("member").child(toUid);
            if (!fromMember.exists() || !toMember.exists()) {
                if (listener != null) listener.onFailure();
                return;
            }

            String fromName = fromMember.child("name").getValue(String.class);
            if (fromName == null || fromName.trim().isEmpty()) fromName = "Member";
            String toName = toMember.child("name").getValue(String.class);
            if (toName == null || toName.trim().isEmpty()) toName = "Member";

            final String finalFromName = fromName;
            final String finalToName = toName;
            final double fromBal = FinanceUtils.parseAmount(
                    fromMember.child("monthly_balance").child(monthKey).getValue());
            final double toBal = FinanceUtils.parseAmount(
                    toMember.child("monthly_balance").child(monthKey).getValue());
            final double fromDue = FinanceUtils.parseAmount(
                    fromMember.child("due_history").child(monthKey).getValue());
            final double toDue = FinanceUtils.parseAmount(
                    toMember.child("due_history").child(monthKey).getValue());

            DatabaseReference lockRef = root.child(TRANSFERS_NODE).child(key);
            lockRef.runTransaction(new Transaction.Handler() {
                @NonNull
                @Override
                public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                    if (currentData.getValue() != null) {
                        return Transaction.abort();
                    }
                    Map<String, Object> marker = new HashMap<>();
                    marker.put("payerUid", fromUid);
                    marker.put("receiverUid", toUid);
                    marker.put("monthKey", monthKey);
                    marker.put("amount", amount);
                    marker.put("paymentMethod", method);
                    marker.put("status", STATUS_COMPLETED);
                    marker.put("timestampMillis", System.currentTimeMillis());
                    currentData.setValue(marker);
                    return Transaction.success(currentData);
                }

                @Override
                public void onComplete(@Nullable DatabaseError error,
                                       boolean committed,
                                       @Nullable DataSnapshot currentData) {
                    if (error != null) {
                        if (listener != null) listener.onFailure();
                        return;
                    }
                    if (!committed) {
                        if (listener != null) listener.onAlreadySettled();
                        return;
                    }

                    long now = System.currentTimeMillis();
                    String timestamp = DateUtils.formatTimestamp(now);
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("member/" + fromUid + "/monthly_balance/" + monthKey, fromBal + amount);
                    updates.put("member/" + toUid + "/monthly_balance/" + monthKey, toBal - amount);
                    updates.put("member/" + fromUid + "/due_history/" + monthKey, fromDue - amount);
                    updates.put("member/" + toUid + "/due_history/" + monthKey, toDue + amount);

                    String txId = root.child("cash_in").push().getKey();
                    if (txId != null) {
                        Map<String, Object> tx = new HashMap<>();
                        tx.put("transactionId", txId);
                        tx.put("userId", fromUid);
                        tx.put("userName", finalFromName);
                        tx.put("amount", amount);
                        tx.put("timestamp", timestamp);
                        tx.put("timestampMillis", now);
                        tx.put("status", "settlement");
                        tx.put("type", "settlement_to_" + toUid);
                        tx.put("balanceMonthKey", monthKey);
                        tx.put("paymentMethod", method);
                        tx.put("transferKey", key);
                        tx.put("messId", messId);
                        updates.put("cash_in/" + txId, tx);
                    }

                    String settlementId = root.child("settlements").push().getKey();
                    if (settlementId != null) {
                        Map<String, Object> hist = new HashMap<>();
                        hist.put("settlementId", settlementId);
                        hist.put("payerUid", fromUid);
                        hist.put("payerName", finalFromName);
                        hist.put("receiverUid", toUid);
                        hist.put("receiverName", finalToName);
                        hist.put("amount", amount);
                        hist.put("monthKey", monthKey);
                        hist.put("paymentMethod", method);
                        hist.put("status", STATUS_COMPLETED);
                        hist.put("timestamp", timestamp);
                        hist.put("timestampMillis", now);
                        hist.put("transferKey", key);
                        hist.put("messId", messId);
                        updates.put("settlements/" + settlementId, hist);
                    }

                    root.updateChildren(updates)
                            .addOnSuccessListener(unused -> {
                                FinanceUtils.updateAllMemberDues(messId);
                                if (listener != null) listener.onSuccess();
                            })
                            .addOnFailureListener(e -> {
                                // Release lock so a retry can succeed
                                lockRef.removeValue();
                                if (listener != null) listener.onFailure();
                            });
                }
            });
        }).addOnFailureListener(e -> {
            if (listener != null) listener.onFailure();
        });
    }

    /** @deprecated Prefer {@link #recordSettlement} with an explicit payment method. */
    public static void recordExternalPayment(@NonNull String messId,
                                             @NonNull String fromUid,
                                             @NonNull String toUid,
                                             double amount,
                                             @NonNull String monthKey,
                                             @Nullable Runnable onSuccess,
                                             @Nullable Runnable onFailure) {
        recordSettlement(messId, fromUid, toUid, amount, monthKey, METHOD_UPI, onSuccess, onFailure);
    }
}
