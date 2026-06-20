package com.srtech.messwise;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.srtech.messwise.admin_ui.MealAdminActivity;
import com.srtech.messwise.fragment_ui.dashboard.HomeFragment;
import com.srtech.messwise.ui.AdminWheelMenuView;

import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {

    private FrameLayout adminWheelContainer;
    private AdminWheelMenuView adminWheelMenu;
    private BottomNavigationView bottomNav;
    private boolean isWheelOpen = false, isAdmin = false;
    private SharedPreferences prefs;
    private String userId, messId, messName;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getWindow().getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
//            v.setPadding(0, 0, 0, 0);
            return insets;
        });

        bottomNav = findViewById(R.id.bottomNav);

        if (savedInstanceState == null) {
            loadFragment(new HomeFragment());
        }

        prefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        userId = prefs.getString("userId", null);
        messId = prefs.getString("messId", null);
        messName = prefs.getString("messName", null);
        isAdmin = prefs.getBoolean("isAdmin", false);

        adminWheelContainer = findViewById(R.id.adminWheelContainer);
        adminWheelMenu = findViewById(R.id.adminWheelMenu);

        adminWheelMenu.setOnWheelItemClickListener(index -> {
            closeAdminWheel();

            switch (index) {
//                case 0:
//                    startActivity(new Intent(this, ManageMembersActivity.class));
//                    break;
                case 1:
                    startActivity(new Intent(this, MealAdminActivity.class));
                    break;
//                case 2:
//                    startActivity(new Intent(this, ReportsActivity.class));
//                    break;
//                case 3:
//                    startActivity(new Intent(this, SettingsActivity.class));
//                    break;
            }
        });

        adminWheelContainer.setOnClickListener(v -> closeAdminWheel());

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.adminFragment) {
                if (isAdmin) {
                    toggleAdminWheel();
                    return false;
                } else {
                    Toast.makeText(this, "You are not an admin!", Toast.LENGTH_SHORT).show();
                }
            } else {
                closeAdminWheel();

                if (id == R.id.homeFragment) {
                    loadFragment(new HomeFragment());
                    return true;
                }
            }
            return false;
        });
    }

    private void toggleAdminWheel() {
        if (isWheelOpen) closeAdminWheel();
        else openAdminWheel();
    }

    private void openAdminWheel() {
        isWheelOpen = true;
        adminWheelContainer.setVisibility(View.VISIBLE);
        adminWheelContainer.setAlpha(0f);
        adminWheelContainer.animate().alpha(1f).setDuration(180).start();
        adminWheelMenu.startOpenAnimation();
    }

    private void closeAdminWheel() {
        if (!isWheelOpen) return;
        isWheelOpen = false;
        adminWheelContainer.animate()
                .alpha(0f)
                .setDuration(150)
                .withEndAction(() -> adminWheelContainer.setVisibility(View.GONE))
                .start();
    }

    private void loadFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }
}