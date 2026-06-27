package com.srtech.messwise;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

public class BaseActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // Apply theme before super.onCreate
        int theme = getSharedPreferences("UserPrefs", MODE_PRIVATE)
                .getInt("pref_theme", AppCompatDelegate.MODE_NIGHT_YES);
        AppCompatDelegate.setDefaultNightMode(theme);
        
        super.onCreate(savedInstanceState);
    }
}
