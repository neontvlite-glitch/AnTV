package com.stadiolab.app;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import com.bumptech.glide.Glide;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class LiveTvActivity extends Activity {
    private ActivationGuard activationGuard;
    private boolean catalogLoaded = false;

    private TextView selectedTitle;
    private TextView selectedDescription;
    private TextView positionText;
    private ExoPlayer player;
    private View liveGuide;
    private RecyclerView liveRows;
    private int currentRowPosition = 0;
    private int currentChannelPosition = 0;
    private int playingRowPosition = 0;
    private int playingChannelPosition = 0;
    private final android.os.Handler guideHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable hideGuideRunnable = () -> hideGuide();

    @Override
    protected void onResume() {
        super.onResume();
        if (activationGuard != null) activationGuard.start();
    }

    @Override
    protected void onPause() {
        if (activationGuard != null) activationGuard.stop();
        if (player != null) player.pause();
        super.onPause();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_live_tv);

        selectedTitle = findViewById(R.id.liveSelectedTitle);
        selectedDescription = findViewById(R.id.liveSelectedDescription);
        positionText = findViewById(R.id.livePosition);

        PlayerView playerView = findViewById(R.id.livePlayer);
        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);

        liveGuide = findViewById(R.id.liveGuide);
        liveRows = findViewById(R.id.liveRows);
        RecyclerView rows = liveRows;
        rows.setLayoutManager(new LinearLayoutManager(this));
        rows.setItemAnimator(null);
        rows.setPreserveFocusAfterLayout(true);

        rows.setAdapter(new RowAdapter(new ArrayList<>()));
	  activationGuard = new ActivationGuard(this);
        activationGuard.setOnUnauthorized(() -> {
            if (player != null) {
                player.stop();
                player.clearMediaItems();
            }
        });
        activationGuard.setOnAuthorized(() -> {
            if (!catalogLoaded) {
                catalogLoaded = true;
                loadLiveCatalog(rows);
            }
        });
    }
    private CharSequence coloredChannelTitle(String category, String channel) {
        String text = category + "    " + channel;
        SpannableString styled = new SpannableString(text);

        styled.setSpan(
                new ForegroundColorSpan(0xFFFFD54F),
                category.length() + 4,
                text.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );

        return styled;
    }
	private void loadLiveCatalog(RecyclerView rows) {
    new Thread(() -> {
        HttpURLConnection connection = null;

        try {
            URL url = new URL(
                    "https://panel.n30ntvlt.com/api/stadiolab/live"
            );

            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);

            int responseCode = connection.getResponseCode();

            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new Exception("HTTP " + responseCode);
            }

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream())
            );

            StringBuilder response = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                response.append(line);
            }

            reader.close();

            JSONObject root = new JSONObject(response.toString());
            JSONArray categories = root.getJSONArray("categories");

            List<LiveSection> sections = new ArrayList<>();

            for (int i = 0; i < categories.length(); i++) {
                JSONObject category = categories.getJSONObject(i);

                String categoryName = category.optString("name", "");
                JSONArray channelArray = category.optJSONArray("channels");

                List<LiveChannel> channels = new ArrayList<>();

                if (channelArray != null) {
                    for (int j = 0; j < channelArray.length(); j++) {
                        JSONObject item = channelArray.getJSONObject(j);

                        String name = item.optString("name", "");
                        String posterUrl = item.optString("posterUrl", null);
                        String streamId = item.optString("streamId", "");

                        channels.add(
                                new LiveChannel(
                                        name,
                                        "",
                                        posterUrl,
                                        streamId
                                )
                        );
                    }
                }

                if (categoryName.equalsIgnoreCase("Deportes")) {
                    String[] ordenDeportes = {
                        "TUDN México",
                        "TUDN USA",

                        "ESPN México",
                        "ESPN 2 México",
                        "ESPN 3 México",
                        "ESPN 4 México",
                        "ESPN Deportes",
                        "ESPN Argentina",
                        "ESPN 2 Argentina",
                        "ESPN 3 Argentina",
                        "ESPN Premium",
                        "ESPN Chile",

                        "Fox Sports México",
                        "Fox Sports 2 México",
                        "Fox Sports 3 México",
                        "Fox Sports Premium",
                        "Fox México",
                        "Fox Sports Argentina",
                        "Fox Deportes USA",
                        "FOX Costa Rica",

                        "TV Azteca Deportes",
                        "Claro Sports",
                        "Directv Sports 1 Argentina",
                        "TyC Sports Argentina",
                        "TNT Sports HD",
                        "beIN Sports Ñ",
                        "F1 DAZN",
                        "FUTV Costa Rica",
                        "AYM Sports",
                        "Golf Channel Latin America",
                        "OneToroTV",

                        "ESPN USA",
                        "ESPN 2 USA",
                        "ESPN U",
                        "ESPN SEC",
                        "ACCN ESPN",
                        "ESPN News",

                        "FS1 USA",
                        "FS2 USA",
                        "Fox Soccer Plus",

                        "beIN Sports",
                        "NFL Network",
                        "MLB TV",
                        "NBA TV",
                        "NHL TV",
                        "BTN Network",
                        "CBS Sports Network",
                        "UFC Fight Pass TV",
                        "Tennis Channel",
                        "Golf Channel",
                        "Outdoor Channel",
                        "FanDuel TV",
                        "RFD TV"
                    };

                    java.util.Map<String, Integer> posiciones =
                            new java.util.HashMap<>();

                    for (int k = 0; k < ordenDeportes.length; k++) {
                        posiciones.put(ordenDeportes[k], k);
                    }

                    channels.sort((a, b) -> {
                        int pa = posiciones.getOrDefault(a.name, 9999);
                        int pb = posiciones.getOrDefault(b.name, 9999);
                        return Integer.compare(pa, pb);
                    });
                }
                if (!channels.isEmpty()) {
                    sections.add(
                            new LiveSection(categoryName, channels)
                    );
                }
            }

            runOnUiThread(() -> {
                rows.setAdapter(new RowAdapter(sections));

                if (!sections.isEmpty()) {
    LiveChannel firstChannel = sections.get(0).channels.get(0);

    selectedTitle.setText(firstChannel.name);
    selectedDescription.setText(firstChannel.description);
    positionText.setText(
            "1 de " + sections.get(0).channels.size()
    );

    currentRowPosition = 0;
    currentChannelPosition = 0;
    playingRowPosition = 0;
    playingChannelPosition = 0;

    rows.postDelayed(() -> {
        RecyclerView.ViewHolder rowHolder =
                rows.findViewHolderForAdapterPosition(0);

        if (rowHolder instanceof RowAdapter.RowHolder) {
            RecyclerView firstRow =
                    ((RowAdapter.RowHolder) rowHolder).channels;

            firstRow.postDelayed(() -> {
                RecyclerView.ViewHolder channelHolder =
                        firstRow.findViewHolderForAdapterPosition(0);

                if (channelHolder != null) {
                    channelHolder.itemView.requestFocus();
                }
            }, 200);
        }

        showGuide();

        if (firstChannel.streamId != null &&
                !firstChannel.streamId.isEmpty()) {
            playLiveChannel(firstChannel.streamId);
        }
    }, 200);
}
            });

        } catch (Exception e) {
            e.printStackTrace();

            runOnUiThread(() ->
                    selectedTitle.setText("ERROR AL CARGAR TV")
            );

        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }).start();
}
    private List<LiveSection> createDemoSections() {
        List<LiveSection> sections = new ArrayList<>();

        sections.add(new LiveSection(
                "DEPORTES",
                Arrays.asList(
                        new LiveChannel("DEPORTES 1", "Partido en vivo"),
                        new LiveChannel("DEPORTES 2", "Programación deportiva"),
                        new LiveChannel("FÚTBOL", "Fútbol en vivo"),
                        new LiveChannel("SPORTS", "Eventos deportivos"),
                        new LiveChannel("ARENA", "Cobertura especial")
                )
        ));

        sections.add(new LiveSection(
                "NOTICIAS",
                Arrays.asList(
                        new LiveChannel("NOTICIAS 1", "Noticias nacionales"),
                        new LiveChannel("NOTICIAS 2", "Información en vivo"),
                        new LiveChannel("24 HORAS", "Actualidad"),
                        new LiveChannel("MUNDO", "Noticias internacionales"),
                        new LiveChannel("INFO", "Últimas noticias")
                )
        ));

        sections.add(new LiveSection(
                "ENTRETENIMIENTO",
                Arrays.asList(
                        new LiveChannel("ENTRETENIMIENTO 1", "Programación general"),
                        new LiveChannel("ENTRETENIMIENTO 2", "Series y programas"),
                        new LiveChannel("FAMILIA", "Contenido familiar"),
                        new LiveChannel("CLÁSICOS", "Programación clásica"),
                        new LiveChannel("PLUS", "Contenido especial")
                )
        ));

        return sections;
    }

    private class RowAdapter extends RecyclerView.Adapter<RowAdapter.RowHolder> {

        private final List<LiveSection> sections;

        RowAdapter(List<LiveSection> sections) {
            this.sections = sections;
        }

        @NonNull
        @Override
        public RowHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_live_row, parent, false);
            return new RowHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RowHolder holder, int position) {
            LiveSection section = sections.get(position);

            holder.title.setText(section.title);

            LinearLayoutManager manager =
                    new LinearLayoutManager(
                            LiveTvActivity.this,
                            LinearLayoutManager.HORIZONTAL,
                            false
                    );

            holder.channels.setLayoutManager(manager);
            holder.channels.setItemAnimator(null);
            holder.channels.setNestedScrollingEnabled(false);
            holder.channels.setPreserveFocusAfterLayout(true);
            holder.channels.setItemViewCacheSize(10);
            holder.channels.setAdapter(
                    new ChannelAdapter(position, section.title, section.channels)
            );
        }

        @Override
        public int getItemCount() {
            return sections.size();
        }

        class RowHolder extends RecyclerView.ViewHolder {
            TextView title;
            RecyclerView channels;

            RowHolder(View itemView) {
                super(itemView);
                title = itemView.findViewById(R.id.rowTitle);
                channels = itemView.findViewById(R.id.rowChannels);
            }
        }
    }

    private class ChannelAdapter
            extends RecyclerView.Adapter<ChannelAdapter.ChannelHolder> {

        private final int rowPosition;
        private final String sectionTitle;
        private final List<LiveChannel> channels;

        ChannelAdapter(int rowPosition, String sectionTitle, List<LiveChannel> channels) {
            this.rowPosition = rowPosition;
            this.sectionTitle = sectionTitle;
            this.channels = channels;
        }

        @NonNull
        @Override
        public ChannelHolder onCreateViewHolder(
                @NonNull ViewGroup parent,
                int viewType
        ) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_live_channel, parent, false);

            return new ChannelHolder(view);
        }

        @Override
        public void onBindViewHolder(
                @NonNull ChannelHolder holder,
                int position
        ) {
            LiveChannel channel = channels.get(position);

            holder.title.setText(channel.name);

            String imageSource = channel.posterUrl;

            if (imageSource != null && !imageSource.isEmpty()) {
                try {
                    String fileName = android.net.Uri.parse(imageSource)
                            .getLastPathSegment();

                    if (fileName != null && fileName.matches(
                            "[a-zA-Z0-9_-]+\\.webp")) {
                        String assetPath = "channel_logos/" + fileName;
                        try (java.io.InputStream input =
                                     holder.image.getContext()
                                             .getAssets().open(assetPath)) {
                            imageSource = "file:///android_asset/" + assetPath;
                        } catch (java.io.IOException ignored) {
                            // Si no existe el logo local, usar la URL original.
                        }
                    }
                } catch (Exception ignored) {
                    // Conservar la URL original.
                }
            }

            Glide.with(holder.image)
                    .load(imageSource)
                    .into(holder.image);


            holder.card.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    currentRowPosition = rowPosition;
                    currentChannelPosition = position;
                    v.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(120)
                            .start();

                    selectedTitle.setText(
                            coloredChannelTitle(sectionTitle, channel.name)
                    );

                    selectedDescription.setText(channel.description);

                    positionText.setText(
                            (position + 1) + " de " + channels.size()
                    );
                } else {
                    v.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(120)
                            .start();
                }
            });

            holder.card.setOnClickListener(v -> {
                selectedTitle.setText(channel.name);
                selectedDescription.setText(channel.description);

                if (channel.streamId == null || channel.streamId.isEmpty()) {
                    return;
                }

                playingRowPosition = rowPosition;
                playingChannelPosition = position;
                playLiveChannel(channel.streamId);
                showGuide();
            });
        }

        @Override
        public int getItemCount() {
            return channels.size();
        }

        class ChannelHolder extends RecyclerView.ViewHolder {
            View card;
            TextView title;
            ImageView image;

            ChannelHolder(View itemView) {
                super(itemView);
                card = itemView.findViewById(R.id.channelCard);
                title = itemView.findViewById(R.id.channelTitle);
                image = itemView.findViewById(R.id.channelImage);
            }
        }
    }

    private static class LiveSection {
        final String title;
        final List<LiveChannel> channels;

        LiveSection(String title, List<LiveChannel> channels) {
            this.title = title;
            this.channels = channels;
        }
    }



    @Override
    public boolean onKeyDown(int keyCode, android.view.KeyEvent event) {
        if (keyCode == android.view.KeyEvent.KEYCODE_BACK) {
            if (liveGuide != null && liveGuide.getVisibility() == View.VISIBLE) {
                hideGuide();
                return true;
            }
        }

        if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP ||
                keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN) {

            if (liveGuide != null && liveGuide.getVisibility() != View.VISIBLE) {
                showGuide();
                liveRows.postDelayed(() -> restoreCurrentChannelFocus(), 150);
                return true;
            }
        }

        if (liveGuide != null && liveGuide.getVisibility() == View.VISIBLE &&
                (keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP ||
                 keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN ||
                 keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT ||
                 keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT)) {
            showGuide();
        }

        return super.onKeyDown(keyCode, event);
    }
    private void restoreCurrentChannelFocus() {
        if (liveRows == null) return;

        liveRows.scrollToPosition(playingRowPosition);

        liveRows.post(() -> {
            RecyclerView.ViewHolder rowHolder =
                    liveRows.findViewHolderForAdapterPosition(playingRowPosition);

            if (!(rowHolder instanceof RowAdapter.RowHolder)) return;

            RecyclerView channels =
                    ((RowAdapter.RowHolder) rowHolder).channels;

            channels.scrollToPosition(playingChannelPosition);

            channels.post(() -> {
                RecyclerView.ViewHolder channelHolder =
                        channels.findViewHolderForAdapterPosition(
                                playingChannelPosition
                        );

                if (channelHolder != null) {
                    channelHolder.itemView.requestFocus();
                }
            });
        });
    }
    private void showGuide() {
        if (liveGuide == null) return;

        liveGuide.setVisibility(View.VISIBLE);

        guideHandler.removeCallbacks(hideGuideRunnable);
        guideHandler.postDelayed(hideGuideRunnable, 5000);
    }

    private void hideGuide() {
        guideHandler.removeCallbacks(hideGuideRunnable);

        if (liveGuide != null) {
            liveGuide.setVisibility(View.GONE);
        }
    }
        private void playLiveChannel(String streamId) {
        new Thread(() -> {
            HttpURLConnection connection = null;

            try {
                SharedPreferences prefs = getSharedPreferences(
                        "stadiolab_activation", MODE_PRIVATE
                );

                String installationId = prefs.getString("installationId", "");
                String deviceToken = prefs.getString("deviceToken", "");

                if (installationId.isEmpty() || deviceToken.isEmpty()) {
                    throw new Exception("Dispositivo sin activación");
                }

                URL url = new URL(
                        "https://panel.n30ntvlt.com/api/stadiolab/play/autorizar"
                );

                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty(
                        "Content-Type", "application/json; charset=UTF-8"
                );
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                connection.setDoOutput(true);

                JSONObject body = new JSONObject();
                body.put("identificador", installationId);
                body.put("token", deviceToken);
                body.put("streamId", streamId);

                try (OutputStream out = connection.getOutputStream()) {
                    out.write(body.toString().getBytes(
                            java.nio.charset.StandardCharsets.UTF_8
                    ));
                }

                int status = connection.getResponseCode();

                if (status != HttpURLConnection.HTTP_OK) {
                    throw new Exception("Autorización HTTP " + status);
                }

                StringBuilder response = new StringBuilder();

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(
                                connection.getInputStream(),
                                java.nio.charset.StandardCharsets.UTF_8
                        )
                )) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                }

                JSONObject json = new JSONObject(response.toString());
                String playbackUrl = json.optString("url", "");

                if (playbackUrl.isEmpty()) {
                    throw new Exception("URL de reproducción vacía");
                }

                if (playbackUrl.startsWith("/")) {
                    playbackUrl = "https://panel.n30ntvlt.com" + playbackUrl;
                }

                final String finalPlaybackUrl = playbackUrl;

                runOnUiThread(() -> {
                    if (activationGuard == null ||
                            !activationGuard.isAuthorized() ||
                            player == null) {
                        return;
                    }

                    MediaItem mediaItem = new MediaItem.Builder()
                            .setUri(finalPlaybackUrl)
                            .setMimeType("application/x-mpegURL")
                            .build();

                    player.setMediaItem(mediaItem);
                    player.prepare();
                    player.play();
                });

            } catch (Exception e) {
                android.util.Log.e(
                        "StadioLab",
                        "Error al autorizar reproducción",
                        e
                );
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }).start();
    }
    @Override
    protected void onDestroy() {
        if (activationGuard != null) {
            activationGuard.destroy();
            activationGuard = null;
        }
        if (player != null) {
            player.release();
            player = null;
        }
        super.onDestroy();
    }
    private static class LiveChannel {
        final String name;
        final String description;
        final String posterUrl;
        final String streamId;

        LiveChannel(String name, String description) {
            this(name, description, null, null);
        }

        LiveChannel(String name, String description, String posterUrl, String streamId) {
            this.name = name;
            this.description = description;
            this.posterUrl = posterUrl;
            this.streamId = streamId;
        }
    }
}










