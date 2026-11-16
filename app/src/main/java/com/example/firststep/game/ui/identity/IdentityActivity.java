package com.example.firststep.game.ui.identity;

import android.content.Intent;
import android.os.Bundle;
import android.text.*;
import android.view.View;
import android.widget.*;
import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import com.example.firststep.BuildConfig;
import com.example.firststep.R;
import com.example.firststep.game.auth.*;
import com.example.firststep.game.composition.home.CampusHomeRoute;
import com.example.firststep.game.composition.identity.*;
import com.example.firststep.game.ui.guide.GuideActivity;
import java.util.Objects;

/** Welcome and account shell. Guest entry opens the standalone campus guide. */
public final class IdentityActivity extends AppCompatActivity {
  private static final String ACCOUNT_ENTRY_OPEN = "identity_account_entry_open";
  private IdentityViewModel model;
  private EditText name, student, department;
  private boolean binding, viewingProfile;
  private boolean choosingAccount, loginInfoExpanded;
  private boolean openingCampusHome;
  private String displayedUid;
  private final OnBackPressedCallback accountBack =
      new OnBackPressedCallback(false) {
        @Override
        public void handleOnBackPressed() {
          closeAccountScreen();
        }
      };

  public SessionState stateForTest() {
    return model.controller.state();
  }

  @Override
  protected void onCreate(Bundle state) {
    super.onCreate(state);
    EdgeToEdge.enable(this);
    setContentView(R.layout.game_identity_activity);
    choosingAccount = state != null && state.getBoolean(ACCOUNT_ENTRY_OPEN);
    loginInfoExpanded = state != null && state.getBoolean("login_info_expanded");
    getOnBackPressedDispatcher().addCallback(this, accountBack);
    model =
        new ViewModelProvider(
                this,
                new ViewModelProvider.Factory() {
                  @Override
                  public <T extends ViewModel> T create(Class<T> type) {
                    return type.cast(
                        new IdentityViewModel(H07VariantFactory.create(IdentityActivity.this)));
                  }
                })
            .get(IdentityViewModel.class);
    name = findViewById(R.id.game_identity_name);
    student = findViewById(R.id.game_identity_student);
    department = findViewById(R.id.game_identity_department);
    androidx.core.view.OnApplyWindowInsetsListener insetContent =
        (view, insets) -> {
          androidx.core.graphics.Insets bars =
              insets.getInsets(
                  androidx.core.view.WindowInsetsCompat.Type.systemBars()
                      | androidx.core.view.WindowInsetsCompat.Type.displayCutout());
          view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
          return insets;
        };
    androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(
        findViewById(R.id.game_identity_root), insetContent);
    androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(
        findViewById(R.id.game_identity_welcome_scroll), insetContent);
    androidx.core.view.ViewCompat.setAccessibilityHeading(
        findViewById(R.id.game_identity_welcome_title), true);
    TextWatcher watcher =
        new TextWatcher() {
          public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

          public void onTextChanged(CharSequence s, int start, int before, int count) {
            if (!binding)
              model.controller.edit(
                  name.getText().toString(),
                  student.getText().toString(),
                  department.getText().toString());
          }

          public void afterTextChanged(Editable e) {}
        };
    name.addTextChangedListener(watcher);
    student.addTextChangedListener(watcher);
    department.addTextChangedListener(watcher);
    findViewById(R.id.game_identity_welcome_login).setOnClickListener(v -> {
      choosingAccount = true;
      render(model.controller.state());
    });
    findViewById(R.id.game_identity_welcome_guest).setOnClickListener(v -> {
      if (isSignedOut(model.controller.state())) {
        startActivity(new Intent(this, GuideActivity.class));
      }
    });
    TextView guestEntry = findViewById(R.id.game_identity_welcome_guest);
    String guestLabel = getString(R.string.game_welcome_guest);
    String guestNote = getString(BuildConfig.DEBUG
        ? R.string.game_welcome_guest_debug_note : R.string.game_welcome_guest_note);
    SpannableString guestText = new SpannableString(guestLabel + "\n" + guestNote);
    int noteStart = guestLabel.length() + 1;
    guestText.setSpan(new android.text.style.RelativeSizeSpan(0.8f), noteStart, guestText.length(),
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    guestText.setSpan(new android.text.style.ForegroundColorSpan(getColor(R.color.game_reskin_muted)),
        noteStart, guestText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    guestEntry.setText(guestText);
    findViewById(R.id.game_identity_login_back).setOnClickListener(v -> closeAccountScreen());
    findViewById(R.id.game_identity_info_toggle).setOnClickListener(v -> {
      loginInfoExpanded = !loginInfoExpanded;
      renderLoginInfo(true);
    });
    findViewById(R.id.game_identity_login).setOnClickListener(v -> model.controller.signIn(false));
    findViewById(R.id.game_identity_reselect)
        .setOnClickListener(v -> model.controller.signIn(true));
    findViewById(R.id.game_identity_logout).setOnClickListener(v -> model.controller.logout());
    findViewById(R.id.game_identity_save).setOnClickListener(v -> model.controller.save());
    findViewById(R.id.game_identity_retry).setOnClickListener(v -> model.controller.retry());
    findViewById(R.id.game_identity_open_campus).setOnClickListener(v -> openCampusHome());
    visible(R.id.game_identity_campus_demo_note, BuildConfig.DEBUG);
    findViewById(R.id.game_identity_open_profile)
        .setOnClickListener(v -> {
          viewingProfile = true;
          render(model.controller.state());
        });
    findViewById(R.id.game_identity_back)
        .setOnClickListener(v -> closeAccountScreen());
    findViewById(R.id.game_identity_host).setVisibility(View.INVISIBLE);
  }

  @Override
  protected void onResume() {
    super.onResume();
    openingCampusHome = false;
    model.runtime.attach(this);
    model.controller.resume();
    model.controller.setListener(this::render);
    render(model.controller.state());
    findViewById(R.id.game_identity_host).setVisibility(View.VISIBLE);
  }

  @Override
  protected void onPause() {
    findViewById(R.id.game_identity_host).setVisibility(View.INVISIBLE);
    model.controller.pause();
    model.controller.setListener(null);
    super.onPause();
  }

  @Override
  protected void onDestroy() {
    model.runtime.detach(this);
    super.onDestroy();
  }

  @Override
  protected void onSaveInstanceState(Bundle state) {
    state.putBoolean(ACCOUNT_ENTRY_OPEN, choosingAccount);
    super.onSaveInstanceState(state);
  }

  private static boolean isSignedOut(SessionState state) {
    return state.getUid() == null
        && (state.getStage() == SessionState.Stage.LOGGED_OUT
            || state.getStage() == SessionState.Stage.UNCONFIGURED);
  }

  private void closeAccountScreen() {
    if (viewingProfile) {
      showHome();
    } else if (choosingAccount && isSignedOut(model.controller.state())) {
      choosingAccount = false;
      render(model.controller.state());
    } else {
      finish();
    }
  }

  private void visible(int id, boolean show) {
    findViewById(id).setVisibility(show ? View.VISIBLE : View.GONE);
  }

  private void showHome() {
    viewingProfile = false;
    render(model.controller.state());
  }

  private void openCampusHome() {
    if (openingCampusHome || model.controller.state().getStage() != SessionState.Stage.COMPLETE) return;
    openingCampusHome = true;
    startActivity(CampusHomeRoute.intent(this));
  }

  private void renderLoginInfo(boolean loginScreen) {
    visible(R.id.game_identity_info_toggle, loginScreen);
    visible(R.id.game_identity_info_detail, !loginScreen || loginInfoExpanded);
    TextView toggle = findViewById(R.id.game_identity_info_toggle);
    toggle.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0,
        loginInfoExpanded ? R.drawable.game_guide_icon_up : R.drawable.game_guide_icon_down, 0);
    androidx.core.view.ViewCompat.setStateDescription(toggle, getString(loginInfoExpanded
        ? R.string.game_guide_expanded : R.string.game_guide_collapsed));
  }

  private void renderLoginStatus(SessionState state, boolean loginScreen) {
    boolean unconfigured = state.getStage() == SessionState.Stage.UNCONFIGURED;
    View panel = findViewById(R.id.game_identity_status_panel);
    panel.setBackgroundResource(unconfigured ? R.drawable.game_reskin_tinted_card : 0);
    int padding = unconfigured
        ? getResources().getDimensionPixelSize(R.dimen.game_space_medium) : 0;
    panel.setPadding(padding, padding, padding, padding);
    visible(R.id.game_identity_status_heading, unconfigured);
    visible(R.id.game_identity_status_panel, !loginScreen || unconfigured || state.getFailure() != null
        || model.runtime.kind() == IdentityRuntime.Kind.FAKE || model.runtime.kind() == IdentityRuntime.Kind.LOCAL_EMULATOR);
  }

  private void render(SessionState s) {
    binding = true;
    boolean changed = !Objects.equals(displayedUid, s.getUid());
    displayedUid = s.getUid();
    SessionState.Stage stage = s.getStage();
    Profile p = s.getDraft();
    boolean form =
        s.getUid() != null
            && p != null
            && (stage == SessionState.Stage.FORM
                || stage == SessionState.Stage.SAVING
                || stage == SessionState.Stage.PENDING_FAILURE);
    boolean editing = s.canEdit();
    boolean complete = stage == SessionState.Stage.COMPLETE;
    if (changed || !complete) viewingProfile = false;
    boolean showWelcome = isSignedOut(s) && !choosingAccount;
    boolean loginScreen = !showWelcome && isSignedOut(s);
    visible(R.id.game_identity_login_header, loginScreen);
    visible(R.id.game_identity_login_spacer, loginScreen);
    renderLoginInfo(loginScreen);
    renderLoginStatus(s, loginScreen);
    visible(R.id.game_identity_welcome, showWelcome);
    visible(R.id.game_identity_root, !showWelcome);
    WindowInsetsControllerCompat bars =
        WindowCompat.getInsetsController(getWindow(), findViewById(R.id.game_identity_host));
    bars.setAppearanceLightStatusBars(!showWelcome);
    bars.setAppearanceLightNavigationBars(!showWelcome);
    View home = findViewById(R.id.game_identity_home);
    boolean showHome = complete && !viewingProfile;
    boolean enteringHome = showHome && home.getVisibility() != View.VISIBLE;
    update(name, form ? p.getName() : "", changed || !form);
    update(student, form ? p.getStudentNumber() : "", changed || !form);
    update(department, form ? p.getDepartment() : "", changed || !form);
    name.setEnabled(editing);
    student.setEnabled(editing);
    department.setEnabled(editing);
    visible(R.id.game_identity_form, form);
    visible(R.id.game_identity_entry, !complete);
    visible(R.id.game_identity_home, showHome);
    visible(R.id.game_identity_profile_panel, viewingProfile);
    accountBack.setEnabled(viewingProfile || (choosingAccount && isSignedOut(s)));
    ((Button) findViewById(R.id.game_identity_back))
        .setText(viewingProfile ? R.string.game_identity_home_back : R.string.game_identity_back);
    visible(
        R.id.game_identity_login,
        stage == SessionState.Stage.LOGGED_OUT || stage == SessionState.Stage.UNCONFIGURED);
    findViewById(R.id.game_identity_login).setEnabled(stage == SessionState.Stage.LOGGED_OUT);
    visible(R.id.game_identity_logout, s.getUid() != null || stage == SessionState.Stage.AUTH_BUSY);
    visible(
        R.id.game_identity_reselect,
        s.getUid() != null
            && stage != SessionState.Stage.SAVING
            && stage != SessionState.Stage.AUTH_BUSY);
    visible(R.id.game_identity_retry, stage == SessionState.Stage.PENDING_FAILURE);
    visible(R.id.game_identity_save, editing);
    findViewById(R.id.game_identity_save).setEnabled(s.canSave());
    int label;
    switch (stage) {
      case UNCONFIGURED:
        label = R.string.game_identity_unconfigured;
        break;
      case LOGGED_OUT:
        label = R.string.game_identity_logged_out;
        break;
      case AUTH_BUSY:
        label = R.string.game_identity_auth_busy;
        break;
      case PROFILE_LOADING:
        label = R.string.game_identity_loading;
        break;
      case SAVING:
        label = R.string.game_identity_saving;
        break;
      case COMPLETE:
        label = R.string.game_identity_complete;
        break;
      case PENDING_FAILURE:
        label = R.string.game_identity_pending;
        break;
      default:
        label = R.string.game_identity_form_help;
    }
    String text = getString(label);
    if (s.isCached()) text += "\n" + getString(R.string.game_identity_cached);
    if (s.getFailure() != null) {
      int error =
          s.getFailure() == Failure.CANCELED
              ? R.string.game_identity_canceled
              : s.getFailure() == Failure.PERMISSION_DENIED
                  ? R.string.game_identity_denied
                  : s.getFailure() == Failure.UNAVAILABLE
                      ? R.string.game_identity_unavailable
                      : s.getFailure() == Failure.NO_CREDENTIAL
                          ? R.string.game_identity_no_credential
                          : R.string.game_identity_failed;
      text += "\n" + getString(error);
    }
    ((TextView) findViewById(R.id.game_identity_status)).setText(text);
    int kind =
        model.runtime.kind() == IdentityRuntime.Kind.FAKE
            ? R.string.game_identity_fake
            : model.runtime.kind() == IdentityRuntime.Kind.LOCAL_EMULATOR
                ? R.string.game_identity_emulator
                : R.string.game_identity_live;
    ((TextView) findViewById(R.id.game_identity_environment)).setText(kind);
    ((TextView) findViewById(R.id.game_identity_landing))
        .setText(
            viewingProfile
                ? getString(
                    R.string.game_identity_profile,
                    p.getName(),
                    p.getStudentNumber(),
                    p.getDepartment())
                : "");
    if (enteringHome) {
      WindowCompat.getInsetsController(getWindow(), home).hide(WindowInsetsCompat.Type.ime());
      ScrollView root = findViewById(R.id.game_identity_root);
      root.post(
          () -> {
            if (home.isShown()) root.scrollTo(0, 0);
          });
    }
    binding = false;
  }

  private void update(EditText field, String text, boolean changed) {
    if (changed) field.clearFocus();
    if ((changed || !field.hasFocus()) && !field.getText().toString().equals(text))
      field.setText(text);
  }
}
