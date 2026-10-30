package com.stadiolab.app;

import android.app.Activity;
import android.widget.TextView;
import android.widget.ScrollView;
import android.widget.LinearLayout;
import android.view.WindowManager;
import android.view.Window;
import android.graphics.drawable.ColorDrawable;
import android.graphics.Color;
import android.app.Dialog;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Toast;

import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

public class MultiviewActivity extends Activity {

    private final PlayerView[] views = new PlayerView[4];
    private final ExoPlayer[] players = new ExoPlayer[4];

    private static class MultiviewOption {
        String id;
        String name;
    }

    private static class MultiviewEvent {
        String id;
        String name;
        String time;
        String competition;

        final java.util.List<MultiviewOption> options =
                new java.util.ArrayList<>();
    }

    private final java.util.List<MultiviewEvent> availableEvents =
            new java.util.ArrayList<>();

    private final java.util.List<MultiviewEvent> allMultiviewEvents =
            new java.util.ArrayList<>();
    private ActivationGuard activationGuard;
    private int selectedWindow = 0;
    private boolean expanded = false;
    private int initialEventPosition = -1;
    private String initialOptionId = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_multiview);

        initialEventPosition = getIntent().getIntExtra(
                "playingEventPosition", -1
        );

        initialOptionId = getIntent().getStringExtra(
                "playingOptionId"
        );

        if (initialOptionId == null) {
            initialOptionId = "";
        }

        int[] ids = {
            R.id.multiviewPlayer1,
            R.id.multiviewPlayer2,
            R.id.multiviewPlayer3,
            R.id.multiviewPlayer4
        };

        for (int i = 0; i < 4; i++) {
            views[i] = findViewById(ids[i]);
            players[i] = new ExoPlayer.Builder(this).build();
            views[i].setPlayer(players[i]);
            views[i].setFocusable(true);
        }

        activationGuard = new ActivationGuard(this);

        activationGuard.setOnUnauthorized(() -> {
            for (ExoPlayer player : players) {
                if (player != null) {
                    player.stop();
                    player.clearMediaItems();
                }
            }
        });

        activationGuard.setOnAuthorized(() -> {
            loadMultiviewEvents();
        });

        selectWindow(0);
        
    }

    private boolean isEventWithinMultiviewWindow(String eventTime) {
        if (eventTime == null || eventTime.trim().isEmpty()) {
            return false;
        }

        java.util.regex.Matcher matcher =
                java.util.regex.Pattern.compile(
                        "(?i)(\\d{1,2}):(\\d{2})\\s*(AM|PM)?"
                ).matcher(eventTime.trim());

        if (!matcher.find()) {
            return false;
        }

        int hour = Integer.parseInt(matcher.group(1));
        int minute = Integer.parseInt(matcher.group(2));
        String meridiem = matcher.group(3);

        if (minute > 59) {
            return false;
        }

        if (meridiem != null) {
            if (hour < 1 || hour > 12) {
                return false;
            }

            hour = hour % 12;

            if (meridiem.equalsIgnoreCase("PM")) {
                hour += 12;
            }
        } else if (hour > 23) {
            return false;
        }

        java.util.Calendar now = java.util.Calendar.getInstance();

        int currentMinutes =
                now.get(java.util.Calendar.HOUR_OF_DAY) * 60
                + now.get(java.util.Calendar.MINUTE);

        int eventMinutes = hour * 60 + minute;

        int difference = eventMinutes - currentMinutes;

        if (difference > 720) {
            difference -= 1440;
        } else if (difference < -720) {
            difference += 1440;
        }

        return difference >= -240 && difference <= 60;
    }
    private void loadMultiviewEvents() {
        new Thread(() -> {
            java.net.HttpURLConnection connection = null;

            try {
                java.net.URL url = new java.net.URL(
                    "https://panel.n30ntvlt.com/api/stadiolab/eventos"
                );

                connection = (java.net.HttpURLConnection)
                        url.openConnection();

                connection.setRequestMethod("GET");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);

                if (connection.getResponseCode() != 200) {
                    throw new Exception("Error al cargar eventos");
                }

                StringBuilder response = new StringBuilder();

                try (java.io.BufferedReader reader =
                        new java.io.BufferedReader(
                            new java.io.InputStreamReader(
                                connection.getInputStream(),
                                java.nio.charset.StandardCharsets.UTF_8
                            )
                        )) {

                    String line;

                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                }

                org.json.JSONObject json =
                        new org.json.JSONObject(response.toString());

                if (!json.optBoolean("ok", false)) {
                    throw new Exception("Catálogo no disponible");
                }

                org.json.JSONArray events =
                        json.getJSONArray("events");

                java.util.List<MultiviewEvent> downloaded =
                        new java.util.ArrayList<>();

                java.util.List<MultiviewEvent> allDownloaded =
                        new java.util.ArrayList<>();

                for (int i = 0; i < events.length(); i++) {
                    org.json.JSONObject item = events.getJSONObject(i);

                    String time = item.optString("time", "");



                    MultiviewEvent event = new MultiviewEvent();
                    event.id = item.optString("id", "");
                    event.name = item.optString("name", "");
                    event.time = time;
                    event.competition =
                            item.optString("competition", "");

                    org.json.JSONArray options =
                            item.optJSONArray("options");

                    if (options != null) {
                        for (int j = 0; j < options.length(); j++) {
                            org.json.JSONObject optionJson =
                                    options.getJSONObject(j);

                            MultiviewOption option =
                                    new MultiviewOption();

                            option.id = optionJson.optString("id", "");
                            option.name = optionJson.optString("name", "");

                            if (!option.id.isEmpty()) {
                                event.options.add(option);
                            }
                        }
                    }

                    if (!event.options.isEmpty()) {
                        allDownloaded.add(event);

                        if (isEventWithinMultiviewWindow(time)) {
                            downloaded.add(event);
                        }
                    }
                }

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;

                    availableEvents.clear();
                    availableEvents.addAll(downloaded);

                    allMultiviewEvents.clear();
                    allMultiviewEvents.addAll(allDownloaded);

                    loadInitialMultiviewWindows();
                });

            } catch (Exception e) {
                android.util.Log.e(
                    "StadioLabMultiview",
                    "Error al cargar catálogo",
                    e
                );

                runOnUiThread(() ->
                    android.widget.Toast.makeText(
                        this,
                        "No se pudieron cargar los eventos",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                );

            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }).start();
    }
    private void loadInitialMultiviewWindows() {
        if (activationGuard == null
                || !activationGuard.isAuthorized()
                || allMultiviewEvents.isEmpty()) {
            return;
        }

        int startIndex = -1;
        MultiviewOption startingOption = null;

        if (!initialOptionId.isEmpty()) {
            for (int i = 0; i < allMultiviewEvents.size(); i++) {
                MultiviewEvent event = allMultiviewEvents.get(i);

                for (MultiviewOption option : event.options) {
                    if (initialOptionId.equals(option.id)) {
                        startIndex = i;
                        startingOption = option;
                        break;
                    }
                }

                if (startIndex >= 0) {
                    break;
                }
            }
        }

        if (startIndex < 0) {
            startIndex = 0;
        }

        for (int window = 0; window < 4; window++) {
            int eventIndex = startIndex + window;

            if (eventIndex >= allMultiviewEvents.size()) {
                break;
            }

            MultiviewEvent event =
                    allMultiviewEvents.get(eventIndex);

            if (event.options.isEmpty()) {
                continue;
            }

            MultiviewOption option =
                    window == 0 && startingOption != null
                            ? startingOption
                            : event.options.get(0);

            authorizeMultiviewOption(event, option, window);
        }

        selectWindow(0);
    }
    private void showMultiviewEventSelector() {
        if (availableEvents.isEmpty()) {
            Toast.makeText(
                    this,
                    "No hay eventos disponibles en el intervalo",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        String[] names = new String[availableEvents.size()];

        for (int i = 0; i < availableEvents.size(); i++) {
            MultiviewEvent event = availableEvents.get(i);
            names[i] = event.time + "  |  " + event.name;
        }

        showMultiviewStyledDialog(
                "Cambiar ventana " + (selectedWindow + 1),
                names,
                which -> showMultiviewOptionSelector(
                        availableEvents.get(which)
                )
        );
    }

    private void showMultiviewOptionSelector(MultiviewEvent event) {
        if (event.options.size() == 1) {
            authorizeMultiviewOption(
                    event,
                    event.options.get(0),
                    selectedWindow
            );
            return;
        }

        String[] names = new String[event.options.size()];

        for (int i = 0; i < event.options.size(); i++) {
            names[i] = event.options.get(i).name;
        }

        showMultiviewStyledDialog(
                event.name,
                names,
                which -> {
                    MultiviewOption option = event.options.get(which);
                    authorizeMultiviewOption(
                            event,
                            option,
                            selectedWindow
                    );
                }
        );
    }

    private interface MultiviewSelectionListener {
        void onSelected(int index);
    }

    private void showMultiviewStyledDialog(
            String title,
            String[] items,
            MultiviewSelectionListener listener) {

        Dialog dialog = new Dialog(this);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(20, 20, 20, 20);
        panel.setBackgroundResource(R.drawable.event_dialog_background);

        TextView heading = new TextView(this);
        heading.setText(title);
        heading.setGravity(android.view.Gravity.CENTER);
        heading.setTextColor(Color.rgb(255, 213, 79));
        heading.setTextSize(22);
        heading.setTypeface(null, 1);
        heading.setPadding(12, 8, 12, 20);
        panel.addView(heading);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(true);

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);

        for (int i = 0; i < items.length; i++) {
            final int index = i;

            TextView item = new TextView(this);
            item.setText(items[i]);
            item.setTextColor(Color.WHITE);
            item.setTextSize(17);
            item.setGravity(android.view.Gravity.CENTER_VERTICAL);
            item.setPadding(20, 14, 20, 14);
            item.setFocusable(true);
            item.setClickable(true);
            item.setBackgroundResource(R.drawable.event_card_background);

            item.setOnClickListener(v -> {
                dialog.dismiss();
                listener.onSelected(index);
            });

            list.addView(item, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));
        }

        scroll.addView(list);
        panel.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        TextView cancel = new TextView(this);
        cancel.setText("Cancelar");
        cancel.setTextColor(Color.WHITE);
        cancel.setTextSize(16);
        cancel.setGravity(android.view.Gravity.CENTER);
        cancel.setPadding(12, 18, 12, 12);
        cancel.setFocusable(true);
        cancel.setBackgroundResource(R.drawable.event_card_background);
        cancel.setOnClickListener(v -> dialog.dismiss());

        panel.addView(cancel);

        dialog.setContentView(panel);
        Window window = dialog.getWindow();

        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(
                    (int) (getResources().getDisplayMetrics().widthPixels * 0.48f),
                    (int) (getResources().getDisplayMetrics().heightPixels * 0.78f)
            );
            window.setGravity(android.view.Gravity.CENTER);
        }

        dialog.show();

        if (window != null) {
            window.setLayout(
                    (int) (getResources().getDisplayMetrics().widthPixels * 0.48f),
                    (int) (getResources().getDisplayMetrics().heightPixels * 0.78f)
            );
        }

        if (list.getChildCount() > 0) {
            list.getChildAt(0).requestFocus();
        }
    }
    private void authorizeMultiviewOption(
            MultiviewEvent event,
            MultiviewOption option,
            int windowIndex) {

        if (activationGuard == null
                || !activationGuard.isAuthorized()) {
            Toast.makeText(
                    this,
                    "Dispositivo sin autorización",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        if (windowIndex < 0 || windowIndex >= players.length) {
            return;
        }

        final int targetWindow = windowIndex;

        new Thread(() -> {
            java.net.HttpURLConnection connection = null;

            try {
                android.content.SharedPreferences prefs =
                        getSharedPreferences(
                                "stadiolab_activation",
                                MODE_PRIVATE
                        );

                String installationId =
                        prefs.getString("installationId", "");

                String deviceToken =
                        prefs.getString("deviceToken", "");

                if (installationId.isEmpty() || deviceToken.isEmpty()) {
                    throw new Exception("Dispositivo sin activación");
                }

                java.net.URL url = new java.net.URL(
                        "https://panel.n30ntvlt.com/api/stadiolab/eventos/autorizar"
                );

                connection = (java.net.HttpURLConnection)
                        url.openConnection();

                connection.setRequestMethod("POST");
                connection.setRequestProperty(
                        "Content-Type",
                        "application/json; charset=UTF-8"
                );
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                connection.setDoOutput(true);

                org.json.JSONObject body = new org.json.JSONObject();

                body.put("identificador", installationId);
                body.put("token", deviceToken);
                body.put("opcionId", option.id);

                try (java.io.OutputStream out =
                        connection.getOutputStream()) {

                    out.write(
                            body.toString().getBytes(
                                    java.nio.charset.StandardCharsets.UTF_8
                            )
                    );
                }

                int status = connection.getResponseCode();

                if (status != 200) {
                    throw new Exception("Autorización HTTP " + status);
                }

                StringBuilder response = new StringBuilder();

                try (java.io.BufferedReader reader =
                        new java.io.BufferedReader(
                                new java.io.InputStreamReader(
                                        connection.getInputStream(),
                                        java.nio.charset.StandardCharsets.UTF_8
                                )
                        )) {

                    String line;

                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                }

                org.json.JSONObject json =
                        new org.json.JSONObject(response.toString());

                String playbackUrl = json.optString("url", "");

                if (playbackUrl.isEmpty()) {
                    throw new Exception("URL de reproducción vacía");
                }

                if (playbackUrl.startsWith("/")) {
                    playbackUrl =
                            "https://panel.n30ntvlt.com" + playbackUrl;
                }

                final String finalPlaybackUrl = playbackUrl;

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()
                            || activationGuard == null
                            || !activationGuard.isAuthorized()) {
                        return;
                    }

                    androidx.media3.common.MediaItem mediaItem =
                            new androidx.media3.common.MediaItem.Builder()
                                    .setUri(finalPlaybackUrl)
                                    .setMimeType("application/x-mpegURL")
                                    .build();

                    ExoPlayer targetPlayer = players[targetWindow];

                    targetPlayer.stop();
                    targetPlayer.clearMediaItems();
                    targetPlayer.setMediaItem(mediaItem);
                    targetPlayer.prepare();
                    targetPlayer.play();

                    Toast.makeText(
                            this,
                            "Ventana " + (targetWindow + 1)
                                    + ": " + event.name,
                            Toast.LENGTH_SHORT
                    ).show();
                });

            } catch (Exception e) {
                android.util.Log.e(
                        "StadioLabMultiview",
                        "Error al autorizar transmisión",
                        e
                );

                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        Toast.makeText(
                                this,
                                "No se pudo autorizar ventana "
                                        + (targetWindow + 1),
                                Toast.LENGTH_LONG
                        ).show();
                    }
                });

            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }).start();
    }
    private void selectWindow(int index) {
        if (index < 0 || index >= 4) {
            return;
        }

        selectedWindow = index;

        int[] cellIds = {
                R.id.cell1,
                R.id.cell2,
                R.id.cell3,
                R.id.cell4
        };

        for (int i = 0; i < 4; i++) {
            View cell = findViewById(cellIds[i]);

            android.graphics.drawable.GradientDrawable border =
                    new android.graphics.drawable.GradientDrawable();

            border.setColor(0xFF111111);
            border.setStroke(
                    i == selectedWindow ? 6 : 2,
                    i == selectedWindow
                            ? 0xFFFFFFFF
                            : 0xFF555555
            );

            cell.setBackground(border);

            if (players[i] != null) {
                players[i].setVolume(
                        i == selectedWindow ? 1f : 0f
                );
            }
        }

        views[selectedWindow].requestFocus();
    }
    private void toggleExpanded() {
        expanded = !expanded;

        android.widget.LinearLayout topRow =
                findViewById(R.id.topRow);

        android.widget.LinearLayout bottomRow =
                findViewById(R.id.bottomRow);

        int[] cellIds = {
                R.id.cell1,
                R.id.cell2,
                R.id.cell3,
                R.id.cell4
        };

        android.view.View[] cells = new android.view.View[4];

        for (int i = 0; i < 4; i++) {
            cells[i] = findViewById(cellIds[i]);
        }

        if (expanded) {
            topRow.setVisibility(
                    selectedWindow < 2 ? View.VISIBLE : View.GONE
            );

            bottomRow.setVisibility(
                    selectedWindow >= 2 ? View.VISIBLE : View.GONE
            );

            for (int i = 0; i < 4; i++) {
                cells[i].setVisibility(
                        i == selectedWindow ? View.VISIBLE : View.GONE
                );
            }

            android.widget.LinearLayout selectedRow =
                    selectedWindow < 2 ? topRow : bottomRow;

            android.widget.LinearLayout.LayoutParams rowParams =
                    (android.widget.LinearLayout.LayoutParams)
                    selectedRow.getLayoutParams();

            rowParams.height = 0;
            rowParams.weight = 1f;
            selectedRow.setLayoutParams(rowParams);

        } else {
            topRow.setVisibility(View.VISIBLE);
            bottomRow.setVisibility(View.VISIBLE);

            for (android.widget.LinearLayout row :
                    new android.widget.LinearLayout[]{topRow, bottomRow}) {

                android.widget.LinearLayout.LayoutParams params =
                        (android.widget.LinearLayout.LayoutParams)
                        row.getLayoutParams();

                params.height = 0;
                params.weight = 1f;
                row.setLayoutParams(params);
            }

            for (android.view.View cell : cells) {
                cell.setVisibility(View.VISIBLE);
            }
        }

        selectWindow(selectedWindow);

        android.widget.LinearLayout root =
                findViewById(R.id.multiRoot);

        View header = findViewById(R.id.multiHeader);

        header.setVisibility(expanded ? View.GONE : View.VISIBLE);
        root.setPadding(
                expanded ? 0 : dpMultiview(8),
                expanded ? 0 : dpMultiview(8),
                expanded ? 0 : dpMultiview(8),
                expanded ? 0 : dpMultiview(8)
        );

        int[] labelIds = {
                R.id.multiLabel1,
                R.id.multiLabel2,
                R.id.multiLabel3,
                R.id.multiLabel4
        };

        for (int i = 0; i < 4; i++) {
            View cell = cells[i];

            android.widget.LinearLayout.LayoutParams params =
                    (android.widget.LinearLayout.LayoutParams)
                            cell.getLayoutParams();

            params.setMargins(
                    expanded ? 0 : dpMultiview(4),
                    expanded ? 0 : dpMultiview(4),
                    expanded ? 0 : dpMultiview(4),
                    expanded ? 0 : dpMultiview(4)
            );

            cell.setLayoutParams(params);
            cell.setPadding(
                    expanded ? 0 : dpMultiview(4),
                    expanded ? 0 : dpMultiview(4),
                    expanded ? 0 : dpMultiview(4),
                    expanded ? 0 : dpMultiview(4)
            );

            findViewById(labelIds[i]).setVisibility(
                    expanded ? View.GONE : View.VISIBLE
            );

            if (expanded) {
                cell.setBackgroundColor(android.graphics.Color.BLACK);
            }
        }

        if (expanded) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            );
        } else {
            getWindow().getDecorView().setSystemUiVisibility(0);
        }
    }

    private int dpMultiview(int dp) {
        return Math.round(
                dp * getResources().getDisplayMetrics().density
        );
    }

    @Override
    public void onBackPressed() {
        if (expanded) {
            toggleExpanded();
            return;
        }

        super.onBackPressed();
    }
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {

        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == KeyEvent.KEYCODE_ENTER) {
            toggleExpanded();
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_MENU) {
            showMultiviewEventSelector();
            return true;
        }

        if (expanded && (
                keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                || keyCode == KeyEvent.KEYCODE_DPAD_UP
                || keyCode == KeyEvent.KEYCODE_DPAD_DOWN)) {
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                && selectedWindow % 2 == 1) {
            selectWindow(selectedWindow - 1);
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                && selectedWindow % 2 == 0) {
            selectWindow(selectedWindow + 1);
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_UP
                && selectedWindow >= 2) {
            selectWindow(selectedWindow - 2);
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                && selectedWindow < 2) {
            selectWindow(selectedWindow + 2);
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (activationGuard != null) {
            activationGuard.start();
        }
    }

    @Override
    protected void onPause() {
        if (activationGuard != null) {
            activationGuard.stop();
        }

        for (ExoPlayer player : players) {
            if (player != null) {
                player.pause();
            }
        }

        super.onPause();
    }
    @Override
    protected void onDestroy() {
        if (activationGuard != null) activationGuard.destroy();
        for (ExoPlayer player : players) {
            if (player != null) {
                player.release();
            }
        }
        super.onDestroy();
    }
}