package com.example.firststep.game.ui.guide;

import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.lifecycle.*;
import com.example.firststep.R;
import com.example.firststep.game.content.*;
import java.util.*;

/** Replaceable Java/XML binder. External intent extras never select content or storage. */
public final class GuideActivity extends AppCompatActivity {
  private GuideViewModel model;
  private LinearLayout content;
  private ScrollView scroll;
  private Button primary, readAgain;
  private TextView title, status;
  private Object renderedKey;
  private int restoredScroll = -1;
  private boolean binding;
  private final Map<RadioButton, String[]> choices = new LinkedHashMap<>();

  public GuideBundle guideForTest() {
    return model.guide;
  }

  public boolean unavailableForTest() {
    return model.unavailable;
  }

  public String panelForTest() {
    return model.panel.name();
  }

  public Map<String, String> answersForTest() {
    return model.practice == null ? Collections.emptyMap() : model.practice.getAnswers();
  }

  @Override
  protected void onCreate(Bundle saved) {
    super.onCreate(saved);
    setContentView(R.layout.game_guide_activity);
    GuideViewModel.Saved restored = readSaved(saved);
    if (saved != null) restoredScroll = Math.max(0, saved.getInt("guide_scroll", 0));
    model =
        new ViewModelProvider(
                this,
                new ViewModelProvider.Factory() {
                  @Override
                  public <T extends ViewModel> T create(Class<T> type) {
                    return type.cast(
                        new GuideViewModel(GuideAdapters.create(GuideActivity.this), restored));
                  }
                })
            .get(GuideViewModel.class);
    content = findViewById(R.id.game_guide_content);
    scroll = findViewById(R.id.game_guide_scroll);
    title = findViewById(R.id.game_guide_title);
    status = findViewById(R.id.game_guide_status);
    primary = findViewById(R.id.game_guide_primary);
    readAgain = findViewById(R.id.game_guide_read_again);
    ViewCompat.setAccessibilityHeading(title, true);
    ViewCompat.setOnApplyWindowInsetsListener(
        scroll,
        (view, insets) -> {
          androidx.core.graphics.Insets bars =
              insets.getInsets(
                  androidx.core.view.WindowInsetsCompat.Type.systemBars()
                      | androidx.core.view.WindowInsetsCompat.Type.displayCutout());
          view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
          return insets;
        });
    primary.setOnClickListener(
        v -> {
          if (model.unavailable) model.reload(true);
          else if (model.panel == GuideViewModel.Panel.GUIDE) model.startPractice();
          else if (model.panel == GuideViewModel.Panel.PRACTICE) model.submit();
          else model.retry();
        });
    readAgain.setOnClickListener(v -> model.showGuide());
    findViewById(R.id.game_guide_close).setOnClickListener(v -> finish());
  }

  @Override
  protected void onStart() {
    super.onStart();
    model.observe(this::render);
  }

  @Override
  protected void onStop() {
    model.observe(null);
    super.onStop();
  }

  private void render() {
    boolean available = !model.loading && !model.unavailable && model.guide != null;
    status.setText(
        model.loading
            ? R.string.game_guide_loading
            : model.unavailable
                ? R.string.game_guide_unavailable
                : model.sourceUnavailable
                    ? R.string.game_guide_source_failed
                    : R.string.game_guide_offline);
    TextView environment = findViewById(R.id.game_guide_environment);
    environment.setVisibility(model.environment.simulatedInputs ? View.VISIBLE : View.GONE);
    environment.setText(R.string.game_guide_test_environment);
    Object key = available ? model.panel : model.loading ? "loading" : "unavailable";
    if (!Objects.equals(renderedKey, key)) {
      renderedKey = key;
      content.removeAllViews();
      choices.clear();
      if (available) {
        if (model.panel == GuideViewModel.Panel.GUIDE) showIntroduction();
        else if (model.panel == GuideViewModel.Panel.PRACTICE) showQuestions();
        else showResult();
      }
      if (available) {
        int y = restoredScroll >= 0 ? restoredScroll : 0;
        restoredScroll = -1;
        scroll.post(() -> scroll.scrollTo(0, y));
      }
    }
    binding = true;
    if (model.practice != null)
      for (Map.Entry<RadioButton, String[]> entry : choices.entrySet()) {
        String[] ids = entry.getValue();
        entry.getKey().setChecked(ids[1].equals(model.practice.getAnswers().get(ids[0])));
      }
    binding = false;
    primary.setVisibility(model.loading ? View.GONE : View.VISIBLE);
    primary.setEnabled(
        !model.loading
            && (model.unavailable
                || model.panel != GuideViewModel.Panel.PRACTICE
                || model.practice.getAnswers().size() == model.guide.getQuestions().size()));
    primary.setText(
        model.unavailable
            ? R.string.game_guide_reload
            : model.panel == GuideViewModel.Panel.GUIDE
                ? R.string.game_guide_start
                : model.panel == GuideViewModel.Panel.PRACTICE
                    ? R.string.game_guide_submit
                    : R.string.game_guide_retry);
    readAgain.setVisibility(
        available && model.panel != GuideViewModel.Panel.GUIDE ? View.VISIBLE : View.GONE);
    title.setText(
        available && model.panel == GuideViewModel.Panel.PRACTICE
            ? R.string.game_guide_practice_title
            : R.string.game_guide_entry);
  }

  private TextView text(String value, boolean heading) {
    TextView view = new TextView(this);
    view.setText(value);
    view.setTextColor(getColor(R.color.game_color_text));
    view.setTextSize(
        android.util.TypedValue.COMPLEX_UNIT_PX,
        getResources().getDimension(R.dimen.game_text_body));
    int space = getResources().getDimensionPixelSize(R.dimen.game_space_medium);
    view.setPadding(0, space / 2, 0, space / 2);
    if (heading) {
      view.setTypeface(null, android.graphics.Typeface.BOLD);
      ViewCompat.setAccessibilityHeading(view, true);
    }
    content.addView(view, new LinearLayout.LayoutParams(-1, -2));
    return view;
  }

  private GuideBundle.Source source(String id) {
    for (GuideBundle.Source source : model.guide.getSources())
      if (source.getId().equals(id)) return source;
    throw new IllegalStateException("Missing validated source");
  }

  private GuideBundle.Claim claim(String id) {
    for (GuideBundle.Claim claim : model.guide.getClaims())
      if (claim.getId().equals(id)) return claim;
    throw new IllegalStateException("Missing validated claim");
  }

  private void sourceButton(GuideBundle.Source source) {
    Button button =
        (Button) getLayoutInflater().inflate(R.layout.game_guide_source_button, content, false);
    button.setText(getString(R.string.game_guide_source_open, source.getLabel()));
    button.setTag("guide_source:" + source.getId());
    button.setMinHeight(getResources().getDimensionPixelSize(R.dimen.game_touch_min));
    button.setOnClickListener(v -> model.openSource(source));
    content.addView(button, new LinearLayout.LayoutParams(-1, -2));
  }

  private void showIntroduction() {
    text(getString(R.string.game_guide_sequence), true);
    for (GuideBundle.Claim claim : model.guide.getClaims()) {
      text(claim.getText(), false);
      GuideBundle.Source source = source(claim.getSourceId());
      text(
          getString(R.string.game_guide_source, source.getLabel())
              + "\n"
              + getString(R.string.game_guide_checked, source.getCheckedDate()),
          false);
      text(source.getUrl(), false);
      sourceButton(source);
    }
  }

  private void showQuestions() {
    text(getString(R.string.game_guide_practice_help), false);
    int i = 0;
    for (GuideBundle.Question question : model.guide.getQuestions()) {
      text(getString(R.string.game_guide_question, ++i, question.getText()), true);
      RadioGroup group = new RadioGroup(this);
      group.setOrientation(LinearLayout.VERTICAL);
      group.setSaveEnabled(false);
      content.addView(group, new LinearLayout.LayoutParams(-1, -2));
      for (GuideBundle.Option option : question.getOptions()) {
        RadioButton button = new RadioButton(this);
        button.setId(View.generateViewId());
        button.setText(option.getText());
        button.setTag("guide_option:" + question.getId() + ":" + option.getId());
        button.setTextColor(getColor(R.color.game_color_text));
        button.setSaveEnabled(false);
        button.setMinHeight(getResources().getDimensionPixelSize(R.dimen.game_touch_min));
        button.setTextSize(
            android.util.TypedValue.COMPLEX_UNIT_PX,
            getResources().getDimension(R.dimen.game_text_body));
        choices.put(button, new String[] {question.getId(), option.getId()});
        group.addView(button, new RadioGroup.LayoutParams(-1, -2));
        button.setOnCheckedChangeListener(
            (v, checked) -> {
              if (checked && !binding) model.select(question.getId(), option.getId());
            });
      }
    }
  }

  private static String optionLabel(GuideBundle.Question question, String id) {
    for (GuideBundle.Option option : question.getOptions())
      if (option.getId().equals(id)) return option.getText();
    throw new IllegalStateException("Missing validated option");
  }

  private void showResult() {
    text(
            getString(
                R.string.game_guide_score,
                model.practice.getScore(),
                model.guide.getQuestions().size()),
            true)
        .setTag("guide_score");
    text(getString(R.string.game_guide_no_progress), false);
    int i = 0;
    for (GuideBundle.Question question : model.guide.getQuestions()) {
      text(getString(R.string.game_guide_question, ++i, question.getText()), true);
      text(
          getString(
              model.practice.isCorrect(question.getId())
                  ? R.string.game_guide_correct
                  : R.string.game_guide_incorrect),
          false);
      text(
          getString(
              R.string.game_guide_chosen,
              optionLabel(question, model.practice.getAnswers().get(question.getId()))),
          false);
      text(
          getString(R.string.game_guide_answer, optionLabel(question, question.getAnswerId())),
          false);
      text(question.getExplanation(), false);
      GuideBundle.Source source = source(claim(question.getClaimId()).getSourceId());
      text(
          getString(R.string.game_guide_source, source.getLabel())
              + "\n"
              + getString(R.string.game_guide_checked, source.getCheckedDate()),
          false);
      sourceButton(source);
    }
  }

  private static GuideViewModel.Saved readSaved(Bundle state) {
    if (state == null || !state.containsKey("guide_dataset")) return null;
    try {
      ArrayList<String> qs = state.getStringArrayList("guide_questions"),
          os = state.getStringArrayList("guide_options");
      if (qs == null || os == null || qs.size() != os.size() || qs.size() > 3)
        throw new IllegalArgumentException();
      Map<String, String> answers = new LinkedHashMap<>();
      for (int i = 0; i < qs.size(); i++)
        if (answers.put(qs.get(i), os.get(i)) != null) throw new IllegalArgumentException();
      if (!(state.get("guide_dataset") instanceof String)
          || !(state.get("guide_version") instanceof Integer)
          || !(state.get("guide_panel") instanceof String)
          || !(state.get("guide_submitted") instanceof Boolean))
        throw new IllegalArgumentException();
      return new GuideViewModel.Saved(
          state.getString("guide_dataset"),
          state.getInt("guide_version"),
          state.getString("guide_panel"),
          answers,
          state.getBoolean("guide_submitted"),
          true);
    } catch (RuntimeException invalid) {
      return new GuideViewModel.Saved("", 0, "", Collections.emptyMap(), false, false);
    }
  }

  @Override
  protected void onSaveInstanceState(Bundle state) {
    GuideViewModel.Saved saved = model.snapshot();
    if (saved != null) {
      state.putString("guide_dataset", saved.datasetId);
      state.putInt("guide_version", saved.version);
      state.putString("guide_panel", saved.panel);
      state.putBoolean("guide_submitted", saved.submitted);
      state.putStringArrayList("guide_questions", new ArrayList<>(saved.answers.keySet()));
      state.putStringArrayList("guide_options", new ArrayList<>(saved.answers.values()));
    }
    state.putInt("guide_scroll", scroll.getScrollY());
    super.onSaveInstanceState(state);
  }
}
