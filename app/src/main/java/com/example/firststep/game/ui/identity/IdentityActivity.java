package com.example.firststep.game.ui.identity;

import android.os.Bundle;
import android.text.*;
import android.view.View;
import android.widget.*;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import com.example.firststep.BuildConfig;
import com.example.firststep.R;
import com.example.firststep.game.auth.*;
import com.example.firststep.game.composition.home.CampusHomeRoute;
import com.example.firststep.game.composition.identity.*;
import java.util.Objects;

/** Replaceable account-only shell. Never reads or presents legacy/fixture exploration progress. */
public final class IdentityActivity extends AppCompatActivity {
  private IdentityViewModel model;
  private EditText name, student, department;
  private boolean binding, viewingProfile;
  private boolean openingCampusHome;
  private String displayedUid;
  private final OnBackPressedCallback profileBack =
      new OnBackPressedCallback(false) {
        @Override
        public void handleOnBackPressed() {
          showHome();
        }
      };

  public SessionState stateForTest() {
    return model.controller.state();
  }

  @Override
  protected void onCreate(Bundle state) {
    super.onCreate(state);
    setContentView(R.layout.game_identity_activity);
    getOnBackPressedDispatcher().addCallback(this, profileBack);
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
    androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(
        findViewById(R.id.game_identity_root),
        (view, insets) -> {
          androidx.core.graphics.Insets bars =
              insets.getInsets(
                  androidx.core.view.WindowInsetsCompat.Type.systemBars()
                      | androidx.core.view.WindowInsetsCompat.Type.displayCutout());
          view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
          return insets;
        });
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
        .setOnClickListener(v -> {
          if (viewingProfile) showHome();
          else finish();
        });
    findViewById(R.id.game_identity_root).setVisibility(View.INVISIBLE);
  }

  @Override
  protected void onResume() {
    super.onResume();
    openingCampusHome = false;
    model.runtime.attach(this);
    model.controller.resume();
    model.controller.setListener(this::render);
    render(model.controller.state());
    findViewById(R.id.game_identity_root).setVisibility(View.VISIBLE);
  }

  @Override
  protected void onPause() {
    findViewById(R.id.game_identity_root).setVisibility(View.INVISIBLE);
    model.controller.pause();
    model.controller.setListener(null);
    super.onPause();
  }

  @Override
  protected void onDestroy() {
    model.runtime.detach(this);
    super.onDestroy();
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
    profileBack.setEnabled(viewingProfile);
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
