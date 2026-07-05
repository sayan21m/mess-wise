package com.srtech.messwise.utils;

import android.content.SharedPreferences;

import com.google.firebase.database.DataSnapshot;

public final class PermissionUtils {

    private PermissionUtils() {
    }

    public static void syncFromMessSnapshot(
            SharedPreferences prefs,
            DataSnapshot messSnapshot,
            String userId) {

        if (prefs == null || messSnapshot == null || userId == null) {
            return;
        }

        String adminUid = messSnapshot.child("admin_uid").getValue(String.class);
        boolean isMainAdmin = userId.equals(adminUid);

        DataSnapshot userSnap = messSnapshot.child("member").child(userId);
        Boolean adminFlag = userSnap.child("is_admin").getValue(Boolean.class);
        boolean isAdmin = isMainAdmin || (adminFlag != null && adminFlag);
        prefs.edit().putBoolean("isAdmin", isAdmin).apply();

        String role = userSnap.child("role").getValue(String.class);
        if (role == null) {
            role = isAdmin ? "Admin" : "Member";
        }

        if (isAdmin || "Admin".equals(role)) {
            savePermissions(prefs, true, true, true, true);
            return;
        }

        DataSnapshot permSnap = messSnapshot.child("config").child("role_permissions").child(role);
        boolean members = Boolean.TRUE.equals(permSnap.child("manage_members").getValue(Boolean.class));
        boolean meals = Boolean.TRUE.equals(permSnap.child("manage_meals").getValue(Boolean.class));
        boolean finances = Boolean.TRUE.equals(permSnap.child("manage_finances").getValue(Boolean.class));
        boolean summary = Boolean.TRUE.equals(permSnap.child("view_meal_summary").getValue(Boolean.class));
        savePermissions(prefs, members, meals, finances, summary);
    }

    public static void savePermissions(
            SharedPreferences prefs,
            boolean members,
            boolean meals,
            boolean finances,
            boolean summary) {

        prefs.edit()
                .putBoolean("perm_manage_members", members)
                .putBoolean("perm_manage_meals", meals)
                .putBoolean("perm_manage_finances", finances)
                .putBoolean("perm_view_meal_summary", summary)
                .apply();
    }
}
