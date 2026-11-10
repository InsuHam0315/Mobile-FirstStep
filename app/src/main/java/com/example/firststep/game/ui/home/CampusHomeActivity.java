package com.example.firststep.game.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.example.firststep.FacilityDirectoryActivity;
import com.example.firststep.R;
import com.example.firststep.RouteMapActivity;
import com.example.firststep.game.composition.home.account.AccountHomeReader;
import com.example.firststep.game.composition.home.account.AccountHomeTestRuntime;
import com.example.firststep.game.presentation.home.account.AccountHomeController;
import com.example.firststep.game.presentation.home.account.AccountHomeState;
import com.example.firststep.game.ui.authored.AuthoredEpisodeActivity;
import com.example.firststep.game.ui.guide.GuideActivity;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Campus browsing and owner-scoped Home progress. */
public final class CampusHomeActivity extends AppCompatActivity {
  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private final Handler main = new Handler(Looper.getMainLooper());
  private AccountHomeController progress;
  private final Runnable tick = new Runnable() {
    @Override public void run() {
      progress.tick();
      main.postDelayed(this, 1000L);
    }
  };

  @Override
  protected void onCreate(Bundle state) {
    super.onCreate(state);
    setContentView(R.layout.game_campus_home_activity);
    ViewCompat.setAccessibilityHeading(findViewById(R.id.game_campus_home_title), true);
    ViewCompat.setOnApplyWindowInsetsListener(
        findViewById(R.id.game_campus_home_scroll),
        (view, insets) -> {
          Insets bars = insets.getInsets(
              WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
          view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
          return insets;
        });
    findViewById(R.id.game_campus_home_map)
        .setOnClickListener(v -> startActivity(RouteMapActivity.browseIntent(this)));
    findViewById(R.id.game_campus_home_facilities)
        .setOnClickListener(v -> startActivity(new Intent(this, FacilityDirectoryActivity.class)));
    findViewById(R.id.game_campus_home_guide)
        .setOnClickListener(v -> startActivity(new Intent(this, GuideActivity.class)));
    Runnable openEpisode = () -> startActivity(AuthoredEpisodeActivity.intent(this));
    AccountHomeTestRuntime.Binding test = AccountHomeTestRuntime.current();
    if (test == null) {
      AccountHomeReader reader = new AccountHomeReader(this);
      progress = new AccountHomeController(reader, reader.isRegistered(), System::currentTimeMillis,
          worker, main::post, this::renderProgress, openEpisode);
    } else {
      progress = new AccountHomeController(test.source, test.registered, test.clock,
          worker, main::post, this::renderProgress, openEpisode);
    }
    findViewById(R.id.game_campus_home_authored).setOnClickListener(v -> progress.openAuthored());
    findViewById(R.id.game_campus_progress_retry).setOnClickListener(v -> progress.refresh());
    findViewById(R.id.game_campus_home_back).setOnClickListener(v -> finish());
  }

  @Override protected void onResume() {
    super.onResume();
    progress.resume();
    main.postDelayed(tick, 1000L);
  }

  @Override protected void onPause() {
    main.removeCallbacks(tick);
    progress.pause();
    super.onPause();
  }

  @Override protected void onDestroy() {
    worker.shutdown();
    super.onDestroy();
  }

  private void renderProgress(AccountHomeState state) {
    int status;
    int action = R.string.game_authored_entry;
    switch (state.status) {
      case UNAVAILABLE: status = R.string.game_campus_progress_unavailable; break;
      case NO_ACCOUNT: status = R.string.game_campus_progress_no_account; break;
      case UNSTARTED: status = R.string.game_campus_progress_unstarted; break;
      case WAITING:
        status = R.string.game_campus_progress_waiting;
        action = R.string.game_campus_progress_waiting_open;
        break;
      case READY:
        status = R.string.game_campus_progress_ready;
        action = R.string.game_campus_progress_quiz_open;
        break;
      case COMPLETE:
        status = R.string.game_campus_progress_complete;
        action = R.string.game_campus_progress_result_open;
        break;
      case FAILED: status = R.string.game_campus_progress_failed; break;
      default: status = R.string.game_campus_progress_loading;
    }
    TextView current = findViewById(R.id.game_campus_progress_state);
    if (state.status == AccountHomeState.Status.WAITING) {
      long seconds = state.remainingMillis / 1000 + (state.remainingMillis % 1000 == 0 ? 0 : 1);
      current.setText(getString(R.string.game_campus_progress_waiting, seconds / 60, seconds % 60));
    } else current.setText(status);

    findViewById(R.id.game_campus_progress_reminder_note).setVisibility(
        state.status == AccountHomeState.Status.WAITING ? View.VISIBLE : View.GONE);
    TextView remote = findViewById(R.id.game_campus_progress_remote);
    remote.setVisibility(state.server == AccountHomeState.Server.NONE ? View.GONE : View.VISIBLE);
    if (state.server != AccountHomeState.Server.NONE) {
      remote.setText(state.server == AccountHomeState.Server.COMPLETE
          ? R.string.game_campus_progress_remote_complete : R.string.game_campus_progress_remote_intro);
    } else remote.setText("");

    Button open = findViewById(R.id.game_campus_home_authored);
    open.setText(action);
    boolean retry = state.status == AccountHomeState.Status.FAILED
        || state.status == AccountHomeState.Status.NO_ACCOUNT;
    open.setEnabled(!retry && state.status != AccountHomeState.Status.LOADING);
    findViewById(R.id.game_campus_progress_retry).setVisibility(retry ? View.VISIBLE : View.GONE);

    TextView favorites = findViewById(R.id.game_campus_home_favorites);
    if (!state.favoritesAvailable) favorites.setText(R.string.game_campus_home_favorites_unavailable);
    else if (state.favorites.isEmpty()) favorites.setText(R.string.game_campus_home_favorites_empty);
    else favorites.setText(TextUtils.join("\n", state.favorites));
  }
}
