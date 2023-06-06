package com.kodebite.libppf;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.kodebite.libppf.databinding.ActivityLockerBinding;

public class LockerActivity extends AppCompatActivity {

    ActivityLockerBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLockerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.patternBtn.setOnClickListener(v -> {
            startActivity(new Intent(this, PatternActivity.class));
        });

        binding.pinLockView.attachIndicatorDots(binding.indicatorDots);
        binding.indicatorDots.setCount(binding.pinLockView.getPinLength());

    }

}