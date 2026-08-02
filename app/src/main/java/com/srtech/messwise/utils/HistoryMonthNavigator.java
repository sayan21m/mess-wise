package com.srtech.messwise.utils;

import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.srtech.messwise.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.function.ToLongFunction;

/**
 * Month strip (&lt; Month Year &gt;) for full transaction history dialogs.
 * Months are ordered oldest → newest; starts on the current / newest month.
 */
public final class HistoryMonthNavigator {

    public interface Listener {
        void onMonthSelected(@NonNull String monthKey);
    }

    private final View bar;
    private final TextView label;
    private final View btnPrev;
    private final View btnNext;
    private final Listener listener;

    private final List<String> months = new ArrayList<>();
    private int index = 0;

    private HistoryMonthNavigator(@NonNull View root, @NonNull Listener listener) {
        this.bar = root.findViewById(R.id.monthNavBar);
        this.label = root.findViewById(R.id.tvHistoryMonth);
        this.btnPrev = root.findViewById(R.id.btnPrevMonth);
        this.btnNext = root.findViewById(R.id.btnNextMonth);
        this.listener = listener;

        if (bar != null) bar.setVisibility(View.VISIBLE);
        if (btnPrev != null) btnPrev.setOnClickListener(v -> move(-1));
        if (btnNext != null) btnNext.setOnClickListener(v -> move(1));
    }

    @NonNull
    public static HistoryMonthNavigator bind(@NonNull View root, @NonNull Listener listener) {
        return new HistoryMonthNavigator(root, listener);
    }

    public static void hide(@NonNull View root) {
        View bar = root.findViewById(R.id.monthNavBar);
        if (bar != null) bar.setVisibility(View.GONE);
    }

    @NonNull
    public static String monthKeyOf(long millis) {
        if (millis <= 0) millis = System.currentTimeMillis();
        return new SimpleDateFormat("yyyy-MM", Locale.ENGLISH).format(new Date(millis));
    }

    @NonNull
    public static String currentMonthKey() {
        return monthKeyOf(System.currentTimeMillis());
    }

    @NonNull
    public static <T> List<String> collectMonthKeys(@NonNull List<T> items,
                                                    @NonNull ToLongFunction<T> timestampFn) {
        TreeSet<String> keys = new TreeSet<>();
        for (T item : items) {
            long ts = timestampFn.applyAsLong(item);
            if (ts > 0) keys.add(monthKeyOf(ts));
        }
        keys.add(currentMonthKey());
        return new ArrayList<>(keys); // ascending
    }

    @NonNull
    public static <T> List<T> filterByMonth(@NonNull List<T> items,
                                            @NonNull ToLongFunction<T> timestampFn,
                                            @NonNull String monthKey) {
        List<T> out = new ArrayList<>();
        for (T item : items) {
            if (monthKey.equals(monthKeyOf(timestampFn.applyAsLong(item)))) {
                out.add(item);
            }
        }
        return out;
    }

    /** Rebuild month list; keeps selection when possible, else prefers current month. */
    public void setMonths(@NonNull List<String> monthKeys, @Nullable String preferKey) {
        months.clear();
        if (monthKeys.isEmpty()) {
            months.add(currentMonthKey());
        } else {
            months.addAll(monthKeys);
        }

        String target = preferKey;
        if (target == null || !months.contains(target)) {
            String current = currentMonthKey();
            target = months.contains(current) ? current : months.get(months.size() - 1);
        }
        index = months.indexOf(target);
        if (index < 0) index = months.size() - 1;
        applyUi(true);
    }

    @NonNull
    public String getSelectedMonthKey() {
        if (months.isEmpty()) return currentMonthKey();
        return months.get(Math.max(0, Math.min(index, months.size() - 1)));
    }

    private void move(int delta) {
        int next = index + delta;
        if (next < 0 || next >= months.size()) return;
        index = next;
        applyUi(true);
    }

    private void applyUi(boolean notify) {
        if (label != null) {
            label.setText(SettlementDialogHelper.monthDisplay(getSelectedMonthKey()));
        }
        if (btnPrev != null) {
            btnPrev.setEnabled(index > 0);
            btnPrev.setAlpha(index > 0 ? 1f : 0.35f);
        }
        if (btnNext != null) {
            boolean canNext = index < months.size() - 1;
            btnNext.setEnabled(canNext);
            btnNext.setAlpha(canNext ? 1f : 0.35f);
        }
        if (notify && listener != null) {
            listener.onMonthSelected(getSelectedMonthKey());
        }
    }
}
