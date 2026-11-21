package com.example.firststep.game.ui.guide;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.example.firststep.BuildConfig;
import com.example.firststep.R;
import com.example.firststep.game.content.*;
import com.example.firststep.game.speech.IntroSpeech;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Transient preview. Rendering and speech callbacks never advance the narration. */
public final class NarrationActivity extends AppCompatActivity {
  private static final String KOREAN_ACTION = "com.example.firststep.narration.KOREAN";

  public static Intent koreanIntent(Context context) {
    return new Intent(context, NarrationActivity.class).setAction(KOREAN_ACTION);
  }

  private NarrationContent content;
  private GuideBundle guide;
  private NarrationState state;
  private IntroSpeech speech;
  private TextView caption, position, source, status, provenance, sourceDetails;
  private Button previous, next, replay, finishButton;
  private LinearProgressIndicator progress;
  private final ExecutorService loader = Executors.newSingleThreadExecutor();
  private boolean destroyed;
  private int restoredPosition;
  @Override protected void onCreate(Bundle saved) {
    super.onCreate(saved);
    setContentView(R.layout.game_narration_activity);
    boolean korean = KOREAN_ACTION.equals(getIntent().getAction());
    ((TextView) findViewById(R.id.narration_heading)).setText(korean
        ? R.string.game_korean_narration_heading : R.string.game_narration_heading);
    ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.narration_root), (view, insets) -> {
      androidx.core.graphics.Insets bars = insets.getInsets(
          WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
      view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
      return insets;
    });
    caption = findViewById(R.id.narration_caption);
    position = findViewById(R.id.narration_position);
    source = findViewById(R.id.narration_source);
    status = findViewById(R.id.narration_status);
    provenance = findViewById(R.id.narration_provenance);
    sourceDetails = findViewById(R.id.narration_source_details);
    previous = findViewById(R.id.narration_previous);
    next = findViewById(R.id.narration_next);
    replay = findViewById(R.id.narration_replay);
    finishButton = findViewById(R.id.narration_finish);
    progress = findViewById(R.id.narration_progress);
    findViewById(R.id.narration_back).setOnClickListener(v -> finish());
    findViewById(R.id.narration_info).setOnClickListener(v ->
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.game_narration_info_title)
            .setMessage(R.string.game_narration_limit)
            .setPositiveButton(R.string.game_narration_info_confirm, null)
            .show());
    findViewById(R.id.narration_skip).setOnClickListener(v -> finishToPractice());
    finishButton.setOnClickListener(v -> finishToPractice());
    restoredPosition = saved == null ? 0 : saved.getInt("narration_position", 0);
    findViewById(R.id.narration_controls).setVisibility(View.GONE);
    caption.setText(R.string.game_guide_loading);
    android.content.Context app = getApplicationContext();
    loader.execute(() -> {
      try {
        NarrationContent loadedContent = NarrationAsset.load(app, korean
            ? NarrationContent.KOREAN_DATASET_ID : NarrationContent.DATASET_ID);
        GuideBundle loadedGuide = new AssetGuideRepository(app, korean
            ? GuideDecoder.KOREAN_DATASET_ID : GuideDecoder.DATASET_ID).load();
        runOnUiThread(() -> {
          if (destroyed || isFinishing()) return;
          content = loadedContent;
          guide = loadedGuide;
          state = new NarrationState(content.sentences().size(), restoredPosition);
          findViewById(R.id.narration_controls).setVisibility(View.VISIBLE);
          progress.setMax(content.sentences().size());
          replay.setEnabled(true);
          render();
        });
      } catch (Exception failure) {
        runOnUiThread(() -> {
          if (!destroyed && !isFinishing()) caption.setText(R.string.game_narration_unavailable);
        });
      }
    });
    previous.setOnClickListener(v -> { stopSpeech(); state.previous(); render(); });
    next.setOnClickListener(v -> { stopSpeech(); state.next(); render(); });
    replay.setOnClickListener(v -> {
      status.setText("");
      if (speech != null && state != null) speech.speak(content.sentences().get(state.position()).text);
    });
  }
  @Override protected void onStart() {
    super.onStart();
    speech = new IntroSpeech(this, BuildConfig.DEBUG ? getIntent().getStringExtra("game_narration_debug_speech_mode") : null, message -> status.setText(message));
  }
  @Override protected void onPause() {
    if (speech != null) speech.stop();
    super.onPause();
  }
  @Override protected void onStop() {
    if (speech != null) { speech.close(); speech = null; }
    super.onStop();
  }
  @Override protected void onSaveInstanceState(Bundle out) {
    out.putInt("narration_position", state == null ? restoredPosition : state.position());
    super.onSaveInstanceState(out);
  }
  @Override protected void onDestroy() {
    destroyed = true;
    loader.shutdownNow();
    super.onDestroy();
  }
  private void stopSpeech() { if (speech != null) speech.stop(); status.setText(""); }
  private void finishToPractice() { stopSpeech(); setResult(RESULT_OK); finish(); }
  private void render() {
    NarrationContent.Sentence sentence = content.sentences().get(state.position());
    caption.setText(sentence.text);
    position.setText(getString(R.string.game_narration_position, state.position() + 1, content.sentences().size()));
    progress.setProgressCompat(state.position() + 1, false);
    previous.setEnabled(state.position() > 0);
    boolean last = state.position() + 1 == content.sentences().size();
    next.setVisibility(last ? View.GONE : View.VISIBLE);
    next.setEnabled(!last);
    finishButton.setVisibility(last ? View.VISIBLE : View.GONE);
    sourceDetails.setVisibility(View.GONE);
    provenance.setVisibility(View.VISIBLE);
    if (sentence.provenance == NarrationContent.Provenance.FICTIONAL) {
      provenance.setText(R.string.game_narration_fictional);
    } else if (sentence.provenance == NarrationContent.Provenance.EDITORIAL) {
      provenance.setText(R.string.game_narration_editorial);
    } else {
      provenance.setVisibility(View.GONE);
    }
    source.setVisibility(View.GONE);
    source.setOnClickListener(null);
    source.setContentDescription(null);
    if (sentence.provenance != NarrationContent.Provenance.CLAIM) return;
    GuideBundle.Source origin = findSource(sentence.claimId);
    if (origin == null) {
      status.setText(R.string.game_narration_source_unavailable);
      return;
    }
    sourceDetails.setText(getString(R.string.game_narration_source_details,
        origin.getLabel(), origin.getCheckedDate(), origin.getUrl()));
    sourceDetails.setVisibility(View.VISIBLE);
    source.setText(R.string.game_guide_review_source);
    source.setContentDescription(getString(R.string.game_narration_source_accessibility,
        origin.getLabel(), origin.getCheckedDate()));
    source.setVisibility(View.VISIBLE);
    source.setOnClickListener(v -> {
      if (!GuideAdapters.openOfficialSource(this, origin)) {
        status.setText(R.string.game_guide_source_failed);
      }
    });
  }

  private GuideBundle.Source findSource(String claimId) {
    for (GuideBundle.Claim claim : guide.getClaims()) {
      if (!claim.getId().equals(claimId)) continue;
      for (GuideBundle.Source source : guide.getSources()) {
        if (source.getId().equals(claim.getSourceId())) return source;
      }
      break;
    }
    return null;
  }
}
