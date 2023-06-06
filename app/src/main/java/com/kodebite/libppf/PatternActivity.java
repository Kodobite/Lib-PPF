package com.kodebite.libppf;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.kodebite.libppf.databinding.ActivityPatternBinding;

public class PatternActivity extends AppCompatActivity {
    ActivityPatternBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPatternBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.pinBtn.setOnClickListener(v -> {
            startActivity(new Intent(this, LockerActivity.class));
        });


    }
}