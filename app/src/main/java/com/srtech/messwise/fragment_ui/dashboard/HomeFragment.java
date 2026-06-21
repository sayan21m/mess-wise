package com.srtech.messwise.fragment_ui.dashboard;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.srtech.messwise.R;
import com.srtech.messwise.ui.AttendanceActivity;
import com.srtech.messwise.ui.SettingsActivity;

public class HomeFragment extends Fragment {

    private ImageView profile;
    private CardView mealAttendance;
    private SharedPreferences prefs;
    private String userId, messId;

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
}