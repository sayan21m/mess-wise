package com.srtech.messwise.utils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public final class DateUtils {

    private static final SimpleDateFormat MEAL_DAY =
            new SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH);
    private static final SimpleDateFormat MONTH_KEY =
            new SimpleDateFormat("yyyy-MM", Locale.ENGLISH);
    private static final SimpleDateFormat TIMESTAMP_DISPLAY =
            new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH);
    private static final SimpleDateFormat SLOT_TIME =
            new SimpleDateFormat("hh:mm a", Locale.ENGLISH);

    private DateUtils() {
    }

    public static String formatMealDay(Date date) {
        synchronized (MEAL_DAY) {
            return MEAL_DAY.format(date);
        }
    }

    public static String formatMealDay(long millis) {
        return formatMealDay(new Date(millis));
    }

    public static String formatMonthKey(Date date) {
        synchronized (MONTH_KEY) {
            return MONTH_KEY.format(date);
        }
    }

    public static String formatMonthKey(long millis) {
        return formatMonthKey(new Date(millis));
    }

    public static String formatTimestamp(long millis) {
        synchronized (TIMESTAMP_DISPLAY) {
            return TIMESTAMP_DISPLAY.format(new Date(millis));
        }
    }

    public static Date parseMealDay(String key) {
        if (key == null || key.isEmpty()) return null;
        try {
            synchronized (MEAL_DAY) {
                return MEAL_DAY.parse(key);
            }
        } catch (ParseException e) {
            return null;
        }
    }

    public static boolean isSameMonthYear(Date date, int month, int year) {
        if (date == null) return false;
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        return cal.get(Calendar.MONTH) == month && cal.get(Calendar.YEAR) == year;
    }

    public static boolean isOlderThanMonth(Calendar target, Calendar cutoff) {
        if (target.get(Calendar.YEAR) < cutoff.get(Calendar.YEAR)) return true;
        return target.get(Calendar.YEAR) == cutoff.get(Calendar.YEAR)
                && target.get(Calendar.MONTH) < cutoff.get(Calendar.MONTH);
    }

    public static String formatSlotTime(int hour, int minute) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, minute);
        synchronized (SLOT_TIME) {
            return SLOT_TIME.format(cal.getTime());
        }
    }

    public static int parseSlotTimeMinutes(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) return -1;
        try {
            Date date;
            synchronized (SLOT_TIME) {
                date = SLOT_TIME.parse(timeStr.trim());
            }
            if (date == null) return -1;
            Calendar cal = Calendar.getInstance();
            cal.setTime(date);
            return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
        } catch (ParseException e) {
            return -1;
        }
    }

    public static int daysRemainingInMonth(Calendar calendar) {
        int today = calendar.get(Calendar.DAY_OF_MONTH);
        int lastDay = calendar.getActualMaximum(Calendar.DAY_OF_MONTH);
        return Math.max(0, lastDay - today + 1);
    }

    public static int daysBetween(Calendar from, Calendar to) {
        Calendar start = (Calendar) from.clone();
        Calendar end = (Calendar) to.clone();
        zeroTime(start);
        zeroTime(end);
        long diffMs = end.getTimeInMillis() - start.getTimeInMillis();
        return (int) (diffMs / (24L * 60L * 60L * 1000L));
    }

    private static void zeroTime(Calendar calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
    }
}
