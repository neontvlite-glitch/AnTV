package com.stadiolab.app;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ActivationGuard {

    private static final String STATUS_URL =
            "https://panel.n30ntvlt.com/api/stadiolab/device/status";

    private static final long CHECK_INTERVAL_MS = 30000;

    private final Activity activity;
    private final Handler handler =
            new Handler(Looper.getMainLooper());

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    private boolean stopped = false;
    private boolean checking = false;
    private boolean authorized = false;

    private final Runnable periodicCheck = new Runnable() {
        @Override
        public void run() {
            if (!stopped) {
                verify();
            }
        }
    };

    public ActivationGuard(Activity activity) {
        this.activity = activity;
    }

    public void start() {
        stopped = false;
        authorized = false;
        handler.removeCallbacks(periodicCheck);
        verify();
    }

    public void stop() {
        stopped = true;
        authorized = false;
        handler.removeCallbacks(periodicCheck);
    }

    public boolean isAuthorized() {
        return authorized && !stopped;
    }
    private Runnable onAuthorized;

    public void setOnAuthorized(Runnable callback) {
        onAuthorized = callback;
    }

    private Runnable onUnauthorized;

    public void setOnUnauthorized(Runnable callback) {
        onUnauthorized = callback;
    }

    private void verify() {
        if (stopped || checking) return;

        checking = true;
        authorized = false;

        SharedPreferences prefs = activity.getSharedPreferences(
                "stadiolab_activation", Activity.MODE_PRIVATE
        );

        String installationId =
                prefs.getString("installationId", "");
        String token =
                prefs.getString("deviceToken", "");

        if (installationId.isEmpty() || token.isEmpty()) {
            checking = false;
            redirectToActivation();
            return;
        }

        executor.execute(() -> {
            boolean valid = false;

            HttpURLConnection connection = null;

            try {
                connection = (HttpURLConnection)
                        new URL(STATUS_URL).openConnection();

                connection.setRequestMethod("POST");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                connection.setDoOutput(true);
                connection.setRequestProperty(
                        "Content-Type",
                        "application/json; charset=utf-8"
                );

                JSONObject body = new JSONObject();
                body.put("identificador", installationId);
                body.put("token", token);

                try (OutputStream out = connection.getOutputStream()) {
                    out.write(body.toString().getBytes(
                            StandardCharsets.UTF_8
                    ));
                }

                if (connection.getResponseCode() == 200) {
                    ByteArrayOutputStream buffer =
                            new ByteArrayOutputStream();

                    try (InputStream in = connection.getInputStream()) {
                        byte[] bytes = new byte[4096];
                        int count;

                        while ((count = in.read(bytes)) != -1) {
                            buffer.write(bytes, 0, count);
                        }
                    }

                    JSONObject response = new JSONObject(
                            new String(
                                    buffer.toByteArray(),
                                    StandardCharsets.UTF_8
                            )
                    );

                    valid = response.optBoolean("ok", false)
                            && response.optBoolean("activo", false);
                }

            } catch (Exception ignored) {
                valid = false;

            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }

            final boolean result = valid;

            handler.post(() -> {
                checking = false;

                if (stopped || activity.isFinishing()
                        || activity.isDestroyed()) {
                    return;
                }

                authorized = result;

                if (result && onAuthorized != null) {
                    onAuthorized.run();
                }

                if (!result) {
                    if (onUnauthorized != null) {
                        onUnauthorized.run();
                    }
                    redirectToActivation();
                } else {
                    handler.removeCallbacks(periodicCheck);
                    handler.postDelayed(
                            periodicCheck, CHECK_INTERVAL_MS
                    );
                }
            });
        });
    }
    private void redirectToActivation() {
        if (stopped || activity.isFinishing()
                || activity.isDestroyed()) {
            return;
        }

        stopped = true;
        authorized = false;
        handler.removeCallbacks(periodicCheck);

        Intent intent = new Intent(
                activity,
                ActivationActivity.class
        );

        intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_CLEAR_TASK
        );

        activity.startActivity(intent);
        activity.finish();
    }

    public void destroy() {
        stop();
        executor.shutdownNow();
    }
}
