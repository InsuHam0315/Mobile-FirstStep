package com.example.firststep.game.ui.integration;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.example.firststep.BuildConfig;
import com.example.firststep.R;
import com.example.firststep.game.composition.integration.*;
import com.example.firststep.game.domain.Progress;
import com.example.firststep.game.ui.home.GameHomeActivity;
import java.util.concurrent.*;

/** Explicit debug entry; its stable UUID is separate from all progress databases. */
public final class IntegrationEntryActivity extends AppCompatActivity {
  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private IntegrationContext context;
  private boolean busy, resumed, invalid;
  private long generation;
  private IntegrationContext supplied;

  public IntegrationContext contextForTest() {
    return context;
  }

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    setContentView(R.layout.game_integration_entry);
    ViewCompat.setOnApplyWindowInsetsListener(
        findViewById(R.id.game_integration_entry_root),
        (view, insets) -> {
          androidx.core.graphics.Insets bars =
              insets.getInsets(
                  WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
          view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
          return insets;
        });
    findViewById(R.id.game_integration_entry_back).setOnClickListener(v -> finish());
    findViewById(R.id.game_integration_entry_normal)
        .setOnClickListener(v -> open(Progress.Mode.NORMAL));
    findViewById(R.id.game_integration_entry_demo)
        .setOnClickListener(v -> open(Progress.Mode.DEMO));
    enabled(false);
    ((TextView) findViewById(R.id.game_integration_entry_status))
        .setText(R.string.game_integration_loading);
    boolean explicit = IntegrationIntentCodec.hasIntegrationContext(getIntent());
    supplied = explicit ? IntegrationIntentCodec.readContext(getIntent()) : null;
    invalid = !BuildConfig.DEBUG || (explicit && supplied == null);
  }

  @Override
  protected void onResume() {
    super.onResume();
    resumed = true;
    if (invalid) {
      error();
      return;
    }
    if (context == null) load();
    else {
      busy = false;
      enabled(true);
    }
  }

  @Override
  protected void onPause() {
    resumed = false;
    generation++;
    busy = false;
    enabled(false);
    super.onPause();
  }

  private boolean current(long token) {
    return resumed && generation == token && !isDestroyed() && !isFinishing();
  }

  private void load() {
    if (!resumed || busy) return;
    busy = true;
    enabled(false);
    long token = ++generation;
    worker.execute(
        () -> {
          try {
            IntegrationContext c = supplied;
            if (c == null) {
              String run = new StableRunStore(getApplicationContext()).resolve(null);
              if (run == null) throw new IllegalStateException();
              c =
                  IntegrationContext.create(
                      IntegrationContext.DATASET,
                      run,
                      IntegrationContext.TARGET,
                      Progress.Mode.NORMAL,
                      IntegrationContext.Clock.REAL,
                      null);
            }
            IntegrationContext loaded =
                IntegrationRouteAccess.bound(getApplicationContext(), c).context;
            runOnUiThread(
                () -> {
                  if (!current(token)) return;
                  busy = false;
                  context = loaded;
                  enabled(true);
                  ((TextView) findViewById(R.id.game_integration_entry_status))
                      .setText(description(loaded));
                });
          } catch (Exception failure) {
            runOnUiThread(
                () -> {
                  if (current(token)) error();
                });
          }
        });
  }

  private String description(IntegrationContext c) {
    return getString(
        R.string.game_integration_context,
        c.runId,
        c.mode.name(),
        getString(
            c.clock == IntegrationContext.Clock.REAL
                ? R.string.game_integration_real_clock
                : R.string.game_integration_fake_clock));
  }

  private void open(Progress.Mode mode) {
    if (!resumed || context == null || busy) return;
    busy = true;
    enabled(false);
    long token = ++generation;
    IntegrationContext chosen =
        IntegrationContext.create(
            context.datasetId,
            context.runId,
            context.targetId,
            mode,
            context.clock,
            context.fakeNow);
    worker.execute(
        () -> {
          try {
            IntegrationContext loaded =
                IntegrationRouteAccess.bound(getApplicationContext(), chosen).context;
            runOnUiThread(
                () -> {
                  if (!current(token)) return;
                  busy = false;
                  enabled(true);
                  Intent i = new Intent(this, GameHomeActivity.class);
                  IntegrationIntentCodec.writeContext(i, loaded);
                  startActivity(i);
                });
          } catch (Exception failure) {
            runOnUiThread(
                () -> {
                  if (current(token)) error();
                });
          }
        });
  }

  private void enabled(boolean value) {
    findViewById(R.id.game_integration_entry_normal).setEnabled(value);
    findViewById(R.id.game_integration_entry_demo).setEnabled(value);
  }

  private void error() {
    if (!resumed || isDestroyed() || isFinishing()) return;
    busy = false;
    enabled(false);
    ((TextView) findViewById(R.id.game_integration_entry_status))
        .setText(R.string.game_integration_error);
  }

  @Override
  protected void onDestroy() {
    worker.shutdown();
    super.onDestroy();
  }
}
