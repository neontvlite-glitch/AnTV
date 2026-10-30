package com.stadiolab.app;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ActivationActivity extends Activity {

    private static final String BASE_URL =
            "https://panel.n30ntvlt.com/api/stadiolab/device";

    private static final long POLL_MS = 10000;

    private TextView txtCode;
    private TextView txtStatus;
    private SharedPreferences prefs;

    private final Handler handler =
            new Handler(Looper.getMainLooper());

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    private String installationId;
    private String deviceToken;
    private boolean entering = false;

    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            if (!isFinishing() && !entering) {
                checkStatus();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_activation);

        txtCode = findViewById(R.id.txtActivationCode);
        txtStatus = findViewById(R.id.txtActivationStatus);

        prefs = getSharedPreferences(
                "stadiolab_activation", MODE_PRIVATE
        );

        installationId =
                prefs.getString("installationId", "");

        deviceToken =
                prefs.getString("deviceToken", "");

        if (installationId.isEmpty()) {
            installationId = UUID.randomUUID().toString();

            prefs.edit()
                    .putString("installationId", installationId)
                    .commit();
        }

        String savedCode =
                prefs.getString("activationCode", "");

        if (!savedCode.isEmpty()) {
            txtCode.setText(savedCode);
        }

        if (deviceToken.isEmpty()) {
            registerDevice();
        } else {
            checkStatus();
        }
    }
    private JSONObject post(String endpoint, JSONObject data)
            throws Exception {

        HttpURLConnection connection =
                (HttpURLConnection) new URL(
                        BASE_URL + endpoint
                ).openConnection();

        try {
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setDoOutput(true);

            connection.setRequestProperty(
                    "Content-Type",
                    "application/json; charset=utf-8"
            );

            try (OutputStream out = connection.getOutputStream()) {
                out.write(
                        data.toString().getBytes(StandardCharsets.UTF_8)
                );
            }

            int httpCode = connection.getResponseCode();

            InputStream input = httpCode >= 400
                    ? connection.getErrorStream()
                    : connection.getInputStream();

            if (input == null) {
                throw new Exception("HTTP " + httpCode);
            }

            ByteArrayOutputStream buffer =
                    new ByteArrayOutputStream();

            byte[] chunk = new byte[4096];
            int count;

            try (InputStream in = input) {
                while ((count = in.read(chunk)) != -1) {
                    buffer.write(chunk, 0, count);
                }
            }

            JSONObject result = new JSONObject(
                    new String(
                            buffer.toByteArray(),
                            StandardCharsets.UTF_8
                    )
            );

            if (httpCode >= 400 ||
                    !result.optBoolean("ok", false)) {
                throw new Exception("HTTP " + httpCode);
            }

            return result;

        } finally {
            connection.disconnect();
        }
    }

    private void registerDevice() {
        txtStatus.setText("Registrando dispositivo...");

        executor.execute(() -> {
            try {
                JSONObject body = new JSONObject();

                body.put("identificador", installationId);
                body.put("nombre", android.os.Build.MODEL);

                JSONObject response = post("/register", body);

                String code = response.optString("codigo", "");
                String token = response.optString("token", "");

                if (code.isEmpty() || token.isEmpty()) {
                    throw new Exception("Registro incompleto");
                }

                boolean saved = prefs.edit()
                        .putString("activationCode", code)
                        .putString("deviceToken", token)
                        .commit();

                if (!saved) {
                    throw new Exception("No se pudieron guardar los datos");
                }

                deviceToken = token;

                runOnUiThread(() -> {
                    if (isFinishing()) return;

                    txtCode.setText(code);
                    txtStatus.setText("Pendiente de activación");
                    scheduleCheck();
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isFinishing()) return;

                    txtStatus.setText(
                            "No se pudo registrar. Reintentando..."
                    );

                    handler.postDelayed(() -> {
                        if (!isFinishing()) {
                            registerDevice();
                        }
                    }, POLL_MS);
                });
            }
        });
    }
    private void checkStatus() {
        handler.removeCallbacks(poll);

        executor.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("identificador", installationId);
                body.put("token", deviceToken);

                JSONObject response = post("/status", body);

                String code = response.optString("codigo", "");
                boolean active = response.optBoolean("activo", false);
                String status = response.optString(
                        "estado", "PENDIENTE"
                );

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;

                    if (!code.isEmpty()) {
                        txtCode.setText(code);
                        prefs.edit()
                                .putString("activationCode", code)
                                .apply();
                    }

                    if (active && !entering) {
                        entering = true;
                        txtStatus.setText("Dispositivo activado");

                        startActivity(new Intent(
                                ActivationActivity.this,
                                MainActivity.class
                        ));
                        finish();

                    } else if (!entering) {
                        txtStatus.setText(status);
                        scheduleCheck();
                    }
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;

                    txtStatus.setText(
                            "Sin conexión. Reintentando..."
                    );
                    scheduleCheck();
                });
            }
        });
    }

    private void scheduleCheck() {
        handler.removeCallbacks(poll);
        handler.postDelayed(poll, POLL_MS);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        executor.shutdownNow();
        super.onDestroy();
    }
}