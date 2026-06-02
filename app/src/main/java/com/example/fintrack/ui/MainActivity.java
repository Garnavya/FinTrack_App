package com.example.fintrack.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import com.example.fintrack.R;
import com.google.android.material.navigation.NavigationView;

public class MainActivity extends AppCompatActivity {

    private DrawerLayout drawerLayout;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize SharedPreferences for saving the user's name
        prefs = getSharedPreferences("ClearSlatePrefs", Context.MODE_PRIVATE);

        // Setup Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setTitle("FinTrack Dashboard");

        drawerLayout = findViewById(R.id.drawer_layout);
        NavigationView navigationView = findViewById(R.id.nav_view);

        // Bind the Hamburger icon to the Drawer
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.open, R.string.close);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        // THIS FIXES THE HAMBURGER ICON COLOR
        toggle.getDrawerArrowDrawable().setColor(getResources().getColor(R.color.colorTextPrimary, getTheme()));

        // --- DYNAMIC USER PROFILE LOGIC ---
        View headerView = navigationView.getHeaderView(0);
        TextView textUserName = headerView.findViewById(R.id.textUserName);

        // Load the saved name, defaulting to Garnavya Rawal on first launch
        String savedName = prefs.getString("USER_PROFILE_NAME", "Garnavya Rawal");
        textUserName.setText(savedName);

        // Open an Edit Dialog when the header is tapped
        headerView.setOnClickListener(v -> {
            androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
            builder.setTitle("Update Profile Name");

            final android.widget.EditText input = new android.widget.EditText(this);
            input.setText(textUserName.getText().toString());
            builder.setView(input);

            builder.setPositiveButton("Save", (dialog, which) -> {
                String newName = input.getText().toString().trim();
                if (!newName.isEmpty()) {
                    prefs.edit().putString("USER_PROFILE_NAME", newName).apply();
                    textUserName.setText(newName);
                }
            });
            builder.setNegativeButton("Cancel", null);
            builder.show();
        });

        // Handle Sidebar Clicks
        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            Fragment selectedFragment = null;

            if (id == R.id.nav_dashboard) {
                selectedFragment = new DashboardFragment();
                toolbar.setTitle("FinTrack Dashboard");
            } else if (id == R.id.nav_analytics) {
                selectedFragment = new AnalyticsFragment();
                toolbar.setTitle("Analytics & Charts");
            } else if (id == R.id.nav_export) {
                selectedFragment = new ExportFragment();
                toolbar.setTitle("PDF Reports");
            }

            if (selectedFragment != null) {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, selectedFragment)
                        .commit();
            }

            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        });

        // Load Dashboard by default on startup
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new DashboardFragment())
                    .commit();
            navigationView.setCheckedItem(R.id.nav_dashboard);
        }
    }

    // Handle back button behavior for the drawer
    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }
}