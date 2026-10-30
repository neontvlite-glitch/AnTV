package com.stadiolab.app;

import android.content.SharedPreferences;
import androidx.media3.common.MediaItem;
import java.io.OutputStream;
import android.util.Log;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.recyclerview.widget.RecyclerView.ViewHolder;
import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import android.view.View;
import android.widget.TextView;

import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class EventsActivity extends Activity {
    private boolean resumeEventPlayback = false;

    private ActivationGuard activationGuard;
    private ExoPlayer player;
    private int intentosLiveWindow = 0;

    private View eventsMenu;
    private PlayerView eventsPlayer;
    private RecyclerView eventsList;
    private TextView eventsSubtitle;
    private int lastSelectedEventPosition = 0;
    private int playingEventPosition = -1;
    private String playingOptionId = "";
    private final Handler eventsOverlayHandler =
        new Handler(Looper.getMainLooper());

private final Runnable hideEventsOverlay = () -> {
    if (eventsPlayer != null
            && eventsPlayer.getVisibility() == View.VISIBLE
            && eventsMenu != null) {
        eventsMenu.setVisibility(View.GONE);
    }
};

private void restartEventsOverlayTimer() {
    eventsOverlayHandler.removeCallbacks(hideEventsOverlay);
    eventsOverlayHandler.postDelayed(hideEventsOverlay, 5000);
}

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_events);
        findViewById(R.id.eventsMultiview).setOnClickListener(v -> {
            android.content.Intent multiviewIntent =
                    new android.content.Intent(
                            this,
                            MultiviewActivity.class
                    );

            multiviewIntent.putExtra(
                    "playingEventPosition",
                    playingEventPosition
            );

            multiviewIntent.putExtra(
                    "playingOptionId",
                    playingOptionId
            );

            startActivity(multiviewIntent);
        });

        eventsMenu = findViewById(R.id.eventsMenu);
        eventsPlayer = findViewById(R.id.eventsPlayer);
        eventsList = findViewById(R.id.eventsList);
        eventsSubtitle = findViewById(R.id.eventsSubtitle);
        eventsSubtitle.setVisibility(View.GONE);
        TextView eventsClock = findViewById(R.id.eventsClock);
        final Handler clockHandler = new Handler(Looper.getMainLooper());
        final Runnable[] clockUpdater = new Runnable[1];
        clockUpdater[0] = () -> {
            java.text.SimpleDateFormat format =
                    new java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault());
            eventsClock.setText(format.format(new java.util.Date()));
            clockHandler.postDelayed(clockUpdater[0], 60000);
        };
        clockUpdater[0].run();

        eventsList.setLayoutManager(
                new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        );

        eventsList.setItemAnimator(null);
        eventsList.setPreserveFocusAfterLayout(true);
	  eventsList.setAdapter(new EventAdapter());	

        player = new ExoPlayer.Builder(this).build();
        player.addListener(new androidx.media3.common.Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                String estado;
                switch (state) {
                    case androidx.media3.common.Player.STATE_IDLE:
                        estado = "IDLE";
                        break;
                    case androidx.media3.common.Player.STATE_BUFFERING:
                        estado = "BUFFERING";
                        break;
                    case androidx.media3.common.Player.STATE_READY:
                        estado = "READY";
                        break;
                    case androidx.media3.common.Player.STATE_ENDED:
                        estado = "ENDED";
                        break;
                    default:
                        estado = "DESCONOCIDO";
                }
                Log.d("StadioLabDiagnostico", "Estado=" + estado);
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                Log.d("StadioLabDiagnostico",
                        "Reproduciendo=" + isPlaying);
            }

            

            @Override
            public void onPlayerError(
                    androidx.media3.common.PlaybackException error) {
                Throwable causa = error.getCause();

                Log.e("StadioLabDiagnostico",
                        "Codigo=" + error.errorCode
                        + " Tipo=" + (causa == null
                        ? "desconocido"
                        : causa.getClass().getSimpleName()));

                if (error.errorCode ==
                        androidx.media3.common.PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW
                        && intentosLiveWindow < 2) {

                    intentosLiveWindow++;

                    Log.w("StadioLabDiagnostico",
                            "Recuperando LIVE. Intento=" + intentosLiveWindow);

                    player.seekToDefaultPosition();
                    player.prepare();
                    player.play();
                }
            }
        });
        eventsPlayer.setPlayer(player);

        activationGuard = new ActivationGuard(this);

        activationGuard.setOnUnauthorized(() -> {
            if (player != null) {
                player.stop();
                player.clearMediaItems();
            }
        });

        activationGuard.setOnAuthorized(() -> {
    loadEventsCatalog();
    if (resumeEventPlayback && player != null
            && activationGuard.isAuthorized()) {
        player.play();
        resumeEventPlayback = false;
    }
});
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (activationGuard != null) activationGuard.start();
    }

    @Override
    protected void onPause() {
        resumeEventPlayback = player != null && player.isPlaying();
        if (activationGuard != null) activationGuard.stop();
        if (player != null) player.pause();
        super.onPause();
    }
     @Override
public boolean dispatchKeyEvent(android.view.KeyEvent event) {
    if (event.getAction() == android.view.KeyEvent.ACTION_DOWN
            && event.getKeyCode() == android.view.KeyEvent.KEYCODE_DPAD_UP
            && eventsPlayer != null
            && eventsPlayer.getVisibility() == View.VISIBLE
            && eventsMenu != null
            && eventsMenu.getVisibility() != View.VISIBLE) {
        return onKeyDown(event.getKeyCode(), event);
    }
    return super.dispatchKeyEvent(event);
}

@Override
public boolean onKeyDown(int keyCode, android.view.KeyEvent event) {

    if ((keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN || keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP)
            && eventsPlayer != null
            && eventsPlayer.getVisibility() == View.VISIBLE
            && eventsMenu != null
            && eventsMenu.getVisibility() != View.VISIBLE) {

        eventsMenu.setVisibility(View.VISIBLE);

        eventsList.scrollToPosition(lastSelectedEventPosition);

        eventsList.post(() -> {
            RecyclerView.ViewHolder holder =
                    eventsList.findViewHolderForAdapterPosition(
                            lastSelectedEventPosition
                    );

            if (holder != null) {
                holder.itemView.requestFocus();
            }
        });

        restartEventsOverlayTimer();
        return true;
    }

    return super.onKeyDown(keyCode, event);
}
    @Override
public void onBackPressed() {

    if (eventsPlayer != null
            && eventsPlayer.getVisibility() == View.VISIBLE
            && eventsMenu != null
            && eventsMenu.getVisibility() == View.VISIBLE) {

        eventsOverlayHandler.removeCallbacks(hideEventsOverlay);
        eventsMenu.setVisibility(View.GONE);
        return;
    }

    eventsOverlayHandler.removeCallbacks(hideEventsOverlay);
    super.onBackPressed();
}
     
    @Override
    protected void onDestroy() {
        if (activationGuard != null) activationGuard.destroy();
        if (player != null) player.release();
        super.onDestroy();
    }
	private boolean eventsLoading = false;
private boolean eventsLoaded = false;

private void loadEventsCatalog() {
    if (eventsLoading || eventsLoaded) return;

    eventsLoading = true;
    eventsSubtitle.setText("Cargando eventos deportivos...");

    new Thread(() -> {
        HttpURLConnection connection = null;

        try {
            URL url = new URL(
                "https://panel.n30ntvlt.com/api/stadiolab/eventos"
            );

            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);

            if (connection.getResponseCode() != 200) {
                throw new Exception(
                    "Error HTTP " + connection.getResponseCode()
                );
            }

            StringBuilder response = new StringBuilder();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(
                        connection.getInputStream(),
                        StandardCharsets.UTF_8
                    )
            )) {
                String line;

                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
            }

            JSONObject json = new JSONObject(response.toString());

            if (!json.optBoolean("ok", false)) {
                throw new Exception("Catálogo no disponible");
            }

            JSONArray events = json.getJSONArray("events");

List<EventItem> downloadedEvents = new ArrayList<>();
int totalOptions = 0;

for (int i = 0; i < events.length(); i++) {
    JSONObject event = events.getJSONObject(i);

    EventItem item = new EventItem();
    item.id = event.optString("id", "");
    item.name = event.optString("name", "");
    item.time = event.optString("time", "").trim();
    if (event.isNull("time")
            || item.time.equalsIgnoreCase("null")
            || item.time.equalsIgnoreCase("undefined")) {
        item.time = "";
    }
    item.competition = event.optString("competition", "");

    JSONArray options = event.optJSONArray("options");

    if (options != null) {
        for (int j = 0; j < options.length(); j++) {
            JSONObject optionJson = options.getJSONObject(j);

            EventOption option = new EventOption();
            option.id = optionJson.optString("id", "");
            option.name = optionJson.optString("name", "");
            option.order = optionJson.optInt("order", j + 1);

            item.options.add(option);
            totalOptions++;
        }
    }

    downloadedEvents.add(item);
}

int totalEvents = downloadedEvents.size();

            final int eventCount = totalEvents;
            final int optionCount = totalOptions;

            runOnUiThread(() -> {
                eventsLoading = false;

                if (activationGuard == null ||
                        !activationGuard.isAuthorized()) {
                    return;
                }
			eventItems.clear();
			eventItems.addAll(downloadedEvents);
			if (eventsList.getAdapter() != null) {
    eventsList.getAdapter().notifyDataSetChanged();
}

if (!eventItems.isEmpty()) {

    int autoPlayPosition = findAutoPlayEventPosition();

    int selectedPosition =
            autoPlayPosition >= 0 ? autoPlayPosition : 0;

    lastSelectedEventPosition = selectedPosition;

    eventsList.scrollToPosition(selectedPosition);

    eventsList.post(() -> {
        RecyclerView.ViewHolder holder =
                eventsList.findViewHolderForAdapterPosition(
                        selectedPosition
                );

        if (holder != null) {
            holder.itemView.requestFocus();
        }
    });

    if (autoPlayPosition >= 0) {
        EventItem autoEvent = eventItems.get(autoPlayPosition);

        if (!autoEvent.options.isEmpty()) {
            authorizeEventOption(
                    autoEvent,
                    autoEvent.options.get(0)
            );
        }
    }
}
                eventsLoaded = true;

                eventsSubtitle.setText(
                    eventCount + " eventos deportivos · " +
                    optionCount + " opciones disponibles"
                );
            });

        } catch (Exception e) {
            android.util.Log.e(
                "StadioLabEvents",
                "Error al cargar eventos",
                e
            );

            runOnUiThread(() -> {
                eventsLoading = false;
                eventsSubtitle.setText(
                    "No se pudieron cargar los eventos"
                );
            });

        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }).start();
}
 private final List<EventItem> eventItems = new ArrayList<>();
 private int findAutoPlayEventPosition() {
    java.util.Calendar now = java.util.Calendar.getInstance();

    int currentMinutes =
            now.get(java.util.Calendar.HOUR_OF_DAY) * 60
            + now.get(java.util.Calendar.MINUTE);

    int bestPosition = -1;
    int bestMinutes = -1;

    for (int i = 0; i < eventItems.size(); i++) {
        EventItem event = eventItems.get(i);

        if (event.options.isEmpty()
                || event.time == null) {
            continue;
        }

        String time = event.time.trim();

        java.util.regex.Matcher matcher =
                java.util.regex.Pattern.compile(
                        "(?i)(\\d{1,2}):(\\d{2})\\s*(AM|PM)?"
                ).matcher(time);

        if (!matcher.find()) {
            continue;
        }

        int hour = Integer.parseInt(matcher.group(1));
        int minute = Integer.parseInt(matcher.group(2));
        String meridiem = matcher.group(3);

        if (minute > 59) {
            continue;
        }

        if (meridiem != null) {
            if (hour < 1 || hour > 12) {
                continue;
            }

            hour = hour % 12;

            if (meridiem.equalsIgnoreCase("PM")) {
                hour += 12;
            }
        } else if (hour > 23) {
            continue;
        }

        int eventMinutes = hour * 60 + minute;

        if (eventMinutes <= currentMinutes
                && eventMinutes > bestMinutes) {

            bestMinutes = eventMinutes;
            bestPosition = i;
        }
    }

    return bestPosition;
}

private static class EventItem {
    String id;
    String name;
    String time;
    String competition;
    final List<EventOption> options = new ArrayList<>();
}

private static class EventOption {
    String id;
    String name;
    int order;
}
private class EventAdapter extends RecyclerView.Adapter<EventAdapter.EventHolder> {

    class EventHolder extends RecyclerView.ViewHolder {
        android.widget.ProgressBar progress;
        TextView time;
        TextView name;
        TextView count;
	  android.widget.ImageView logo;

        EventHolder(View view) {
            super(view);
            time = view.findViewById(R.id.eventTime);
            name = view.findViewById(R.id.eventName);
            count = view.findViewById(R.id.eventOptionsCount);
		logo = view.findViewById(R.id.eventLogo);
            progress = view.findViewById(R.id.eventProgress);
        }
    }

    @Override
    public EventHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_event, parent, false);

        return new EventHolder(view);
    }

    @Override
    public void onBindViewHolder(EventHolder holder, int position) {
        EventItem event = eventItems.get(position);
	if ("CMLL".equalsIgnoreCase(event.competition)) {
    holder.logo.setImageResource(R.drawable.logo_cmll);
} else if ("MLB".equalsIgnoreCase(event.competition)) {
    holder.logo.setImageResource(R.drawable.logo_mlb);
} else if ("NFL".equalsIgnoreCase(event.competition)) {
    holder.logo.setImageResource(R.drawable.logo_nfl);
} else if ("NBA".equalsIgnoreCase(event.competition)) {
    holder.logo.setImageResource(R.drawable.logo_nba);
} else if ((event.competition != null && event.competition.toUpperCase(java.util.Locale.ROOT).contains("NCAA"))) {
    holder.logo.setImageResource(R.drawable.logo_ncaa);
} else if ("NHL".equalsIgnoreCase(event.competition)) {
    holder.logo.setImageResource(R.drawable.logo_nhl);
} else if ("Liga MX".equalsIgnoreCase(event.competition)) {
    holder.logo.setImageResource(R.drawable.logo_ligamx);
} else if ("Liga MX Femenil".equalsIgnoreCase(event.competition)) {
    holder.logo.setImageResource(R.drawable.logo_ligamx_femenil);
} else if (event.competition != null &&
        (event.competition.toLowerCase(java.util.Locale.ROOT).contains("women's champions league") ||
         event.competition.toLowerCase(java.util.Locale.ROOT).contains("womens champions league") ||
         event.competition.toLowerCase(java.util.Locale.ROOT).contains("champions league femenil") ||
         event.competition.toLowerCase(java.util.Locale.ROOT).contains("champions league femenina"))) {
    holder.logo.setImageResource(R.drawable.logo_champions_femenil);
} else if (event.competition != null &&
        (event.competition.equalsIgnoreCase("CHAMPIONSHIP") ||
         event.competition.equalsIgnoreCase("EFL CHAMPIONSHIP") ||
         event.competition.equalsIgnoreCase("ENGLISH CHAMPIONSHIP"))) {
    holder.logo.setImageResource(R.drawable.logo_championship);
} else if ("Champions League".equalsIgnoreCase(event.competition)) {
    holder.logo.setImageResource(R.drawable.logo_champions);
} else if (event.competition != null &&
        (event.competition.toLowerCase(java.util.Locale.ROOT).contains("copa del rey") ||
         event.competition.toLowerCase(java.util.Locale.ROOT).contains("copa de su majestad el rey"))) {
    holder.logo.setImageResource(R.drawable.logo_copa_del_rey);
} else if (event.competition != null &&
        (event.competition.toLowerCase(java.util.Locale.ROOT).contains("efl cup") ||
         event.competition.toLowerCase(java.util.Locale.ROOT).contains("carabao cup") ||
         event.competition.toLowerCase(java.util.Locale.ROOT).contains("english league cup"))) {
    holder.logo.setImageResource(R.drawable.logo_efl_cup);
} else if ("Copa Sudamericana".equalsIgnoreCase(event.competition) || "Sudamericana".equalsIgnoreCase(event.competition)) {
    holder.logo.setImageResource(R.drawable.logo_sudamericana);
} else if (event.competition != null &&
        (event.competition.toLowerCase(java.util.Locale.ROOT).contains("laliga") ||
         event.competition.toLowerCase(java.util.Locale.ROOT).contains("la liga"))) {
    holder.logo.setImageResource(R.drawable.logo_laliga);
} else if (event.competition != null &&
        event.competition.toLowerCase(java.util.Locale.ROOT).contains("libertadores")) {
    holder.logo.setImageResource(R.drawable.logo_libertadores);
} else if (event.competition != null &&
        event.competition.toUpperCase(java.util.Locale.ROOT).contains("MLS")) {
    holder.logo.setImageResource(R.drawable.logo_mls);
} else if (event.competition != null &&
        event.competition.toUpperCase(java.util.Locale.ROOT).contains("WNBA")) {
    holder.logo.setImageResource(R.drawable.logo_wnba);
} else if (event.competition != null &&
        (event.competition.toLowerCase(java.util.Locale.ROOT).contains("tennis") ||
         event.competition.toLowerCase(java.util.Locale.ROOT).contains("tenis") ||
         event.competition.toUpperCase(java.util.Locale.ROOT).contains("ATP") ||
         event.competition.toUpperCase(java.util.Locale.ROOT).contains("WTA") ||
         event.competition.toLowerCase(java.util.Locale.ROOT).contains("wimbledon") ||
         event.competition.toLowerCase(java.util.Locale.ROOT).contains("roland garros") ||
         event.competition.toLowerCase(java.util.Locale.ROOT).contains("australian open") ||
         event.competition.toLowerCase(java.util.Locale.ROOT).contains("us open"))) {
    holder.logo.setImageResource(R.drawable.logo_tennis);
} else if (event.competition != null &&
        event.competition.toLowerCase(java.util.Locale.ROOT).contains("premier league")) {
    holder.logo.setImageResource(R.drawable.logo_premier_league);
} else if (event.competition != null &&
        event.competition.toLowerCase(java.util.Locale.ROOT).contains("bundesliga")) {
    holder.logo.setImageResource(R.drawable.logo_bundesliga);
} else if (event.competition != null &&
        (event.competition.toLowerCase(java.util.Locale.ROOT).contains("saudi") ||
         event.competition.toLowerCase(java.util.Locale.ROOT).contains("roshn"))) {
    holder.logo.setImageResource(R.drawable.logo_saudi);
} else if (event.competition != null &&
        (event.competition.toLowerCase(java.util.Locale.ROOT).contains("argentina") ||
         event.competition.toLowerCase(java.util.Locale.ROOT).contains("liga profesional"))) {
    holder.logo.setImageResource(R.drawable.logo_liga_argentina);
} else if (event.competition != null &&
        event.competition.toLowerCase(java.util.Locale.ROOT).contains("ligue 1")) {
    holder.logo.setImageResource(R.drawable.logo_ligue1);
} else if (event.competition != null &&
        (event.competition.toLowerCase(java.util.Locale.ROOT).contains("serie a") ||
         event.competition.toLowerCase(java.util.Locale.ROOT).contains("serie-a"))) {
    holder.logo.setImageResource(R.drawable.logo_serie_a);
} else if (event.competition != null &&
        (event.competition.toLowerCase(java.util.Locale.ROOT).contains("formula 1") ||
         event.competition.toLowerCase(java.util.Locale.ROOT).contains("formula one") ||
         event.competition.equalsIgnoreCase("F1"))) {
    holder.logo.setImageResource(R.drawable.logo_formula1);
} else if (event.competition != null &&
        (event.competition.toLowerCase(java.util.Locale.ROOT).contains("golf") ||
         event.competition.toLowerCase(java.util.Locale.ROOT).contains("pga"))) {
    holder.logo.setImageResource(R.drawable.logo_golf);
} else if (event.competition != null &&
        (event.competition.toUpperCase(java.util.Locale.ROOT).contains("UFC") ||
         event.competition.toUpperCase(java.util.Locale.ROOT).contains("MMA"))) {
    holder.logo.setImageResource(R.drawable.logo_ufc);
} else if (event.competition != null &&
        (event.competition.equalsIgnoreCase("BOX") ||
         event.competition.toUpperCase(java.util.Locale.ROOT).contains("BOXEO") ||
         event.competition.toUpperCase(java.util.Locale.ROOT).contains("BOXING"))) {
    holder.logo.setImageResource(R.drawable.logo_boxeo);
} else {
    holder.logo.setImageResource(android.R.drawable.ic_menu_gallery);
}

        holder.progress.setVisibility(View.GONE);
        holder.progress.setProgress(0);

        String timeLabel = event.time == null ? "" : event.time.trim();

java.util.regex.Matcher timeMatcher =
        java.util.regex.Pattern.compile(
                "(?i)(\\d{1,2}):(\\d{2})\\s*(AM|PM)?"
        ).matcher(timeLabel);

if (timeMatcher.find()) {
    int hour = Integer.parseInt(timeMatcher.group(1));
    int minute = Integer.parseInt(timeMatcher.group(2));
    String meridiem = timeMatcher.group(3);

    if (minute <= 59
            && ((meridiem == null && hour <= 23)
            || (meridiem != null && hour >= 1 && hour <= 12))) {

        if (meridiem != null) {
            hour = hour % 12;
            if (meridiem.equalsIgnoreCase("PM")) {
                hour += 12;
            }
        }

        java.util.Calendar now = java.util.Calendar.getInstance();

        int currentMinutes =
                now.get(java.util.Calendar.HOUR_OF_DAY) * 60
                + now.get(java.util.Calendar.MINUTE);

        int startMinutes = hour * 60 + minute;
        int elapsedMinutes = currentMinutes - startMinutes;

        String comp = event.competition == null ? "" :
                event.competition.toLowerCase(java.util.Locale.ROOT);

        int duration = 120;

        if (comp.contains("nfl") ||
                (comp.contains("ncaa") && comp.contains("football"))) {
            duration = 210;
        } else if (comp.contains("mlb") || comp.contains("baseball")) {
            duration = 180;
        } else if (comp.contains("nba") || comp.contains("wnba") ||
                comp.contains("basketball") || comp.contains("baloncesto")) {
            duration = 150;
        } else if (comp.contains("nhl") || comp.contains("hockey")) {
            duration = 150;
        } else if (comp.contains("tennis") || comp.contains("tenis") ||
                comp.contains("atp") || comp.contains("wta") ||
                comp.contains("wimbledon") || comp.contains("roland garros") ||
                comp.contains("australian open") || comp.contains("us open")) {
            duration = 150;
        }

        if (elapsedMinutes >= 0 && elapsedMinutes < duration) {
            holder.progress.setVisibility(View.VISIBLE);
            holder.progress.setProgress(
                    Math.min(100, (elapsedMinutes * 100) / duration)
            );
        }

        if (elapsedMinutes >= 0 && elapsedMinutes < duration) {
            timeLabel = "EN VIVO";
        }
    }
}

if (timeLabel.isEmpty()
        || timeLabel.equalsIgnoreCase("null")
        || timeLabel.equalsIgnoreCase("undefined")) {
    holder.time.setText("");
    holder.time.setVisibility(View.GONE);
} else {
    holder.time.setText(timeLabel);
    holder.time.setVisibility(View.VISIBLE);
}

        String eventName = event.name == null ? "" : event.name.trim();

String[] teams = eventName.split("(?i)\\s+vs\\.?\\s+", 2);

if (teams.length == 2) {
    holder.name.setText(
            teams[0].trim().replaceAll("(?i)Montevideo City Torque", "Montevideo City")
            + "\nVS\n"
            + teams[1].trim()
    );
} else {
    holder.name.setText(eventName);
}

        int total = event.options.size();

                holder.count.setText(event.competition == null || event.competition.trim().isEmpty() ? "" : event.competition.trim());
	holder.itemView.setOnFocusChangeListener((v, hasFocus) -> {
    if (hasFocus
            && eventsPlayer != null
            && eventsPlayer.getVisibility() == View.VISIBLE) {

        int focusedPosition = holder.getBindingAdapterPosition();

        if (focusedPosition != RecyclerView.NO_POSITION) {
            // El foco de navegación no modifica el último evento seleccionado.
        }

        restartEventsOverlayTimer();
    }
});
        holder.itemView.setOnClickListener(v -> {
            int selectedPosition = holder.getBindingAdapterPosition();

            if (selectedPosition == RecyclerView.NO_POSITION) {
                return;
            }

            lastSelectedEventPosition = selectedPosition;
            EventItem selectedEvent = eventItems.get(selectedPosition);

            if (selectedEvent.options.isEmpty()) {
                eventsSubtitle.setText(
                        "Este evento no tiene opciones disponibles"
                );
                return;
            }

            if (selectedEvent.options.size() == 1) {
    authorizeEventOption(selectedEvent, selectedEvent.options.get(0));
} else {
    showEventOptions(selectedEvent);
}
        });
    
    }

    @Override
    public int getItemCount() {
        return eventItems.size();
    }
}
private void authorizeEventOption(EventItem event, EventOption option) {
    if (activationGuard == null || !activationGuard.isAuthorized()) {
        eventsSubtitle.setText("Dispositivo sin autorización");
        return;
    }

    eventsSubtitle.setText("Conectando: " + event.name);

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
                    "https://panel.n30ntvlt.com/api/stadiolab/eventos/autorizar"
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
            body.put("opcionId", option.id);

            try (OutputStream out = connection.getOutputStream()) {
                out.write(body.toString().getBytes(
                        StandardCharsets.UTF_8
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
                            StandardCharsets.UTF_8
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
                if (isFinishing() || isDestroyed()
                        || !activationGuard.isAuthorized()) {
                    return;
                }

                eventsSubtitle.setText("");

int currentPosition = eventItems.indexOf(event);
if (currentPosition >= 0) {
    lastSelectedEventPosition = currentPosition;
    playingEventPosition = currentPosition;
    playingOptionId = String.valueOf(option.id);
}

MediaItem mediaItem = new MediaItem.Builder()
        .setUri(finalPlaybackUrl)
        .setMimeType("application/x-mpegURL")
        .build();

player.stop();
player.clearMediaItems();
intentosLiveWindow = 0;
        player.setMediaItem(mediaItem);

eventsPlayer.setVisibility(View.VISIBLE);
eventsMenu.setVisibility(View.VISIBLE);

player.prepare();
player.play();

restartEventsOverlayTimer();
            });

        } catch (Exception e) {
            Log.e("StadioLabEvents", "Error al autorizar evento", e);

            runOnUiThread(() -> {
                if (!isFinishing() && !isDestroyed()) {
                    eventsSubtitle.setText(
                            "No se pudo autorizar: " + e.getMessage()
                    );
                }
            });

        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }).start();
}
private void showEventOptions(EventItem event) {
    Dialog dialog = new Dialog(this);
    dialog.setContentView(R.layout.dialog_event_options);

    Window window = dialog.getWindow();

    if (window != null) {
        window.setBackgroundDrawable(
                new ColorDrawable(Color.TRANSPARENT)
        );
    }

    TextView title = dialog.findViewById(
            R.id.eventDialogTitle
    );

    TextView subtitle = dialog.findViewById(
            R.id.eventDialogSubtitle
    );

    LinearLayout container = dialog.findViewById(
            R.id.eventOptionsContainer
    );

    title.setText(event.name);

    subtitle.setText(
            event.options.size() + " opciones disponibles"
    );

    for (EventOption option : event.options) {
        TextView button = new TextView(this);

        button.setText(option.name);
        button.setTextColor(Color.WHITE);
        button.setTextSize(17);
        button.setGravity(android.view.Gravity.CENTER_VERTICAL);
        button.setPadding(24, 14, 24, 14);

        button.setFocusable(true);
        button.setClickable(true);
        button.setBackgroundResource(
                R.drawable.event_card_background
        );

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        params.bottomMargin = 8;

        container.addView(button, params);

        button.setOnClickListener(v -> {
    dialog.dismiss();

    authorizeEventOption(event, option);
});
    }

    dialog.show();

    if (window != null) {
        window.setBackgroundDrawableResource(
                R.drawable.event_dialog_background
        );

        window.setLayout(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT
        );
    }

    if (container.getChildCount() > 0) {
        container.getChildAt(0).requestFocus();
    }
}
}






