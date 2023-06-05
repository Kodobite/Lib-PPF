package com.kodebite.libppf;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;

import com.kodebite.libppf.databinding.ActivityLockerBinding;

public class LockerActivity extends AppCompatActivity {

    ActivityLockerBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLockerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
    }

}