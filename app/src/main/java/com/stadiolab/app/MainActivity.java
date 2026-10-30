package com.stadiolab.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

public class MainActivity extends Activity {
    private ActivationGuard activationGuard;

    @Override
    protected void onResume() {
        super.onResume();
        if (activationGuard != null) activationGuard.start();
    }

    @Override
    protected void onPause() {
        if (activationGuard != null) activationGuard.stop();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (activationGuard != null) activationGuard.destroy();
        super.onDestroy();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        activationGuard = new ActivationGuard(this);

        Button btnLive = findViewById(R.id.btnLive);
        Button btnEvents = findViewById(R.id.btnEvents);
        Button btnMovies = findViewById(R.id.btnMovies);
        Button btnSeries = findViewById(R.id.btnSeries);

        View.OnClickListener listener = v -> {
            Button button = (Button) v;
            Toast.makeText(
                    MainActivity.this,
                    button.getText(),
                    Toast.LENGTH_SHORT
            ).show();
        };

        btnLive.setOnClickListener(v -> {
            if (activationGuard == null || !activationGuard.isAuthorized()) {
                Toast.makeText(this, "Verificando activación...", Toast.LENGTH_SHORT).show();
                return;
            }
            startActivity(new Intent(MainActivity.this, LiveTvActivity.class));
        });
        btnEvents.setOnClickListener(v -> { if (activationGuard == null || !activationGuard.isAuthorized()) { Toast.makeText(this, "Verificando activación...", Toast.LENGTH_SHORT).show(); return; } startActivity(new Intent(MainActivity.this, EventsActivity.class)); });
        btnMovies.setOnClickListener(listener);
        btnSeries.setOnClickListener(listener);

        btnLive.requestFocus();
    }
}
