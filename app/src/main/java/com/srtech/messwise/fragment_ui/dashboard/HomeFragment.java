package com.srtech.messwise.fragment_ui.dashboard;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.FirebaseDatabase;
import com.srtech.messwise.R;
import com.srtech.messwise.ui.AttendanceActivity;
import com.srtech.messwise.ui.SettingsActivity;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class HomeFragment extends Fragment {

    private ImageView profile;
    private CardView mealAttendance;
    private SharedPreferences prefs;
    private String userId, messId;
    FirebaseDatabase db;
    TextView totalMeal;

    public HomeFragment() {
        // Required empty public constructor
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.home_fragment, container, false);

        prefs = requireActivity().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        userId = prefs.getString("userId", null);
        messId = prefs.getString("messId", null);

        profile = view.findViewById(R.id.profile);
        mealAttendance = view.findViewById(R.id.mealAttendance);
        totalMeal = view.findViewById(R.id.totalMeal);

        db = FirebaseDatabase.getInstance();

        setTotalMeal();

        profile.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), SettingsActivity.class);
            startActivity(intent);
        });

        if (mealAttendance != null) {
            mealAttendance.setOnClickListener(v -> {
                Intent intent = new Intent(getActivity(), AttendanceActivity.class);
                intent.putExtra("userId", userId);
                intent.putExtra("messId", messId);
                startActivity(intent);
            });
        }

        return view;
    }

    private void setTotalMeal() {
        String currentMonth = new SimpleDateFormat("MMM", Locale.ENGLISH)
                .format(Calendar.getInstance().getTime());

        SimpleDateFormat inputFormat = new SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH);
        SimpleDateFormat monthFormat = new SimpleDateFormat("MMM", Locale.ENGLISH);

        db.getReference().child(messId).child("member").child(userId).child("meal_count_history").get()
                .addOnSuccessListener(snapshot -> {
                    int total = 0;

                    for (DataSnapshot item : snapshot.getChildren()) {
                        String key = item.getKey();
                        Integer value = item.getValue(Integer.class);

                        if (key == null || value == null) continue;

                        try {
                            String monthFromKey = monthFormat.format(inputFormat.parse(key));

                            if (monthFromKey.equals(currentMonth)) {
                                total += value;
                            }
                        } catch (Exception e) {
                            Log.e("SGT", "Invalid date key: " + key, e);
                        }
                    }

                    totalMeal.setText(total + " Meals");
                })
                .addOnFailureListener(e -> Log.e("SGT", "setTotalMeal failed", e));
    }
}