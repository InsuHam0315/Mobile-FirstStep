package com.example.firststep.game.ui.guide;

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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Transient preview. Rendering and speech callbacks never advance the narration. */
public final class NarrationActivity extends AppCompatActivity {
  private NarrationContent content;
  private GuideBundle guide;
  private NarrationState state;
  private IntroSpeech speech;
  private TextView caption, position, source, status;
  private Button previous, next;
  private final ExecutorService loader = Executors.newSingleThreadExecutor();
  private boolean destroyed;
  private int restoredPosition;
  @Override protected void onCreate(Bundle saved) {
    super.onCreate(saved);
    setContentView(R.layout.game_narration_activity);
    ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.narration_scroll), (view, insets) -> {
      androidx.core.graphics.Insets bars = insets.getInsets(
          WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
      view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
      return insets;
    });
    caption = findViewById(R.id.narration_caption);
    position = findViewById(R.id.narration_position);
    source = findViewById(R.id.narration_source);
    status = findViewById(R.id.narration_status);
    previous = findViewById(R.id.narration_previous);
    next = findViewById(R.id.narration_next);
    findViewById(R.id.narration_skip).setOnClickListener(v -> finishToPractice());
    findViewById(R.id.narration_finish).setOnClickListener(v -> finishToPractice());
    restoredPosition = saved == null ? 0 : saved.getInt("narration_position", 0);
    findViewById(R.id.narration_controls).setVisibility(View.GONE);
    findViewById(R.id.narration_finish).setVisibility(View.GONE);
    caption.setText(R.string.game_guide_loading);
    android.content.Context app = getApplicationContext();
    loader.execute(() -> {
      try {
        NarrationContent loadedContent = NarrationAsset.load(app);
        GuideBundle loadedGuide = new AssetGuideRepository(app).load();
        runOnUiThread(() -> {
          if (destroyed || isFinishing()) return;
          content = loadedContent;
          guide = loadedGuide;
          state = new NarrationState(content.sentences().size(), restoredPosition);
          findViewById(R.id.narration_controls).setVisibility(View.VISIBLE);
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
    findViewById(R.id.narration_replay).setOnClickListener(v -> {
      status.setText("");
      if (speech != null) speech.speak(content.sentences().get(state.position()).text);
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
    previous.setEnabled(state.position() > 0);
    next.setEnabled(state.position() + 1 < content.sentences().size());
    findViewById(R.id.narration_finish).setVisibility(state.position() + 1 == content.sentences().size() ? View.VISIBLE : View.GONE);
    source.setOnClickListener(null);
    source.setClickable(false);
    if (sentence.provenance == NarrationContent.Provenance.CLAIM) {
      GuideBundle.Claim match = null;
      for (GuideBundle.Claim claim : guide.getClaims()) if (claim.getId().equals(sentence.claimId)) match = claim;
      GuideBundle.Source origin = null;
      if (match != null) for (GuideBundle.Source item : guide.getSources()) if (item.getId().equals(match.getSourceId())) origin = item;
      if (origin != null) {
        final GuideBundle.Source selected = origin;
        source.setText(getString(R.string.game_narration_source, selected.getLabel(), selected.getCheckedDate(), selected.getUrl()));
        source.setOnClickListener(v -> {
          if (!GuideAdapters.openOfficialSource(this, selected)) status.setText(R.string.game_guide_source_failed);
        });
      } else source.setText(R.string.game_narration_source_unavailable);
    } else source.setText(sentence.provenance == NarrationContent.Provenance.FICTIONAL ? R.string.game_narration_fictional : R.string.game_narration_editorial);
  }
}
