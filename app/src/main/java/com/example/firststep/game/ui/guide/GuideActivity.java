package com.example.firststep.game.ui.guide;

import android.os.Bundle;
import android.content.Intent;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.lifecycle.*;
import com.example.firststep.R;
import com.example.firststep.BuildConfig;
import com.example.firststep.game.composition.integration.*;
import com.example.firststep.game.domain.Progress;
import com.example.firststep.game.ui.home.GameHomeActivity;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.example.firststep.game.content.*;
import java.util.*;

/** Replaceable binder for the two registered offline guides. */
public final class GuideActivity extends AppCompatActivity {
  private static final String KOREAN_ACTION = "com.example.firststep.guide.KOREAN";
  private GuideViewModel model;
  private boolean korean, openingKorean;
  private final ExecutorService demoWorker = Executors.newSingleThreadExecutor();
  private boolean demoResumed, demoBusy;
  private long demoGeneration;
  private LinearLayout content;
  private ScrollView scroll;
  private Button primary, readAgain;
  private TextView title, status;
  private Object renderedKey;
  private int restoredScroll = -1;
  private int questionIndex;
  private boolean binding;
  private final Set<String> expandedSections = new LinkedHashSet<>();
  private boolean pendingPreviewPractice;
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
    korean = KOREAN_ACTION.equals(getIntent().getAction());
    String dataset = korean ? GuideDecoder.KOREAN_DATASET_ID : GuideDecoder.DATASET_ID;
    GuideViewModel.Saved restored = readSaved(saved);
    pendingPreviewPractice = saved != null && saved.getBoolean("guide_pending_preview_practice", false);
    if (saved != null) restoredScroll = Math.max(0, saved.getInt("guide_scroll", 0));
    if (saved != null) {
      questionIndex = saved.getInt("guide_question_index", 0);
      ArrayList<String> sections = saved.getStringArrayList("guide_expanded_sections");
      if (sections != null) expandedSections.addAll(sections);
    }
    model =
        new ViewModelProvider(
                this,
                new ViewModelProvider.Factory() {
                  @Override
                  public <T extends ViewModel> T create(Class<T> type) {
                    return type.cast(
                        new GuideViewModel(GuideAdapters.create(GuideActivity.this, dataset),
                            restored, dataset));
                  }
                })
            .get(GuideViewModel.class);
    content = findViewById(R.id.game_guide_content);
    scroll = findViewById(R.id.game_guide_scroll);
    title = findViewById(R.id.game_guide_title);
    status = findViewById(R.id.game_guide_status);
    primary = findViewById(R.id.game_guide_primary);
    readAgain = findViewById(R.id.game_guide_read_again);
    ((TextView) findViewById(R.id.game_guide_department)).setText(korean
        ? R.string.game_korean_guide_department : R.string.game_software_guide_department);
    Button department = findViewById(R.id.game_guide_department_switch);
    department.setText(korean ? R.string.game_korean_guide_return : R.string.game_korean_guide_open);
    department.setOnClickListener(v -> {
      if (korean) finish();
      else if (!openingKorean) {
        openingKorean = true;
        startActivity(new Intent(this, GuideActivity.class).setAction(KOREAN_ACTION));
      }
    });
    ViewCompat.setAccessibilityHeading(title, true);
    ViewCompat.setOnApplyWindowInsetsListener(
        findViewById(R.id.game_guide_root),
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
          else if (model.panel == GuideViewModel.Panel.PRACTICE) {
            if (lastQuestion()) model.submit();
            else moveQuestion(1);
          } else {
            questionIndex = 0;
            model.retry();
          }
        });
    findViewById(R.id.game_guide_previous).setOnClickListener(v -> moveQuestion(-1));
    findViewById(R.id.game_guide_quiz_back).setOnClickListener(v -> model.showGuide());
    getOnBackPressedDispatcher().addCallback(this,
        new androidx.activity.OnBackPressedCallback(true) {
          @Override public void handleOnBackPressed() {
            if (model.panel == GuideViewModel.Panel.GUIDE || model.guide == null) finish();
            else model.showGuide();
          }
        });
    readAgain.setOnClickListener(v -> model.showGuide());
    findViewById(R.id.game_guide_close).setOnClickListener(v -> finish());
  }

  @Override
  protected void onResume() {
    super.onResume();
    demoResumed = true;
    openingKorean = false;
  }

  @Override
  protected void onPause() {
    demoResumed = false;
    demoBusy = false;
    demoGeneration++;
    super.onPause();
  }

  @Override
  protected void onDestroy() {
    demoWorker.shutdown();
    super.onDestroy();
  }

  private void openSoftwareDemo() {
    if (korean || !BuildConfig.DEBUG || !demoResumed || demoBusy) return;
    demoBusy = true;
    final long token = ++demoGeneration;
    demoWorker.execute(() -> {
      try {
        String run = new StableRunStore(getApplicationContext())
            .resolve(IntegrationContext.SOFTWARE_DATASET, null);
        IntegrationContext requested = IntegrationContext.create(
            IntegrationContext.SOFTWARE_DATASET, run, IntegrationContext.TARGET,
            Progress.Mode.DEMO, IntegrationContext.Clock.REAL, null);
        IntegrationContext bound = IntegrationRouteAccess.bound(getApplicationContext(), requested).context;
        runOnUiThread(() -> {
          if (!demoResumed || token != demoGeneration || isFinishing() || isDestroyed()) return;
          demoBusy = false;
          Intent intent = new Intent(this, GameHomeActivity.class);
          IntegrationIntentCodec.writeContext(intent, bound);
          startActivity(intent);
        });
      } catch (Exception failure) {
        runOnUiThread(() -> {
          if (!demoResumed || token != demoGeneration || isFinishing() || isDestroyed()) return;
          demoBusy = false;
          Toast.makeText(this, R.string.game_guide_demo_unavailable, Toast.LENGTH_LONG).show();
        });
      }
    });
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
    if (pendingPreviewPractice && !model.loading && !model.unavailable && model.guide != null) {
      pendingPreviewPractice = false;
      model.startPractice();
      return;
    }
    boolean available = !model.loading && !model.unavailable && model.guide != null;
    status.setText(
        model.loading
            ? R.string.game_guide_loading
            : model.unavailable
                ? R.string.game_guide_unavailable
                : model.sourceUnavailable
                    ? R.string.game_guide_source_failed
                    : R.string.game_guide_offline);
    boolean introduction = available && model.panel == GuideViewModel.Panel.GUIDE;
    boolean quiz = available && model.panel == GuideViewModel.Panel.PRACTICE;
    boolean result = available && model.panel == GuideViewModel.Panel.RESULT;
    boolean quizFlow = quiz || result;
    findViewById(R.id.game_guide_department_switch).setVisibility(
        introduction ? View.VISIBLE : View.GONE);
    if (quiz) questionIndex = Math.max(0, Math.min(questionIndex, model.guide.getQuestions().size() - 1));
    placeControls(quizFlow);
    findViewById(R.id.game_guide_fixed_controls).setVisibility(quizFlow ? View.VISIBLE : View.GONE);
    findViewById(R.id.game_guide_backdrop).setVisibility(introduction ? View.VISIBLE : View.GONE);
    androidx.core.view.WindowInsetsControllerCompat bars =
        androidx.core.view.WindowCompat.getInsetsController(getWindow(), scroll);
    bars.setAppearanceLightStatusBars(!introduction);
    bars.setAppearanceLightNavigationBars(!introduction);
    TextView arrivalNotice = findViewById(R.id.game_guide_unverified_notice);
    arrivalNotice.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, introduction ? 14 : 16);
    findViewById(R.id.game_guide_masthead).setVisibility(introduction ? View.VISIBLE : View.GONE);
    View panel = findViewById(R.id.game_guide_panel);
    panel.setBackgroundResource(introduction ? R.drawable.game_guide_translucent_panel : 0);
    LinearLayout.LayoutParams panelSize = (LinearLayout.LayoutParams) panel.getLayoutParams();
    int margin = introduction ? getResources().getDimensionPixelSize(R.dimen.game_space_large) : 0;
    if (panelSize.getMarginStart() != margin) {
      panelSize.setMarginStart(margin);
      panelSize.setMarginEnd(margin);
      panelSize.bottomMargin = margin;
      panel.setLayoutParams(panelSize);
    }
    title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, introduction
        ? android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, 22,
            getResources().getDisplayMetrics())
        : getResources().getDimension(R.dimen.game_text_title));
    findViewById(R.id.game_guide_no_progress_notice)
        .setVisibility(available ? View.GONE : View.VISIBLE);
    findViewById(R.id.game_guide_unverified_notice).setVisibility(quizFlow ? View.GONE : View.VISIBLE);
    findViewById(R.id.game_guide_quiz_back).setVisibility(quizFlow ? View.VISIBLE : View.GONE);
    findViewById(R.id.game_guide_previous).setVisibility(quiz && questionIndex > 0 ? View.VISIBLE : View.GONE);
    findViewById(R.id.game_guide_quiz_notice).setVisibility(quiz && questionIndex == 0 ? View.VISIBLE : View.GONE);
    findViewById(R.id.game_guide_close).setVisibility(quizFlow ? View.GONE : View.VISIBLE);
    status.setVisibility(available && !model.sourceUnavailable ? View.GONE : View.VISIBLE);
    TextView environment = findViewById(R.id.game_guide_environment);
    environment.setVisibility(model.environment.simulatedInputs ? View.VISIBLE : View.GONE);
    environment.setText(R.string.game_guide_test_environment);
    Object key = !available ? (model.loading ? "loading" : "unavailable")
        : quiz ? Integer.valueOf(questionIndex) : model.panel;
    if (!Objects.equals(renderedKey, key)) {
      renderedKey = key;
      content.removeAllViews();
      choices.clear();
      if (available) {
        if (introduction) showIntroduction();
        else if (quiz) showQuestions();
        else showResult();
        int y = restoredScroll >= 0 ? restoredScroll : 0;
        restoredScroll = -1;
        scroll.post(() -> scroll.scrollTo(0, y));
      }
    }
    binding = true;
    if (model.practice != null) {
      for (Map.Entry<RadioButton, String[]> entry : choices.entrySet()) {
        String[] ids = entry.getValue();
        entry.getKey().setChecked(ids[1].equals(model.practice.getAnswers().get(ids[0])));
      }
    }
    binding = false;
    primary.setVisibility(model.loading ? View.GONE : View.VISIBLE);
    primary.setEnabled(!model.loading && (!quiz || (lastQuestion()
        ? model.practice.getAnswers().size() == model.guide.getQuestions().size()
        : model.practice.getAnswers().containsKey(model.guide.getQuestions().get(questionIndex).getId()))));
    primary.setText(model.unavailable ? R.string.game_guide_reload
        : quiz ? (lastQuestion() ? R.string.game_guide_submit : R.string.game_guide_next)
        : result ? R.string.game_guide_retry : R.string.game_guide_start);
    readAgain.setVisibility(result ? View.VISIBLE : View.GONE);
    title.setText(korean
        ? quiz ? R.string.game_korean_guide_practice
            : result ? R.string.game_korean_guide_result : R.string.game_korean_guide_title
        : quiz ? R.string.game_guide_practice_title
            : result ? R.string.game_guide_result_title : R.string.game_guide_entry);
  }

  private TextView text(String value, boolean heading) {
    return text(content, value, heading);
  }

  private TextView text(LinearLayout parent, String value, boolean heading) {
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
    parent.addView(view, new LinearLayout.LayoutParams(-1, -2));
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
    sourceButton(content, source, getString(R.string.game_guide_source_open, source.getLabel()));
  }

  private void sourceButton(LinearLayout parent, GuideBundle.Source source, String label) {
    Button button =
        (Button) getLayoutInflater().inflate(R.layout.game_guide_source_button, parent, false);
    button.setText(label);
    button.setTag("guide_source:" + source.getId());
    button.setMinHeight(getResources().getDimensionPixelSize(R.dimen.game_touch_min));
    button.setOnClickListener(v -> model.openSource(source));
    parent.addView(button, new LinearLayout.LayoutParams(-1, -2));
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode == 101 && resultCode == RESULT_OK) {
      pendingPreviewPractice = true;
      if (!model.loading && !model.unavailable && model.guide != null) {
        pendingPreviewPractice = false;
        model.startPractice();
      }
    }
  }

  private void showIntroduction() {
    View hero = getLayoutInflater().inflate(R.layout.game_guide_hero, content, false);
    hero.findViewById(R.id.game_narration_entry).setOnClickListener(v ->
        startActivityForResult(korean ? NarrationActivity.koreanIntent(this)
            : new Intent(this, NarrationActivity.class), 101));
    View demo = hero.findViewById(R.id.game_guide_demo_entry);
    demo.setVisibility(!korean && BuildConfig.DEBUG ? View.VISIBLE : View.GONE);
    demo.setOnClickListener(v -> openSoftwareDemo());
    content.addView(hero);
    if (korean) {
      text(getString(R.string.game_korean_guide_intro), true);
      text(getString(R.string.game_korean_guide_office_notice), false);
    }
    text(getString(R.string.game_guide_sections), true);
    for (GuideBundle.Claim claim : model.guide.getClaims()) {
      addGuideSection(claim);
    }
    text(getString(R.string.game_guide_no_progress), false);
    text(getString(R.string.game_guide_offline), false)
        .setTextColor(getColor(R.color.game_reskin_muted));
  }

  private void addGuideSection(GuideBundle.Claim claim) {
    View card = getLayoutInflater().inflate(R.layout.game_guide_section, content, false);
    Button toggle = card.findViewById(R.id.game_guide_section_toggle);
    LinearLayout detail = card.findViewById(R.id.game_guide_section_detail);
    int label = sectionLabel(claim.getId());
    toggle.setText(label);
    GuideBundle.Source source = source(claim.getSourceId());
    text(detail, claim.getText(), false);
    text(detail, getString(R.string.game_guide_source, source.getLabel())
        + "\n" + getString(R.string.game_guide_checked, source.getCheckedDate()), false)
        .setTextColor(getColor(R.color.game_reskin_muted));
    sourceButton(detail, source, getString(R.string.game_guide_open_official));
    toggle.setTag("guide_section:" + claim.getId());
    int icon = sectionIcon(label);
    boolean expanded = expandedSections.contains(claim.getId());
    updateSection(toggle, detail, icon, expanded);
    toggle.setOnClickListener(v -> {
      boolean opening = detail.getVisibility() != View.VISIBLE;
      if (opening) expandedSections.add(claim.getId());
      else expandedSections.remove(claim.getId());
      updateSection(toggle, detail, icon, opening);
    });
    content.addView(card);
  }

  private static int sectionLabel(String claimId) {
    switch (claimId) {
      case "college_membership": return R.string.game_guide_section_college;
      case "ko_college_claim": return R.string.game_guide_section_college;
      case "office_guidance": return R.string.game_guide_section_visit;
      case "ko_office_claim": return R.string.game_korean_guide_office;
      case "learning_values": return R.string.game_guide_section_department;
      case "ko_study_claim": return R.string.game_korean_guide_studies;
      case "help_resources": return R.string.game_guide_section_life;
      case "ko_info_claim": return R.string.game_guide_section_life;
      default: return R.string.game_guide_sections;
    }
  }

  private static int sectionIcon(int label) {
    if (label == R.string.game_guide_section_college) return R.drawable.game_guide_icon_college;
    if (label == R.string.game_guide_section_visit
        || label == R.string.game_korean_guide_office) return R.drawable.game_guide_icon_visit;
    if (label == R.string.game_guide_section_department
        || label == R.string.game_korean_guide_studies) return R.drawable.game_guide_icon_book;
    if (label == R.string.game_guide_section_life) return R.drawable.game_guide_icon_people;
    return 0;
  }

  private void updateSection(Button toggle, View detail, int icon, boolean expanded) {
    toggle.setCompoundDrawablesRelativeWithIntrinsicBounds(
        icon, 0, expanded ? R.drawable.game_guide_icon_up : R.drawable.game_guide_icon_down, 0);
    ViewCompat.setStateDescription(toggle, getString(
        expanded ? R.string.game_guide_expanded : R.string.game_guide_collapsed));
    detail.setVisibility(expanded ? View.VISIBLE : View.GONE);
  }

  private boolean lastQuestion() {
    return questionIndex == model.guide.getQuestions().size() - 1;
  }

  private void moveQuestion(int delta) {
    questionIndex += delta;
    render();
  }

  private void placeControls(boolean fixed) {
    LinearLayout host = findViewById(
        fixed ? R.id.game_guide_fixed_controls : R.id.game_guide_inline_controls);
    View controls = findViewById(R.id.game_guide_controls);
    if (controls.getParent() == host) return;
    ((LinearLayout) controls.getParent()).removeView(controls);
    host.addView(controls);
  }

  private void showQuestions() {
    List<GuideBundle.Question> questions = model.guide.getQuestions();
    TextView count = text(getString(R.string.game_narration_position,
        questionIndex + 1, questions.size()), true);
    count.setGravity(android.view.Gravity.CENTER);
    LinearLayout progress = new LinearLayout(this);
    int gap = getResources().getDimensionPixelSize(R.dimen.game_reskin_control_space);
    for (int i = 0; i < questions.size(); i++) {
      View segment = new View(this);
      android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
      shape.setCornerRadius(gap);
      shape.setColor(getColor(i <= questionIndex ? R.color.game_color_action : R.color.game_reskin_line));
      segment.setBackground(shape);
      LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(0, gap, 1);
      size.setMargins(i == 0 ? 0 : gap, 0, 0, 0);
      progress.addView(segment, size);
    }
    LinearLayout.LayoutParams progressSize = new LinearLayout.LayoutParams(-1, -2);
    progressSize.setMargins(gap * 5, 0, gap * 5, gap * 3);
    content.addView(progress, progressSize);

    GuideBundle.Question question = questions.get(questionIndex);
    LinearLayout card = new LinearLayout(this);
    card.setOrientation(LinearLayout.VERTICAL);
    card.setPadding(gap * 2, gap * 2, gap * 2, gap * 2);
    card.setBackgroundResource(R.drawable.game_reskin_card);
    content.addView(card, new LinearLayout.LayoutParams(-1, -2));
    TextView prompt = text(card, question.getText(), true);
    prompt.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 21);
    RadioGroup group = new RadioGroup(this);
    group.setOrientation(LinearLayout.VERTICAL);
    group.setSaveEnabled(false);
    card.addView(group, new LinearLayout.LayoutParams(-1, -2));
    for (GuideBundle.Option option : question.getOptions()) {
      RadioButton button = (RadioButton) getLayoutInflater()
          .inflate(R.layout.game_guide_quiz_option, group, false);
      button.setId(View.generateViewId());
      button.setText(option.getText());
      button.setTag("guide_option:" + question.getId() + ":" + option.getId());
      choices.put(button, new String[] {question.getId(), option.getId()});
      group.addView(button);
      button.setOnCheckedChangeListener((v, checked) -> {
        if (checked && !binding) model.select(question.getId(), option.getId());
      });
    }
  }

  private static String optionLabel(GuideBundle.Question question, String id) {
    for (GuideBundle.Option option : question.getOptions())
      if (option.getId().equals(id)) return option.getText();
    throw new IllegalStateException("Missing validated option");
  }

  private void showResult() {
    TextView score = text(getString(R.string.game_guide_score,
        model.practice.getScore(), model.guide.getQuestions().size()), true);
    score.setTag("guide_score");
    score.setGravity(android.view.Gravity.CENTER);
    score.setBackgroundResource(R.drawable.game_reskin_tinted_card);
    score.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 30);
    int space = getResources().getDimensionPixelSize(R.dimen.game_space_medium);
    score.setPadding(space, space, space, space);
    text(getString(R.string.game_guide_explanations), true);
    int i = 0;
    for (GuideBundle.Question question : model.guide.getQuestions()) {
      View card = getLayoutInflater().inflate(R.layout.game_guide_section, content, false);
      Button toggle = card.findViewById(R.id.game_guide_section_toggle);
      LinearLayout detail = card.findViewById(R.id.game_guide_section_detail);
      int label = sectionLabel(question.getClaimId());
      if ("office_guidance".equals(question.getClaimId())) label = R.string.game_guide_review_location;
      if ("learning_values".equals(question.getClaimId())) label = R.string.game_guide_review_values;
      if ("help_resources".equals(question.getClaimId())) label = R.string.game_guide_review_notice;
      toggle.setText(getString(R.string.game_guide_question, ++i, getString(label)));
      updateSection(toggle, detail, 0, i == 1);
      toggle.setOnClickListener(v ->
          updateSection(toggle, detail, 0, detail.getVisibility() != View.VISIBLE));
      text(detail, getString(model.practice.isCorrect(question.getId())
          ? R.string.game_guide_correct : R.string.game_guide_incorrect), true);
      text(detail, getString(R.string.game_guide_chosen,
          optionLabel(question, model.practice.getAnswers().get(question.getId()))), false);
      text(detail, getString(R.string.game_guide_answer,
          optionLabel(question, question.getAnswerId())), false);
      text(detail, question.getExplanation(), false);
      GuideBundle.Source source = source(claim(question.getClaimId()).getSourceId());
      text(detail, getString(R.string.game_guide_source, source.getLabel()) + "\n"
          + getString(R.string.game_guide_checked, source.getCheckedDate()), false);
      sourceButton(detail, source, getString(R.string.game_guide_review_source));
      content.addView(card);
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
    state.putBoolean("guide_pending_preview_practice", pendingPreviewPractice);
    state.putStringArrayList("guide_expanded_sections", new ArrayList<>(expandedSections));
    state.putInt("guide_question_index", questionIndex);
    state.putInt("guide_scroll", scroll.getScrollY());
    super.onSaveInstanceState(state);
  }
}
