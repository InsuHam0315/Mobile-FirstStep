package com.example.firststep.game.ui.home;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.*;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.OnBackPressedCallback;
import com.example.firststep.BuildConfig;
import com.example.firststep.R;
import com.example.firststep.data.Mission;
import com.example.firststep.facility.*;
import com.example.firststep.game.composition.home.HomeHost;
import com.example.firststep.game.device.homemap.H05LocationAdapter;
import com.example.firststep.game.domain.Progress;
import com.example.firststep.game.presentation.home.*;
import com.example.firststep.game.ui.discovery.DiscoveryActivity;
import com.example.firststep.game.ui.intro.IntroQuizActivity;
import com.example.firststep.map.CampusMapView;
import java.util.*;

/** Replaceable Android view binder. Storage and progress policies live in HomeHost. */
public final class GameHomeActivity extends AppCompatActivity {
  public static final String EXTRA_DEBUG_RUN = "game_intro_debug_run",
      EXTRA_DEBUG_CLOCK = "game_intro_debug_clock";
  private static final int LOCATION_REQUEST = 505;
  private final OnBackPressedCallback backCallback = new OnBackPressedCallback(false) {
    @Override public void handleOnBackPressed() { navigateBack(); }
  };
  private String resultsKey = null, favoritesKey = null;
  private boolean mapFromDetail, resumed, locationResultPending;
  private HomeHost host;
  private HomeController controller;
  private CampusMapView map;
  private H05LocationAdapter location;
  private String run;
  private boolean fake, binding;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final Runnable tick =
      new Runnable() {
        public void run() {
          if (host != null && controller != null) {
            host.tick(controller.snapshot().progress);
            handler.postDelayed(this, 1000);
          }
        }
      };

  public HomeState snapshot() {
    return controller == null ? null : controller.snapshot();
  }

  public HomeState getSnapshotForTest() {
    return snapshot();
  }

  public void debugInjectLocationForTest(Location value) {
    if (BuildConfig.DEBUG
        && location != null
        && resumed
        && snapshot().screen == HomeState.Screen.MAP) location.debugInject(value);
  }

  public void setDebugClockForTest(long value) {
    if (BuildConfig.DEBUG && fake && host != null) {
      host.setFakeNow(value);
      host.tick(snapshot().progress);
    }
  }

  public long getDebugClockForTest() {
    return fake && host != null ? host.now() : 0;
  }

  @Override
  protected void onCreate(Bundle saved) {
    super.onCreate(saved);
    getOnBackPressedDispatcher().addCallback(this, backCallback);
    locationResultPending = saved != null && saved.getBoolean("locationResultPending");
    setContentView(R.layout.game_home_activity);
    map = findViewById(R.id.game_map_view);
    androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(
        findViewById(R.id.game_home_scroll),
        (view, insets) -> {
          androidx.core.graphics.Insets bars =
              insets.getInsets(
                  androidx.core.view.WindowInsetsCompat.Type.systemBars()
                      | androidx.core.view.WindowInsetsCompat.Type.displayCutout());
          view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
          return insets;
        });
    run = BuildConfig.DEBUG ? getIntent().getStringExtra(EXTRA_DEBUG_RUN) : null;
    fake = BuildConfig.DEBUG && getIntent().hasExtra(EXTRA_DEBUG_CLOCK);
    try {
      if (run != null && !UUID.fromString(run).toString().equals(run))
        throw new IllegalArgumentException();
      if (fake && run == null) throw new IllegalArgumentException();
    } catch (Exception invalid) {
      ((TextView) findViewById(R.id.game_home_status)).setText(R.string.game_home_invalid_debug);
      for (int id :
          new int[] {
            R.id.game_home_panel,
            R.id.game_home_search_panel,
            R.id.game_home_detail_panel,
            R.id.game_map_panel,
            R.id.game_profile_content,
            R.id.game_ending_content,
            R.id.game_home_retry
          }) findViewById(id).setVisibility(View.GONE);
      findViewById(R.id.game_home_back).setOnClickListener(v -> finish());
      return;
    }
    long now =
        saved != null && saved.containsKey("clock")
            ? saved.getLong("clock")
            : getIntent().getLongExtra(EXTRA_DEBUG_CLOCK, 0);
    host = new HomeHost(this, run, fake, now);
    controller = new HomeController(host, this::render);
    host.attach(controller);
    if (saved != null) {
      mapFromDetail = saved.getBoolean("mapFromDetail");
      try {
        controller.restore(
            HomeState.Screen.valueOf(saved.getString("screen")),
            Progress.Mode.valueOf(saved.getString("mode")),
            saved.getString("query", ""),
            saved.getString("category", ""),
            saved.getBoolean("favorites"),
            saved.getString("selected"));
      } catch (Exception ignored) {
      }
    }
    map.configure(
        Collections.emptyList(),
        new CampusMapView.Listener() {
          public void status(String m) {
            controller.tile(m);
          }

          public void selected(Mission ignored) {}
        });
    location =
        new H05LocationAdapter(
            this,
            (fix, message) -> {
              map.current(fix);
              controller.location(message);
            });
    bind();
    render(snapshot());
  }

  private void bind() {
    click(R.id.game_home_back, this::navigateBack);
    click(R.id.game_home_retry, () -> controller.retry());
    click(
        R.id.game_home_mode,
        () ->
            controller.mode(
                snapshot().mode == Progress.Mode.NORMAL
                    ? Progress.Mode.DEMO
                    : Progress.Mode.NORMAL));
    click(R.id.game_home_search, () -> controller.navigate(HomeState.Screen.SEARCH));
    click(
        R.id.game_home_map,
        () -> {
          mapFromDetail = false;
          controller.navigate(HomeState.Screen.MAP);
        });
    click(R.id.game_home_profile, () -> controller.navigate(HomeState.Screen.PROFILE));
    click(R.id.game_home_ending, () -> controller.navigate(HomeState.Screen.ENDING));
    click(R.id.game_home_prototype, this::openIntro);
    click(R.id.game_home_discovery, () -> startActivity(new Intent(this, DiscoveryActivity.class)));
    click(
        R.id.game_home_detail_map,
        () -> {
          mapFromDetail = true;
          controller.navigate(HomeState.Screen.MAP);
        });
    click(
        R.id.game_home_detail_favorite,
        () -> {
          HomeState s = snapshot();
          controller.favorite(s.selectedId, !s.favoriteIds.contains(s.selectedId));
        });
    click(
        R.id.game_home_detail_official,
        () -> {
          Facility f = selected();
          if (f != null && FacilityCatalogValidator.isOfficialUrl(f.officialUrl)) {
            try {
              startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(f.officialUrl)));
            } catch (android.content.ActivityNotFoundException ignored) {
            }
          }
        });
    click(R.id.game_map_current, this::requestLocation);
    click(R.id.game_map_zoom_in, () -> map.zoom(1));
    click(R.id.game_map_zoom_out, () -> map.zoom(-1));
    click(R.id.game_map_retry, () -> map.retry());
    findViewById(R.id.game_map_debug_location)
        .setVisibility(BuildConfig.DEBUG ? View.VISIBLE : View.GONE);
    click(
        R.id.game_map_debug_location,
        () -> {
          Location l = new Location("h05-debug");
          l.setLatitude(35.9456011);
          l.setLongitude(126.6802365);
          l.setAccuracy(30);
          l.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
          debugInjectLocationForTest(l);
        });
    EditText query = findViewById(R.id.game_home_search_query);
    query.addTextChangedListener(
        new TextWatcher() {
          public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

          public void onTextChanged(CharSequence s, int start, int before, int count) {
            if (!binding)
              controller.filters(s.toString(), snapshot().category, snapshot().favoritesOnly);
          }

          public void afterTextChanged(Editable e) {}
        });
    Spinner categories = findViewById(R.id.game_home_search_category);
    String[] names = {getString(R.string.game_home_all), "학습", "학생지원", "진로·취업", "생활"};
    categories.setAdapter(
        new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names));
    categories.setOnItemSelectedListener(
        new android.widget.AdapterView.OnItemSelectedListener() {
          public void onNothingSelected(android.widget.AdapterView<?> p) {}

          public void onItemSelected(
              android.widget.AdapterView<?> p, View v, int position, long id) {
            if (!binding) {
              String c = position == 0 ? "" : names[position];
              if (!c.equals(snapshot().category))
                controller.filters(snapshot().query, c, snapshot().favoritesOnly);
            }
          }
        });
    ((CheckBox) findViewById(R.id.game_home_search_favorites))
        .setOnCheckedChangeListener(
            (b, value) -> {
              if (!binding) controller.filters(snapshot().query, snapshot().category, value);
            });
  }

  private void click(int id, Runnable action) {
    findViewById(id).setOnClickListener(v -> action.run());
  }

  private void openIntro() {
    Intent i =
        new Intent(this, IntroQuizActivity.class)
            .putExtra("game_intro_building_id", HomeHost.TARGET)
            .putExtra("game_intro_mode", snapshot().mode.name())
            .putExtra("game_intro_instant_demo", false);
    if (run != null) i.putExtra(EXTRA_DEBUG_RUN, run);
    if (fake) i.putExtra(EXTRA_DEBUG_CLOCK, host.now());
    startActivity(i);
  }

  private void requestLocation() {
    if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
            || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
      requestPermissions(
          new String[] {
            Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION
          },
          LOCATION_REQUEST);
    } else location.start();
  }

  @Override
  public void onRequestPermissionsResult(int request, String[] permissions, int[] grants) {
    super.onRequestPermissionsResult(request, permissions, grants);
    if (request == LOCATION_REQUEST && location != null) {
      locationResultPending = true;
      consumeLocationResult();
    }
  }

  // Permission dialogs can return before onResume. Defer the effect, never start it in background.
  private void consumeLocationResult() {
    if (!resumed || !locationResultPending) return;
    locationResultPending = false;
    if (snapshot() != null && snapshot().screen == HomeState.Screen.MAP) location.start();
  }

  private Facility selected() {
    for (Facility f : snapshot().facilities) if (f.id.equals(snapshot().selectedId)) return f;
    return null;
  }

  private void render(HomeState s) {
    if (s == null) return;
    backCallback.setEnabled(s.screen != HomeState.Screen.HOME);
    binding = true;
    show(R.id.game_home_back, s.screen != HomeState.Screen.HOME);
    show(R.id.game_home_panel, s.screen == HomeState.Screen.HOME);
    show(R.id.game_home_search_panel, s.screen == HomeState.Screen.SEARCH);
    show(R.id.game_home_detail_panel, s.screen == HomeState.Screen.DETAIL);
    show(R.id.game_map_panel, s.screen == HomeState.Screen.MAP);
    show(R.id.game_profile_content, s.screen == HomeState.Screen.PROFILE);
    show(R.id.game_ending_content, s.screen == HomeState.Screen.ENDING);
    String contextLabel = s.mode.name();
    if (run != null) contextLabel += " · " + getString(R.string.game_home_isolated);
    if (fake) contextLabel += " · " + getString(R.string.game_home_fake_clock);
    ((TextView) findViewById(R.id.game_home_context)).setText(contextLabel);
    ((TextView) findViewById(R.id.game_home_status))
        .setText(
            s.loading
                ? getString(R.string.game_home_loading)
                : s.error != null ? getString(R.string.game_home_error) : "");
    show(R.id.game_home_retry, s.error != null);
    String status =
        s.progress == null
            ? getString(R.string.game_home_loading)
            : s.progress.stamp
                ? getString(R.string.game_home_progress_done)
                : s.progress.introDoneAt == null
                    ? getString(R.string.game_home_progress_none)
                    : s.eligible
                        ? getString(R.string.game_home_progress_ready)
                        : getString(R.string.game_home_progress_wait, s.remainingSeconds);
    ((TextView) findViewById(R.id.game_home_progress))
        .setText(
            getString(
                R.string.game_home_progress_format,
                getString(R.string.game_home_target) + " · " + s.mode.name(),
                status));
    ((TextView) findViewById(R.id.game_home_mode))
        .setText(getString(R.string.game_home_mode_format, s.mode.name()));
    ((TextView) findViewById(R.id.game_profile_content))
        .setText(
            getString(R.string.game_home_profile_format, getString(R.string.game_home_local_profile), s.mode.name(), status));
    ((TextView) findViewById(R.id.game_ending_content))
        .setText(
            s.endingAvailable ? R.string.game_home_ending_open : R.string.game_home_ending_locked);
    EditText query = findViewById(R.id.game_home_search_query);
    if (!query.getText().toString().equals(s.query)) {
      query.setText(s.query);
      query.setSelection(query.length());
    }
    String[] cats = {"", "학습", "학생지원", "진로·취업", "생활"};
    int index = 0;
    for (int n = 0; n < cats.length; n++) if (cats[n].equals(s.category)) index = n;
    Spinner spinner = findViewById(R.id.game_home_search_category);
    if (spinner.getSelectedItemPosition() != index) spinner.setSelection(index);
    ((CheckBox) findViewById(R.id.game_home_search_favorites)).setChecked(s.favoritesOnly);
    String nextResults = rowKey(s.results, false, s);
    String nextFavorites = rowKey(s.facilities, true, s);
    if (!nextResults.equals(resultsKey)) {
      populate((LinearLayout) findViewById(R.id.game_home_results), s.results, false, s);
      resultsKey = nextResults;
    }
    if (!nextFavorites.equals(favoritesKey)) {
      populate((LinearLayout) findViewById(R.id.game_home_favorites), s.facilities, true, s);
      favoritesKey = nextFavorites;
    }
    Facility f = selected();
    if (f != null) {
      ((TextView) findViewById(R.id.game_home_detail_content))
          .setText(
              getString(R.string.game_home_detail_format, f.name, f.category, f.summary,
                  f.locationStatus, f.hoursStatus, getString(R.string.game_home_no_coordinate)));
      ((Button) findViewById(R.id.game_home_detail_favorite))
          .setText(
              s.favoriteIds.contains(f.id)
                  ? R.string.game_home_unfavorite
                  : R.string.game_home_favorite);
    } else {
      ((TextView) findViewById(R.id.game_home_detail_content))
          .setText(R.string.game_home_missing_selection);
      findViewById(R.id.game_home_detail_favorite).setEnabled(false);
      findViewById(R.id.game_home_detail_official).setEnabled(false);
    }
    if (f != null) {
      findViewById(R.id.game_home_detail_favorite).setEnabled(true);
      findViewById(R.id.game_home_detail_official).setEnabled(true);
    }
    ((TextView) findViewById(R.id.game_map_selection))
        .setText(
            mapFromDetail && f != null
                ? getString(R.string.game_home_selected_map_format, f.name, f.id, getString(R.string.game_home_no_coordinate))
                : getString(R.string.game_home_no_coordinate));
    ((TextView) findViewById(R.id.game_map_status))
        .setText(
            s.locationMessage.isEmpty()
                ? getString(R.string.game_home_location_initial)
                : s.locationMessage);
    ((TextView) findViewById(R.id.game_map_tile_status)).setText(s.tileMessage);
    binding = false;
  }

  private String rowKey(List<Facility> list, boolean onlyFavorites, HomeState state) {
    StringBuilder key = new StringBuilder();
    for (Facility f : list)
      if (!onlyFavorites || state.favoriteIds.contains(f.id)) key.append(f.id).append('\n');
    return key.toString();
  }

  private void populate(
      LinearLayout parent, List<Facility> list, boolean onlyFavorites, HomeState s) {
    parent.removeAllViews();
    int count = 0;
    for (Facility f : list) {
      if (onlyFavorites && !s.favoriteIds.contains(f.id)) continue;
      count++;
      Button b = (Button) getLayoutInflater().inflate(R.layout.game_home_row, parent, false);
      b.setText(getString(R.string.game_home_row_format, f.name, f.category, f.summary));
      b.setTag(f.id);
      b.setContentDescription(f.name + " · " + f.category);
      b.setMinHeight((int) (48 * getResources().getDisplayMetrics().density));
      b.setOnClickListener(v -> controller.select(f.id));
      parent.addView(b);
    }
    if (count == 0) {
      TextView empty = new TextView(this);
      empty.setText(R.string.game_home_empty);
      empty.setTextColor(getColor(R.color.game_color_text));
      empty.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,
          getResources().getDimension(R.dimen.game_text_body));
      parent.addView(empty);
    }
  }

  private void show(int id, boolean visible) {
    findViewById(id).setVisibility(visible ? View.VISIBLE : View.GONE);
  }

  @Override
  protected void onResume() {
    super.onResume();
    resumed = true;
    if (controller != null) {
      map.active(true);
      controller.resume();
      consumeLocationResult();
      handler.postDelayed(tick, 1000);
    }
  }

  @Override
  protected void onPause() {
    resumed = false;
    handler.removeCallbacks(tick);
    if (controller != null) {
      controller.pause();
      location.stop();
      map.active(false);
    }
    super.onPause();
  }

  @Override
  protected void onDestroy() {
    if (location != null) location.close();
    if (map != null) map.close();
    if (host != null) host.close();
    super.onDestroy();
  }

  @Override
  protected void onSaveInstanceState(Bundle out) {
    super.onSaveInstanceState(out);
    HomeState s = snapshot();
    if (s == null) return;
    out.putBoolean("mapFromDetail", mapFromDetail);
    out.putBoolean("locationResultPending", locationResultPending);
    out.putString("screen", s.screen.name());
    out.putString("mode", s.mode.name());
    out.putString("query", s.query);
    out.putString("category", s.category);
    out.putBoolean("favorites", s.favoritesOnly);
    out.putString("selected", s.selectedId);
    if (fake) out.putLong("clock", host.now());
  }

  private void navigateBack() {
    if (controller == null || snapshot().screen == HomeState.Screen.HOME) {
      finish();
      return;
    }
    HomeState s = snapshot();
    if (s.screen == HomeState.Screen.MAP) location.stop();
    if (s.screen == HomeState.Screen.MAP && mapFromDetail && s.selectedId != null)
      controller.navigate(HomeState.Screen.DETAIL);
    else if (s.screen == HomeState.Screen.DETAIL) controller.navigate(HomeState.Screen.SEARCH);
    else controller.navigate(HomeState.Screen.HOME);
  }
}
