package com.example.firststep.game.ui.identity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.View;
import android.widget.*;
import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import com.example.firststep.R;
import com.example.firststep.RouteMapActivity;
import com.example.firststep.game.auth.*;
import com.example.firststep.game.composition.home.CampusHomeRoute;
import com.example.firststep.game.composition.home.account.AccountHomeReader;
import com.example.firststep.game.composition.home.account.AccountHomeTestRuntime;
import com.example.firststep.game.composition.identity.*;
import com.example.firststep.game.presentation.home.account.AccountHomeController;
import com.example.firststep.game.presentation.home.account.AccountHomeState;
import com.example.firststep.game.presentation.home.entry.CampusChoice;
import com.example.firststep.game.ui.authored.AuthoredEpisodeActivity;
import com.example.firststep.game.ui.guide.GuideActivity;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Artwork-first launcher, optional identity, and explicit locally selected department routing. */
public final class IdentityActivity extends AppCompatActivity {
  private static final String TAB = "firststep.main.tab";
  private static final int HOME = 0, MY = 1;
  private IdentityViewModel model;
  private EditText name, student;
  private AutoCompleteTextView college, department;
  private boolean binding, entered, signInRequested, resumed, openingChild, progressActive;
  private int tab;
  private String displayedOwner;
  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private final Handler main = new Handler(Looper.getMainLooper());
  private AccountHomeController progress;
  private final Runnable tick = new Runnable() {
    public void run() {
      if (!progressActive) return;
      progress.tick();
      main.postDelayed(this, 1000L);
    }
  };
  private final OnBackPressedCallback back = new OnBackPressedCallback(false) {
    public void handleOnBackPressed() {
      if (tab == MY) { tab = HOME; render(model.controller.state()); }
      else if (model.controller.state().getUid() == null) {
        entered = false;
        render(model.controller.state());
      } else finish();
    }
  };

  /** The map returns to this existing shell without carrying identity or progress extras. */
  public static Intent tabIntent(Context context, boolean myPage) {
    return new Intent(context, IdentityActivity.class).putExtra(TAB, myPage ? MY : HOME)
        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
  }

  public SessionState stateForTest() { return model.controller.state(); }
  /** Read-only thread-safe fixture fence, including after this Activity is closed. */
  public boolean hasPendingLocalWritesForTest() { return model.hasPendingLocalWrites(); }

  @Override protected void onCreate(Bundle state) {
    super.onCreate(state);
    EdgeToEdge.enable(this);
    setContentView(R.layout.game_identity_activity);
    entered = state != null && state.getBoolean("main_entered");
    tab = state == null ? HOME : state.getInt("main_tab", HOME);
    signInRequested = state != null && state.getBoolean("main_login_requested");
    if (state == null && getIntent().hasExtra(TAB)) {
      entered = true; tab = getIntent().getIntExtra(TAB, HOME) == MY ? MY : HOME;
    }
    getOnBackPressedDispatcher().addCallback(this, back);
    model = new ViewModelProvider(this, new ViewModelProvider.Factory() {
      @Override public <T extends ViewModel> T create(Class<T> type) {
        return type.cast(new IdentityViewModel(H07VariantFactory.create(IdentityActivity.this),
            getApplicationContext()));
      }
    }).get(IdentityViewModel.class);
    name = findViewById(R.id.game_identity_name);
    student = findViewById(R.id.game_identity_student);
    college = findViewById(R.id.game_identity_college);
    department = findViewById(R.id.game_identity_department);
    name.setFilters(new InputFilter[] {new InputFilter.LengthFilter(80)});
    student.setFilters(new InputFilter[] {new InputFilter.LengthFilter(40)});
    college.setFilters(new InputFilter[] {new InputFilter.LengthFilter(120)});
    department.setFilters(new InputFilter[] {new InputFilter.LengthFilter(120)});
    college.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line,
        new String[] {CampusChoice.SOFTWARE_COLLEGE, CampusChoice.KOREAN_COLLEGE}));
    college.setOnClickListener(v -> college.showDropDown());
    department.setOnClickListener(v -> {
      String selected = college.getText().toString().trim();
      String[] options = CampusChoice.SOFTWARE_COLLEGE.equals(selected)
          ? new String[] {CampusChoice.SOFTWARE_DEPARTMENT}
          : CampusChoice.KOREAN_COLLEGE.equals(selected) ? new String[] {CampusChoice.KOREAN_DEPARTMENT}
              : new String[] {CampusChoice.SOFTWARE_DEPARTMENT, CampusChoice.KOREAN_DEPARTMENT};
      department.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, options));
      department.showDropDown();
    });
    TextWatcher watcher = new TextWatcher() {
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
      public void afterTextChanged(Editable s) {}
      public void onTextChanged(CharSequence s, int start, int before, int count) {
        if (binding) return;
        model.collegeDraft = college.getText().toString();
        Profile draft = inputProfile();
        if (model.controller.state().getUid() == null) model.guestDraft = draft;
        else model.controller.edit(draft.getName(), draft.getStudentNumber(), draft.getDepartment());
      }
    };
    name.addTextChangedListener(watcher);
    student.addTextChangedListener(watcher);
    college.addTextChangedListener(watcher);
    department.addTextChangedListener(watcher);
    department.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line,
        new String[] {CampusChoice.SOFTWARE_DEPARTMENT, CampusChoice.KOREAN_DEPARTMENT}));
    ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.game_identity_shell), (view, insets) -> {
      androidx.core.graphics.Insets bars = insets.getInsets(
          WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()
              | WindowInsetsCompat.Type.ime());
      view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
      return insets;
    });
    findViewById(R.id.game_identity_welcome_guest).setOnClickListener(v -> {
      entered = true; tab = HOME; render(model.controller.state());
    });
    for (int id : new int[] {R.id.game_identity_welcome_login, R.id.game_identity_login, R.id.game_main_google})
      findViewById(id).setOnClickListener(v -> signIn(false));
    findViewById(R.id.game_identity_reselect).setOnClickListener(v -> signIn(true));
    findViewById(R.id.game_identity_logout).setOnClickListener(v -> model.controller.logout());
    findViewById(R.id.game_identity_retry).setOnClickListener(v -> model.controller.retry());
    findViewById(R.id.game_identity_begin_edit).setOnClickListener(v -> model.controller.beginEdit());
    findViewById(R.id.game_identity_save).setOnClickListener(v -> save());
    findViewById(R.id.game_main_choose).setOnClickListener(v -> selectMyPage());
    findViewById(R.id.game_nav_home).setOnClickListener(v -> { tab = HOME; render(model.controller.state()); });
    findViewById(R.id.game_nav_my).setOnClickListener(v -> selectMyPage());
    findViewById(R.id.game_nav_map).setOnClickListener(v -> {
      if (!openingChild) { openingChild = true; startActivity(RouteMapActivity.mainBrowseIntent(this)); }
    });
    findViewById(R.id.game_main_open_guide).setOnClickListener(v -> openGuide());
    findViewById(R.id.game_identity_open_campus).setOnClickListener(v -> {
      if (!openingChild) { openingChild = true; startActivity(CampusHomeRoute.intent(this)); }
    });
    Runnable openEpisode = () -> startActivity(AuthoredEpisodeActivity.intent(this));
    AccountHomeTestRuntime.Binding test = AccountHomeTestRuntime.current();
    if (test == null) {
      AccountHomeReader reader = new AccountHomeReader(this);
      progress = new AccountHomeController(reader, reader.isRegistered(), System::currentTimeMillis,
          worker, main::post, this::renderProgress, openEpisode);
    } else progress = new AccountHomeController(test.source, test.registered, test.clock,
        worker, main::post, this::renderProgress, openEpisode);
    findViewById(R.id.game_main_authored).setOnClickListener(v -> progress.openAuthored());
    findViewById(R.id.game_main_progress_retry).setOnClickListener(v -> progress.refresh());
    model.localChanges.observe(this, ignored -> render(model.controller.state()));
  }

  @Override protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    setIntent(intent);
    entered = true;
    tab = intent.getIntExtra(TAB, HOME) == MY ? MY : HOME;
    render(model.controller.state());
  }

  @Override protected void onResume() {
    super.onResume();
    resumed = true;
    openingChild = false;
    model.runtime.attach(this);
    model.controller.resume();
    model.controller.setListener(this::render);
    render(model.controller.state());
    findViewById(R.id.game_identity_host).setVisibility(View.VISIBLE);
  }

  @Override protected void onPause() {
    resumed = false;
    updateProgressLifecycle();
    findViewById(R.id.game_identity_host).setVisibility(View.INVISIBLE);
    model.controller.setListener(null);
    model.controller.pause();
    super.onPause();
  }

  @Override protected void onDestroy() {
    worker.shutdown();
    model.runtime.detach(this);
    super.onDestroy();
  }

  @Override protected void onSaveInstanceState(Bundle state) {
    state.putBoolean("main_entered", entered);
    state.putInt("main_tab", tab);
    state.putBoolean("main_login_requested", signInRequested);
    super.onSaveInstanceState(state);
  }

  private void selectMyPage() { entered = true; tab = MY; render(model.controller.state()); }

  private void signIn(boolean reselect) {
    if (!resumed || model.controller.state().getStage() == SessionState.Stage.AUTH_BUSY) return;
    signInRequested = true;
    model.controller.signIn(reselect);
  }

  private Profile inputProfile() {
    return new Profile(name.getText().toString(), student.getText().toString(), department.getText().toString());
  }

  private void save() {
    SessionState s = model.controller.state();
    boolean guest = isGuest(s);
    if (!model.localReady || model.localBusy || (!guest && !s.canSave())) return;
    Profile p = inputProfile();
    model.collegeDraft = college.getText().toString();
    model.saveLocal(p, new CampusChoice(model.collegeDraft, p.getDepartment()));
    if (!guest) model.controller.save();
  }

  private void openGuide() {
    if (!resumed || openingChild || !model.localReady) return;
    CampusChoice.Guide selected = model.choice.getGuide();
    if (selected == CampusChoice.Guide.NONE) { selectMyPage(); return; }
    Intent intent = new Intent(this, GuideActivity.class);
    if (selected == CampusChoice.Guide.KOREAN) intent.setAction("com.example.firststep.guide.KOREAN");
    openingChild = true;
    startActivity(intent);
  }

  private boolean isGuest(SessionState s) {
    return s.getUid() == null && (s.getStage() == SessionState.Stage.LOGGED_OUT
        || s.getStage() == SessionState.Stage.UNCONFIGURED);
  }

  private void visible(int id, boolean show) { findViewById(id).setVisibility(show ? View.VISIBLE : View.GONE); }

  private void render(SessionState s) {
    if (model == null || name == null || !resumed) return;
    if (s.getUid() != null && (signInRequested || !entered)) {
      entered = true; tab = HOME; signInRequested = false;
    } else if (s.getFailure() != null) signInRequested = false;
    model.selectOwner(s.getUid());
    String owner = s.getUid() == null ? "guest" : "account:" + s.getUid();
    boolean ownerChanged = !Objects.equals(owner, displayedOwner);
    displayedOwner = owner;
    binding = true;
    boolean guest = isGuest(s);
    boolean canEdit = model.localReady && !model.localBusy && (guest || s.canEdit());
    Profile draft = guest ? model.guestDraft : s.getDraft();
    if (draft == null) draft = new Profile("", "", "");
    update(name, draft.getName(), ownerChanged);
    update(student, draft.getStudentNumber(), ownerChanged);
    update(department, draft.getDepartment(), ownerChanged);
    update(college, model.collegeDraft, ownerChanged);
    for (EditText field : new EditText[] {name, student, college, department}) field.setEnabled(canEdit);
    visible(R.id.game_identity_welcome, !entered);
    visible(R.id.game_main_dashboard, entered && tab == HOME);
    visible(R.id.game_identity_root, entered && tab == MY);
    visible(R.id.game_main_navigation, entered);
    visible(R.id.game_identity_begin_edit, s.getStage() == SessionState.Stage.COMPLETE);
    visible(R.id.game_identity_save, guest || s.canEdit());
    findViewById(R.id.game_identity_save).setEnabled(canEdit && (guest || s.canSave()));
    visible(R.id.game_identity_retry, s.getStage() == SessionState.Stage.PENDING_FAILURE);
    visible(R.id.game_identity_reselect, s.getUid() != null && s.getStage() != SessionState.Stage.SAVING);
    visible(R.id.game_identity_logout, s.getUid() != null || s.getStage() == SessionState.Stage.AUTH_BUSY);
    boolean configured = s.getStage() != SessionState.Stage.UNCONFIGURED;
    boolean signable = guest && configured;
    for (int id : new int[] {R.id.game_identity_welcome_login, R.id.game_identity_login, R.id.game_main_google}) {
      findViewById(id).setEnabled(signable);
      if (id != R.id.game_identity_welcome_login) visible(id, s.getUid() == null);
    }
    ((TextView) findViewById(R.id.game_identity_status)).setText(status(s));
    ((TextView) findViewById(R.id.game_main_welcome_status)).setText(
        s.getStage() == SessionState.Stage.LOGGED_OUT && s.getFailure() == null ? "" : status(s));
    ((TextView) findViewById(R.id.game_main_profile_scope)).setText(guest
        ? R.string.game_main_guest_note : R.string.game_main_account_note);
    int localStatus = model.localBusy ? R.string.game_main_local_saving
        : model.localFailed ? R.string.game_main_local_failed
        : model.localSaved ? R.string.game_main_local_saved : 0;
    ((TextView) findViewById(R.id.game_main_local_status)).setText(localStatus == 0 ? "" : getString(localStatus));
    int environment = model.runtime.kind() == IdentityRuntime.Kind.FAKE ? R.string.game_identity_fake
        : model.runtime.kind() == IdentityRuntime.Kind.LOCAL_EMULATOR ? R.string.game_identity_emulator : R.string.game_identity_live;
    ((TextView) findViewById(R.id.game_identity_environment)).setText(environment);
    String choiceText = model.choice.getCollege().isEmpty() && model.choice.getDepartment().isEmpty()
        ? getString(R.string.game_main_choice_empty)
        : getString(R.string.game_main_choice, model.choice.getCollege(), model.choice.getDepartment());
    if (model.choice.getGuide() == CampusChoice.Guide.NONE && !model.choice.getDepartment().isEmpty())
      choiceText += "\n" + getString(R.string.game_main_unsupported);
    ((TextView) findViewById(R.id.game_main_choice)).setText(choiceText);
    findViewById(R.id.game_main_open_guide).setEnabled(model.localReady && model.choice.getGuide() != CampusChoice.Guide.NONE);
    findViewById(R.id.game_nav_home).setSelected(tab == HOME);
    findViewById(R.id.game_nav_my).setSelected(tab == MY);
    ViewCompat.setStateDescription(findViewById(R.id.game_nav_home), tab == HOME ? getString(R.string.game_main_home) : null);
    ViewCompat.setStateDescription(findViewById(R.id.game_nav_my), tab == MY ? getString(R.string.game_main_my) : null);
    back.setEnabled(entered);
    WindowCompat.getInsetsController(getWindow(), findViewById(R.id.game_identity_host)).setAppearanceLightStatusBars(false);
    WindowCompat.getInsetsController(getWindow(), findViewById(R.id.game_identity_host)).setAppearanceLightNavigationBars(true);
    binding = false;
    updateProgressLifecycle();
  }

  private void update(EditText field, String text, boolean force) {
    if (force) field.clearFocus();
    if ((force || !field.hasFocus()) && !field.getText().toString().equals(text)) {
      if (field instanceof AutoCompleteTextView) ((AutoCompleteTextView) field).setText(text, false);
      else field.setText(text);
    }
  }

  private String status(SessionState s) {
    int id;
    switch (s.getStage()) {
      case UNCONFIGURED: id = R.string.game_main_unconfigured; break;
      case AUTH_BUSY: id = R.string.game_identity_auth_busy; break;
      case PROFILE_LOADING: id = R.string.game_identity_loading; break;
      case SAVING: id = R.string.game_identity_saving; break;
      case COMPLETE: id = R.string.game_identity_complete; break;
      case PENDING_FAILURE: id = R.string.game_identity_pending; break;
      case FORM: id = R.string.game_main_profile_help; break;
      default: id = R.string.game_main_guest_note;
    }
    String text = getString(id);
    if (s.isCached()) text += "\n" + getString(R.string.game_identity_cached);
    if (s.getFailure() != null) text += "\n" + getString(s.getFailure() == Failure.CANCELED
        ? R.string.game_identity_canceled : R.string.game_identity_failed);
    return text;
  }

  private void updateProgressLifecycle() {
    if (progress == null) return;
    boolean active = resumed && entered && tab == HOME;
    if (active == progressActive) return;
    progressActive = active;
    if (active) { progress.resume(); main.postDelayed(tick, 1000L); }
    else { main.removeCallbacks(tick); progress.pause(); }
  }

  private void renderProgress(AccountHomeState s) {
    int label;
    switch (s.status) {
      case UNAVAILABLE: label = R.string.game_campus_progress_unavailable; break;
      case NO_ACCOUNT: label = R.string.game_campus_progress_no_account; break;
      case UNSTARTED: label = R.string.game_campus_progress_unstarted; break;
      case WAITING: label = R.string.game_campus_progress_waiting; break;
      case READY: label = R.string.game_campus_progress_ready; break;
      case COMPLETE: label = R.string.game_campus_progress_complete; break;
      case FAILED: label = R.string.game_campus_progress_failed; break;
      default: label = R.string.game_campus_progress_loading;
    }
    String text;
    if (s.status == AccountHomeState.Status.WAITING) {
      long seconds = (s.remainingMillis + 999) / 1000;
      text = getString(R.string.game_campus_progress_waiting, seconds / 60, seconds % 60);
    } else text = getString(label);
    if (s.server != AccountHomeState.Server.NONE) text += "\n" + getString(
        s.server == AccountHomeState.Server.COMPLETE ? R.string.game_campus_progress_remote_complete : R.string.game_campus_progress_remote_intro);
    ((TextView) findViewById(R.id.game_main_progress)).setText(text);
    findViewById(R.id.game_main_authored).setEnabled(s.status != AccountHomeState.Status.LOADING
        && s.status != AccountHomeState.Status.NO_ACCOUNT && s.status != AccountHomeState.Status.FAILED);
    visible(R.id.game_main_progress_retry, s.status == AccountHomeState.Status.NO_ACCOUNT || s.status == AccountHomeState.Status.FAILED);
  }
}
