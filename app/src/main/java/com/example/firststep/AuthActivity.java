package com.example.firststep;

import android.content.Intent;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.text.InputType;
import android.view.WindowManager;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.credentials.*;
import androidx.credentials.exceptions.GetCredentialException;
import com.example.firststep.auth.AuthSession;
import com.example.firststep.auth.CloudApi;
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;

/** Optional server account access. Guest exploration remains available without authentication. */
public final class AuthActivity extends AppCompatActivity {
  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private final CancellationSignal credentialCancellation = new CancellationSignal();
  private LinearLayout body;
  private TextView status, configStatus;
  private EditText username, password, nickname;
  private Button login, register, google, retry;
  private boolean busy, developmentEnabled, googleEnabled;
  private String googleClientId = "";

  @Override protected void onCreate(Bundle state) {
    super.onCreate(state);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    setContentView(R.layout.activity_mission);
    Ui.applyInsets(findViewById(R.id.mission_scroll));
    body = findViewById(R.id.mission_body);
    TextView back = Ui.text(this, "← 계정 없이 돌아가기", 14, Ui.MUTED, true);
    back.setOnClickListener(v -> finish());body.addView(back);Ui.gap(body, 24);
    body.addView(Ui.label(this, "ACCOUNT"));Ui.gap(body, 10);
    body.addView(Ui.text(this, "기록을 계정에 연결해요", 28, Ui.INK, true));Ui.gap(body, 10);
    body.addView(Ui.text(this, "계정 없이도 이 기기에서 계속 탐방할 수 있어요. 로그인하면 완료 기록과 사진을 계정별로 서버와 동기화합니다. 기존 기기 기록은 직접 가져오기를 선택해야 복사됩니다.", 15, Ui.MUTED, false));Ui.gap(body, 22);
    LinearLayout development = Ui.card(this);
    development.addView(Ui.label(this, "개발용 계정"));Ui.gap(development, 8);
    development.addView(Ui.text(this, "연결한 개발 서버에서 사용하는 별도 계정입니다. Google 또는 학교 인증 계정이 아닙니다. 다른 서비스에서 사용하는 비밀번호를 재사용하지 마세요.", 14, Ui.MUTED, false));
    username = field("개발용 아이디", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD, 64);
    password = field("비밀번호", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD, 128);
    nickname = field("닉네임 · 새 계정 등록 시 사용", InputType.TYPE_CLASS_TEXT, 40);
    development.addView(Ui.text(this, "새 아이디는 영문·숫자·밑줄·마침표·하이픈 3~64자, 비밀번호는 10~128자입니다. 아이디는 대소문자를 구분하지 않습니다.", 12, Ui.MUTED, false));
    development.addView(username);development.addView(password);development.addView(nickname);Ui.gap(development, 12);
    login = Ui.button(this, "개발용 계정 로그인", true);development.addView(login);Ui.gap(development, 8);
    register = Ui.button(this, "개발용 계정 등록", false);development.addView(register);
    login.setOnClickListener(v -> development(false));register.setOnClickListener(v -> development(true));
    body.addView(development);Ui.gap(body, 22);
    google = Ui.button(this, "Google로 로그인", false);body.addView(google);
    google.setOnClickListener(v -> googleLogin());Ui.gap(body, 8);
    body.addView(Ui.text(this, "Google 로그인은 Google 프로젝트와 서버의 실제 설정이 있어야 사용할 수 있습니다. 학교 인증은 현재 제공하지 않습니다.", 13, Ui.MUTED, false));Ui.gap(body, 16);
    configStatus = Ui.text(this, "서버의 로그인 설정을 확인하고 있어요…", 14, Ui.MUTED, false);body.addView(configStatus);
    status = Ui.text(this, "", 14, Ui.RUST, false);body.addView(status);Ui.gap(body, 10);
    retry = Ui.button(this, "서버 연결 다시 확인", false);body.addView(retry);retry.setOnClickListener(v -> configuration());
    updateButtons();configuration();
  }

  private EditText field(String hint, int type, int max) {
    EditText field = new EditText(this);field.setHint(hint);field.setInputType(type);field.setSingleLine(true);
    field.setSaveEnabled(false);if(android.os.Build.VERSION.SDK_INT>=26)field.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
    field.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(max)});
    return field;
  }
  private boolean alive() { return !isFinishing() && !isDestroyed(); }
  private void updateButtons() {
    login.setEnabled(!busy && developmentEnabled);register.setEnabled(!busy && developmentEnabled);
    google.setEnabled(!busy && googleEnabled);if(retry!=null)retry.setEnabled(!busy);
    username.setEnabled(!busy);password.setEnabled(!busy);nickname.setEnabled(!busy);
  }
  private void setBusy(boolean value, String message) { busy = value;status.setText(message);updateButtons(); }
  private void failure(Exception error) {
    if (!alive()) return;
    String message = error instanceof CloudApi.ApiException ? error.getMessage() : "로그인을 완료하지 못했어요. 입력과 기기 설정을 확인하고 다시 시도해 주세요.";
    setBusy(false, message);
  }
  private void configuration() {
    if (busy) return;setBusy(true, "");
    worker.execute(() -> {
      try {
        JSONObject config = new CloudApi(this, "").request("GET", "/v1/config", null);
        runOnUiThread(() -> {
          if (!alive()) return;
          developmentEnabled = config.optBoolean("development_login_enabled", false);
          googleClientId = config.optString("google_client_id", "");
          if (googleClientId.isEmpty()) googleClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID;
          googleEnabled = config.optBoolean("google_enabled", false) && !googleClientId.trim().isEmpty();
          configStatus.setText((developmentEnabled ? "개발용 계정 서버 연결을 확인했어요." : "서버에서 개발용 계정 로그인을 허용하지 않습니다.")
              + (googleEnabled ? "\nGoogle 로그인을 사용할 수 있어요." : "\nGoogle 로그인은 실제 프로젝트 설정 전이라 사용할 수 없어요."));
          setBusy(false, "");
        });
      } catch (Exception error) { runOnUiThread(() -> { if(alive()){developmentEnabled=false;googleEnabled=false;configStatus.setText("서버 설정을 불러오지 못했어요. 계정 없이 탐방을 계속할 수 있습니다.");failure(error);} }); }
    });
  }

  private void development(boolean create) {
    if (busy || !developmentEnabled) return;
    String name = username.getText().toString().trim(), secret = password.getText().toString(), nick = nickname.getText().toString().trim();
    if (name.isEmpty()) {username.setError("개발용 아이디를 입력해 주세요.");return;}
    if (secret.isEmpty()) {password.setError("비밀번호를 입력해 주세요.");return;}
    if (create && nick.isEmpty()) {nickname.setError("닉네임을 입력해 주세요.");return;}
    if (create && !name.matches("[A-Za-z0-9_.-]{3,64}")) {username.setError("아이디는 영문·숫자·밑줄·마침표·하이픈 3~64자로 입력해 주세요.");return;}
    if (create && secret.length()<10) {password.setError("비밀번호는 10자 이상 입력해 주세요.");return;}
    setBusy(true, create ? "개발용 계정을 등록하고 있어요…" : "로그인하고 있어요…");
    password.setText("");
    worker.execute(() -> {
      try {
        JSONObject body = new JSONObject().put("username", name).put("password", secret);
        if (create) body.put("nickname", nick);
        JSONObject response = new CloudApi(this, "").request("POST", "/v1/auth/development/" + (create ? "register" : "login"), body);
        finishAuthentication(response);
      } catch (Exception error) {runOnUiThread(() -> failure(error));}
    });
  }

  private void finishAuthentication(JSONObject response) throws Exception {
    // Commit and finish in one UI event: a canceled/destroyed screen cannot install a late session.
    runOnUiThread(() -> {
      if(!alive())return;
      try {new AuthSession(this).save(response);setResult(RESULT_OK,new Intent());finish();}
      catch(Exception error){failure(error);}
    });
  }

  private void googleLogin() {
    if (busy || !googleEnabled) return;setBusy(true, "Google 로그인 요청을 준비하고 있어요…");
    worker.execute(() -> {
      try {
        JSONObject challenge = new CloudApi(this, "").request("POST", "/v1/auth/google/challenge", new JSONObject());
        String id = challenge.getString("challenge_id"), nonce = challenge.getString("nonce");
        runOnUiThread(() -> requestGoogleCredential(id, nonce));
      } catch (Exception error) { runOnUiThread(() -> failure(error)); }
    });
  }
  private void requestGoogleCredential(String challengeId, String nonce) {
    if (!alive()) return;
    try {
      GetSignInWithGoogleOption option = new GetSignInWithGoogleOption.Builder(googleClientId).setNonce(nonce).build();
      GetCredentialRequest request = new GetCredentialRequest.Builder().addCredentialOption(option).build();
      CredentialManager.create(this).getCredentialAsync(this, request, credentialCancellation,
          ContextCompat.getMainExecutor(this), new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
            @Override public void onResult(GetCredentialResponse result) {
              if (!alive()) return;
              try {
                Credential credential = result.getCredential();
                if (!(credential instanceof CustomCredential) || !GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(credential.getType()))
                  throw new IllegalStateException("Unexpected credential type");
                String idToken = GoogleIdTokenCredential.createFrom(credential.getData()).getIdToken();
                worker.execute(() -> {
                  try {
                    JSONObject response = new CloudApi(AuthActivity.this, "").request("POST", "/v1/auth/google",
                        new JSONObject().put("id_token", idToken).put("challenge_id", challengeId));
                    finishAuthentication(response);
                  } catch (Exception error) {runOnUiThread(() -> failure(error));}
                });
              } catch (Exception error) {failure(error);}
            }
            @Override public void onError(GetCredentialException error) {
              if (alive()) setBusy(false, "Google 로그인이 취소되었거나 사용할 수 없어요. 기기의 Google 계정과 서비스 설정을 확인해 주세요.");
            }
          });
    } catch (Exception error) { failure(error); }
  }
  @Override protected void onDestroy() {
    credentialCancellation.cancel();worker.shutdownNow();if(password!=null)password.setText("");super.onDestroy();
  }
}
