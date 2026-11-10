package com.example.firststep;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.example.firststep.data.*;
import java.util.*;
import com.example.firststep.auth.AuthSession;
import com.example.firststep.auth.CloudApi;

public class MainActivity extends AppCompatActivity {
  private MissionRepository repo;
  private AppMode mode;
  private List<Mission> missions;
  private List<MissionCompletion> completions = new ArrayList<>();
  private LinearLayout page, nav;
  private String tab = "home";
  private String accountNamespace;
  private boolean accountBusy;

  @Override
  protected void onCreate(Bundle b) {
    super.onCreate(b);
    repo = MissionRepository.getInstance(this);
    accountNamespace = AuthSession.namespace(this);
    AuthSession session = new AuthSession(this);
    if (!"guest".equals(session.currentUserId()) && repo.getNickname().isEmpty()) repo.setNickname(session.userNickname());
    mode = repo.getMode();
    missions = repo.getMissions();
    if(b!=null)tab=b.getString("tab","home");
    if (repo.getNickname().isEmpty()) onboarding();
    else refresh();
  }

  private void refresh() {
    AppMode requested = mode;
    MissionRepository requestedRepository = repo;
    String requestedAccount = accountNamespace;
    requestedRepository.getCompletions(
        requested,
        list -> {
          if (requested != mode || requestedRepository != repo || !Objects.equals(requestedAccount, AuthSession.namespace(this)) || isDestroyed() || isFinishing()) return;
          completions = list;
          render();
        });
  }

  private boolean done(String id) {
    for (MissionCompletion c : completions) if (c.missionId.equals(id)) return true;
    return false;
  }

  @Override protected void onResume(){super.onResume();if(repo!=null){if(!Objects.equals(accountNamespace,AuthSession.namespace(this)))selectCurrentAccount();else if(!repo.getNickname().isEmpty())refresh();}}
  @Override protected void onSaveInstanceState(Bundle state){state.putString("tab",tab);super.onSaveInstanceState(state);}

  private void shell() {
    setContentView(R.layout.activity_main);
    LinearLayout root = findViewById(R.id.shell);
    Ui.applyInsets(root);
    page = findViewById(R.id.page);
    nav = findViewById(R.id.navigation);
    String[] labels = {"홈", "지도", "마이페이지"};
    String[] tabs = {"home", "map", "profile"};
    for (int i = 0; i < 3; i++) {
      final String target = tabs[i];
      TextView t = Ui.text(this, labels[i], 14, target.equals(tab) ? Ui.RUST : Ui.MUTED, true);
      t.setGravity(android.view.Gravity.CENTER);
            // FIRSTSTEP_NAV_STYLE_V1
      t.setTextSize(12);
      t.setLineSpacing(Ui.dp(this, 3), 1.0f);
      t.setTextColor(target.equals(tab) ? 0xFFAD5277 : 0xFF85818A);
      t.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));

      android.graphics.drawable.GradientDrawable navBackground =
          new android.graphics.drawable.GradientDrawable();

      navBackground.setColor(
          target.equals(tab) ? 0xFFFFE2EC : 0xFFFFFFFF
      );
      navBackground.setCornerRadius(Ui.dp(this, 14));

      t.setBackground(navBackground);

      LinearLayout.LayoutParams navParams =
          new LinearLayout.LayoutParams(0, Ui.dp(this, 52), 1);

      navParams.setMargins(
          Ui.dp(this, 3), Ui.dp(this, 3),
          Ui.dp(this, 3), Ui.dp(this, 3)
      );

      nav.addView(t, navParams);
      t.setOnClickListener(v -> {
        if ("map".equals(target)) {
          startActivity(new Intent(this, RouteMapActivity.class));
          return;
        }
        tab = target;
        render();
      });
    }
  }

  private void render() {
    shell();
    if (tab.equals("catalog")) catalog();
    else if (tab.equals("profile")) profile();
    else home();
  }

  private void onboarding() {
    shell();
    nav.setVisibility(android.view.View.GONE);
    page.addView(Ui.label(this, "WELCOME TO CAMPUS"));
    Ui.gap(page, 30);
    page.addView(Ui.text(this, "낯선 캠퍼스에\n첫발자국을 남겨요", 32, Ui.INK, true));
    Ui.gap(page, 16);
    page.addView(Ui.text(this, "다섯 장소에 담긴 기억을 찾으며 우리 학교와 조금 더 친해지는 시간.", 16, Ui.MUTED, false));
    Ui.gap(page, 32);
    LinearLayout card = Ui.card(this);
    card.addView(Ui.label(this, "YOUR NAME"));
    Ui.gap(card, 10);
    card.addView(Ui.text(this, "어떻게 불러 드릴까요?", 20, Ui.INK, true));
    EditText name = new EditText(this);
    name.setSingleLine(true);
    name.setHint("닉네임 입력");
    name.setMaxLines(1);
    card.addView(name);
    page.addView(card);
    Ui.gap(page, 24);
    Button start = Ui.button(this, "탐방 시작하기", true);
    page.addView(start);
    start.setOnClickListener(v -> {
      String n = name.getText().toString().trim();
      if (n.isEmpty()) {
        name.setError("닉네임을 입력해 주세요");
        return;
      }
      repo.setNickname(n);
      tab = "home";
      refresh();
    });
    Ui.gap(page, 12);
    Button accountLogin = Ui.button(this, "계정으로 로그인", false);
    page.addView(accountLogin);accountLogin.setOnClickListener(v -> openAuthentication());
  }

  private void header(String small, String title, String subtitle) {
    page.addView(Ui.label(this, small));
    Ui.gap(page, 10);
    page.addView(Ui.text(this, title, 30, Ui.INK, true));
    Ui.gap(page, 10);
    page.addView(Ui.text(this, subtitle, 15, Ui.MUTED, false));
    Ui.gap(page, 22);
  }

  private void home() {
    header(
        "FIRST STEP  /  CAMPUS JOURNAL",
        repo.getNickname() + "님, 안녕하세요",
        "오늘은 캠퍼스의 어떤 기억을 만나 볼까요?");

    LinearLayout modeCard = Ui.card(this);
    modeCard.addView(Ui.label(this, mode == AppMode.DEMO ? "DEMO MODE" : "FIELD MODE"));
    Ui.gap(modeCard, 7);
    modeCard.addView(Ui.text(this, mode == AppMode.DEMO ? "테스트 탐방 중" : "현장 탐방", 20, Ui.INK, true));
    Ui.gap(modeCard, 6);
    modeCard.addView(
        Ui.text(
            this,
            mode == AppMode.DEMO
                ? "위치 확인을 건너뛰며, 기록은 현장 기록과 따로 저장됩니다."
                : "현장 확인이 끝난 지점에서만 위치를 확인하고 시작할 수 있어요.",
            14,
            Ui.MUTED,
            false));
    Ui.gap(modeCard, 14);
    Button switcher = Ui.button(this, mode == AppMode.DEMO ? "현장 모드로 전환" : "테스트 모드로 전환", false);
    modeCard.addView(switcher);
    switcher.setOnClickListener(v -> {
      mode = mode == AppMode.DEMO ? AppMode.FIELD : AppMode.DEMO;
      repo.setMode(mode);
      refresh();
    });
    page.addView(modeCard);
    Ui.gap(page, 12);
    Button guideButton = Ui.button(this, "학교 시설 안내", false);
    page.addView(guideButton);
    guideButton.setOnClickListener(v -> {
      tab = "catalog";
      render();
    });
    Ui.gap(page, 12);

        // FIRSTSTEP_HOME_CARD_V1
    LinearLayout exploreCard = Ui.card(this);

    exploreCard.addView(
        Ui.text(this, "CAMPUS ADVENTURE", 12, 0xFFAD5277, true)
    );

    Ui.gap(exploreCard, 12);

    exploreCard.addView(
        Ui.text(this, "오늘의 캠퍼스 탐험", 24, Ui.INK, true)
    );

    Ui.gap(exploreCard, 10);

    exploreCard.addView(
        Ui.text(this,
            "지도에서 건물을 선택하고 숨겨진 캐릭터와 미션을 만나보세요.",
            15, Ui.MUTED, false)
    );

    Ui.gap(exploreCard, 18);

    Button exploreStart = Ui.button(
        this, "지도에서 탐험 시작하기", true
    );

    exploreCard.addView(exploreStart);

    exploreStart.setOnClickListener(v ->
        startActivity(new Intent(this, RouteMapActivity.class))
    );

    page.addView(exploreCard);

    // FIRSTSTEP_FACILITY_LINK_V1
    Ui.gap(page, 12);

    Button facilityButton = Ui.button(
        this,
        "학교 시설 찾아보기",
        false
    );

    page.addView(facilityButton);

    facilityButton.setOnClickListener(v ->
        startActivity(new Intent(
            this,
            FacilityDirectoryActivity.class
        ))
    );

    Ui.gap(page, 26);
    page.addView(Ui.label(this, "YOUR ROUTE"));
    Ui.gap(page, 8);
    page.addView(Ui.text(this, "다섯 개의 기억", 24, Ui.INK, true));
    Ui.gap(page, 4);
    page.addView(
        Ui.text(this, completions.size() + " / 5 완료  ·  한 장소씩 천천히 둘러보세요", 14, Ui.MUTED, false));
    Ui.gap(page, 16);
    for (int i = 0; i < missions.size(); i++) {
      final int position = i;
      Mission m = missions.get(i);
      boolean completed = done(m.id);
      boolean unlocked = i == 0 || done(missions.get(i - 1).id);
      LinearLayout card = Ui.card(this);
      card.addView(
          Ui.label(
              this,
              String.format(
                  Locale.KOREA,
                  "%02d  /  %s",
                  i + 1,
                  completed ? "완료" : unlocked ? "탐방 가능" : "잠김")));
      Ui.gap(card, 8);
      card.addView(Ui.text(this, m.place, 19, Ui.INK, true));
      Ui.gap(card, 5);
      card.addView(Ui.text(this, m.title, 14, Ui.MUTED, false));
      Ui.gap(card, 12);
      Button go =
          Ui.button(
              this,
              completed ? "다시 보기" : unlocked ? "기억 찾기" : "이전 기억을 먼저 찾아 주세요",
              unlocked && !completed);
      card.addView(go);
      go.setEnabled(unlocked);
      go.setOnClickListener(v -> {
        if (completed) {
          tab = "catalog";
          render();
          return;
        }
        if (mode == AppMode.FIELD && !m.coordinateVerified) {
          new AlertDialog.Builder(this)
              .setTitle("현장 좌표 확인 전")
              .setMessage("이 장소의 좌표와 접근 경로는 아직 현장 검증되지 않았습니다. 테스트 모드에서 미리 체험할 수 있어요.")
              .setPositiveButton("확인", null)
              .show();
          return;
        }
        Intent intent = new Intent(this, MissionActivity.class);
        intent.putExtra("missionIndex", position);
        intent.putExtra("demo", mode == AppMode.DEMO);
        startActivityForResult(intent, 100);
      });
      page.addView(card);
      Ui.gap(page, 12);
    }
    if (completions.size() == missions.size()) {
      LinearLayout c = Ui.card(this);
      c.addView(Ui.text(this, "다섯 기억을 모두 모았어요 ✦", 22, Ui.INK, true));
      Ui.gap(c, 8);
      c.addView(Ui.text(this, "이제 낯설었던 캠퍼스에 나만의 발자국이 생겼어요.", 15, Ui.MUTED, false));
      page.addView(c);
      Ui.gap(page,12);
      Button ending=Ui.button(this,"완성된 탐방 기록 보기",true);
      page.addView(ending);
      ending.setOnClickListener(v->startActivity(new Intent(this,EndingActivity.class).putExtra("demo",mode==AppMode.DEMO)));
    }
  }

  private void catalog() {
    header("CAMPUS GUIDE", "시설 도감", "탐방 전에도 시설 안내를 살펴볼 수 있어요.");
    EditText search = new EditText(this);
    search.setSingleLine(true);
    search.setHint("시설 또는 이야기 검색");
    page.addView(search);
    Ui.gap(page, 16);
    LinearLayout results = Ui.column(this);
    page.addView(results);
    Runnable update =
        () -> {
          results.removeAllViews();
          String q = search.getText().toString().trim().toLowerCase(Locale.ROOT);
          for (Mission m : missions) {
            if (!(m.place + " " + m.title + " " + m.explanation)
                .toLowerCase(Locale.ROOT)
                .contains(q)) continue;
            LinearLayout c = Ui.card(this);
            c.addView(Ui.label(this, done(m.id) ? "기억 수집 완료" : "기본 안내"));
            Ui.gap(c, 8);
            c.addView(Ui.text(this, m.place, 20, Ui.INK, true));
            Ui.gap(c, 8);
            c.addView(Ui.text(this, m.explanation, 15, Ui.MUTED, false));
            for (MissionCompletion record : completions)
              if (record.missionId.equals(m.id)) {
                Ui.gap(c, 10);
                c.addView(
                    Ui.text(
                        this,
                        (record.mode == AppMode.DEMO ? "테스트 기록" : "현장 기록")
                            + " · "
                            + ("AR".equals(record.discoveryMethod) ? "공간 AR 발견" : "일반 카드 발견")
                            + " · "
                            + methodLabel(record.completionMethod)
                            + "\n"
                            + android.text.format.DateFormat.format(
                                "yyyy.MM.dd HH:mm", record.savedAt),
                        12,
                        Ui.MUTED,
                        false));
              }
            for (MissionCompletion completion : completions)
              if (completion.missionId.equals(m.id) && completion.photoUri != null) {
                try {
                  ImageView photo = new ImageView(this);
                  Ui.loadPhoto(photo, completion.photoUri);
                  photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
                  Ui.gap(c, 12);
                  c.addView(photo, new LinearLayout.LayoutParams(-1, Ui.dp(this, 180)));
                } catch (Exception ignored) {
                }
              }
            results.addView(c);
            Ui.gap(results, 12);
          }
          if (results.getChildCount() == 0) {
            results.addView(Ui.text(this, "검색 결과가 없어요. 시설 이름이나 다른 단어로 찾아보세요.", 15, Ui.MUTED, false));
          }
        };
    search.addTextChangedListener(
        new TextWatcher() {
          public void beforeTextChanged(CharSequence s, int st, int count, int after) {}

          public void onTextChanged(CharSequence s, int st, int before, int count) {
            update.run();
          }

          public void afterTextChanged(Editable e) {}
        });
    update.run();
  }

  private void profile() {
    AuthSession account = new AuthSession(this);
    boolean guest = "guest".equals(account.currentUserId());
    header("MY JOURNAL", repo.getNickname() + "의 기록", guest ? "계정 없이 이 기기에 기록하고 있어요." : "완료 기록과 사진을 이 기기에 보관하고 계정별로 서버와 동기화합니다.");
        // FIRSTSTEP_PROFILE_V1

    int totalPlaces = missions.size();
    int completedPlaces = completions.size();

    int progressPercent = totalPlaces == 0
        ? 0
        : Math.min(100, (completedPlaces * 100) / totalPlaces);

    LinearLayout profileSummary = Ui.card(this);

    android.graphics.drawable.GradientDrawable profileBg =
        new android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
            new int[] {
                0xFFFFDCE8,
                0xFFFFF5F9
            }
        );

    profileBg.setCornerRadius(Ui.dp(this, 24));

    profileSummary.setBackground(profileBg);

    profileSummary.setPadding(
        Ui.dp(this, 22),
        Ui.dp(this, 24),
        Ui.dp(this, 22),
        Ui.dp(this, 24)
    );

    profileSummary.addView(
        Ui.text(
            this,
            "MY CAMPUS JOURNAL",
            12,
            0xFFAD5277,
            true
        )
    );

    Ui.gap(profileSummary, 12);

    profileSummary.addView(
        Ui.text(
            this,
            "나의 탐험 현황",
            25,
            Ui.INK,
            true
        )
    );

    Ui.gap(profileSummary, 8);

    profileSummary.addView(
        Ui.text(
            this,
            "캠퍼스에서 모은 소중한 기억을 확인해 보세요.",
            14,
            Ui.MUTED,
            false
        )
    );

    Ui.gap(profileSummary, 22);

    LinearLayout countRow = new LinearLayout(this);

    countRow.setOrientation(LinearLayout.HORIZONTAL);
    countRow.setGravity(android.view.Gravity.CENTER_VERTICAL);

    TextView countLabel = Ui.text(
        this,
        "완료한 장소",
        16,
        Ui.INK,
        true
    );

    countRow.addView(
        countLabel,
        new LinearLayout.LayoutParams(0, -2, 1)
    );

    TextView countValue = Ui.text(
        this,
        completedPlaces + " / " + totalPlaces,
        25,
        0xFFAD5277,
        true
    );

    countRow.addView(countValue);

    profileSummary.addView(countRow);

    Ui.gap(profileSummary, 16);

    android.widget.ProgressBar progressBar =
        new android.widget.ProgressBar(
            this,
            null,
            android.R.attr.progressBarStyleHorizontal
        );

    progressBar.setMax(100);
    progressBar.setProgress(progressPercent);

    progressBar.setProgressTintList(
        android.content.res.ColorStateList.valueOf(
            0xFFE58CAC
        )
    );

    progressBar.setProgressBackgroundTintList(
        android.content.res.ColorStateList.valueOf(
            0xFFFFC8DA
        )
    );

    profileSummary.addView(
        progressBar,
        new LinearLayout.LayoutParams(
            -1,
            Ui.dp(this, 12)
        )
    );

    Ui.gap(profileSummary, 12);

    profileSummary.addView(
        Ui.text(
            this,
            "전체 탐험 진행률 " + progressPercent + "%",
            14,
            Ui.MUTED,
            false
        )
    );

    Ui.gap(profileSummary, 16);

    String encouragement = completedPlaces == 0
        ? "첫 번째 캠퍼스 기억을 찾아보세요."
        : completedPlaces >= totalPlaces
            ? "모든 캠퍼스 기억을 완성했어요!"
            : "좋아요! 다음 장소의 기억도 찾아보세요.";

    profileSummary.addView(
        Ui.text(
            this,
            encouragement,
            14,
            0xFFAD5277,
            true
        )
    );

    page.addView(profileSummary);

    Ui.gap(page, 22);
    LinearLayout accountCard = Ui.card(this);
    accountCard.addView(Ui.label(this, guest ? "기기에서 이용 중" : "계정에 연결됨"));Ui.gap(accountCard, 8);
    accountCard.addView(Ui.text(this, guest ? "로그인 없이도 탐방할 수 있어요" : account.userNickname(), 20, Ui.INK, true));Ui.gap(accountCard, 8);
    accountCard.addView(Ui.text(this, guest ? "로그인하면 완료 기록과 사진을 계정별로 서버와 동기화합니다. 기존 기록은 직접 가져오기를 선택해야 복사됩니다." : ("GOOGLE".equalsIgnoreCase(account.provider()) ? "Google 계정 · 학교 인증 아님" : "개발용 계정 · Google 또는 학교 인증 아님"), 14, Ui.MUTED, false));
    Ui.gap(accountCard, 12);
    if (guest) {
      Button signIn = Ui.button(this, "계정으로 로그인", true);accountCard.addView(signIn);signIn.setEnabled(!accountBusy);signIn.setOnClickListener(v -> openAuthentication());
    } else {
      accountCard.addView(Ui.text(this, repo.getSyncStatus(), 13, Ui.MUTED, false));Ui.gap(accountCard, 8);
      Button sync = Ui.button(this, "서버와 동기화", true);accountCard.addView(sync);sync.setEnabled(!accountBusy);
      sync.setOnClickListener(v -> synchronizeAccount(false));Ui.gap(accountCard, 8);
      Button importGuest = Ui.button(this, "이 기기의 기존 기록 가져오기", false);accountCard.addView(importGuest);importGuest.setEnabled(!accountBusy);
      importGuest.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("기존 기기 기록을 가져올까요?")
          .setMessage("계정 없이 남긴 이 기기의 완료 기록과 사진을 현재 계정에 복사하고 서버와 동기화합니다. 원래 기기 기록은 유지됩니다.")
          .setPositiveButton("가져오기",(d,w)->synchronizeAccount(true)).setNegativeButton("취소",null).show());Ui.gap(accountCard, 8);
      Button signOut = Ui.button(this, "로그아웃", false);accountCard.addView(signOut);signOut.setEnabled(!accountBusy);
      signOut.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("이 기기에서 로그아웃할까요?")
          .setMessage("계정 기록은 별도로 보관하고, 로그인 전에 사용하던 기기 기록으로 돌아갑니다.")
          .setPositiveButton("로그아웃",(d,w)->signOut()).setNegativeButton("취소",null).show());
    }
    page.addView(accountCard);Ui.gap(page, 20);
    LinearLayout c = Ui.card(this);
    c.addView(Ui.label(this, mode == AppMode.DEMO ? "DEMO 기록" : "현장 기록"));
    Ui.gap(c, 10);
    c.addView(Ui.text(this, completions.size() + "개의 기억", 24, Ui.INK, true));
    Ui.gap(c, 8);
    c.addView(Ui.text(this, "테스트 기록과 현장 기록은 별도로 보관됩니다.", 14, Ui.MUTED, false));
    page.addView(c);
    Ui.gap(page, 24);
    Button rename = Ui.button(this, "닉네임 변경", false);
    page.addView(rename);
    rename.setOnClickListener(v -> {
      EditText input = new EditText(this);
      input.setText(repo.getNickname());
      new AlertDialog.Builder(this)
          .setTitle("닉네임 변경")
          .setView(input)
          .setPositiveButton(
              "저장",
              (d, w) -> {
                String n = input.getText().toString().trim();
                if (!n.isEmpty()) {
                  repo.setNickname(n);
                  render();
                }
              })
          .setNegativeButton("취소", null)
          .show();
    });
    Ui.gap(page, 10);
    Button reset = Ui.button(this, guest ? "현재 모드 기록 초기화" : "서버·기기의 현재 모드 기록 삭제", false);
    page.addView(reset);
    reset.setEnabled(!accountBusy);
    reset.setOnClickListener(v ->
        new AlertDialog.Builder(this)
            .setTitle("기록을 초기화할까요?")
            .setMessage((mode == AppMode.DEMO ? "테스트" : "현장") + (guest ? " 모드의 기기 기록과 연결된 사진이 삭제됩니다." : " 모드의 완료 기록과 사진을 서버와 이 기기에서 삭제합니다. 서버 연결에 실패하면 기기 기록도 유지됩니다."))
            .setPositiveButton(
                "초기화",
                (d, w) -> {
                    if(accountBusy)return;
                    final AppMode resetMode=mode;
                    final MissionRepository resetRepository=repo;
                    final String resetAccount=accountNamespace;
                    accountBusy=true;render();
                    resetRepository.resetProgress(
                        resetMode,
                        ok -> {
                          if(!Objects.equals(resetAccount,AuthSession.namespace(this))||repo!=resetRepository)return;
                          if(ok)MissionCheckpointStore.clearMode(getApplicationContext(),resetMode==AppMode.DEMO);
                          if(isFinishing()||isDestroyed())return;
                          accountBusy=false;
                          refresh();
                          if (!ok)
                            Toast.makeText(
                                    this,
                                    resetRepository.isAccountRepository()?resetRepository.getSyncStatus():"기록 초기화 또는 사진 정리에 문제가 있어요. 현재 기록을 다시 확인해 주세요.",
                                    Toast.LENGTH_LONG)
                                .show();
                        });})
            .setNegativeButton("취소", null)
            .show());
    Ui.gap(page, 24);
    page.addView(
        Ui.text(
            this,
            "콘텐츠 초안 2026.10.06 · 정보 확인 전\n시설 정보와 좌표는 현장 확인 전의  내용입니다.",
            13,
            Ui.MUTED,
            false));
  }

  private String methodLabel(String method) {
    if (method == null) return "이전 버전 기록";
    switch (method) {
      case "PHOTO":
        return "사진 촬영";
      case "DEMO_PHOTO":
        return "테스트 이미지 · 실제 촬영 아님";
      case "LIGHT":
        return "조도센서";
      case "HOLD":
        return "길게 누르기 대체 조작";
      case "SPEECH":
        return "음성 인식";
      case "TYPED":
        return "직접 입력";
      case "ACCELEROMETER":
        return "가속도센서";
      case "COMPASS":
        return "방향센서";
      default:
        return "이전 버전 기록";
    }
  }

  private void openAuthentication() { if(!accountBusy)startActivityForResult(new Intent(this, AuthActivity.class), 200); }
  private void selectCurrentAccount() {
    accountNamespace=AuthSession.namespace(this);repo=MissionRepository.getInstance(this);mode=repo.getMode();missions=repo.getMissions();completions=new ArrayList<>();accountBusy=false;
    AuthSession account=new AuthSession(this);
    if(!"guest".equals(account.currentUserId())&&!account.userNickname().isEmpty())repo.setNickname(account.userNickname());
    tab="profile";
    if(repo.getNickname().isEmpty())onboarding();else refresh();
  }
  private void synchronizeAccount(boolean importGuest) {
    if(accountBusy||"guest".equals(new AuthSession(this).currentUserId()))return;
    accountBusy=true;MissionRepository target=repo;String targetAccount=accountNamespace;render();
    MissionRepository.CompletionCallback callback=ok->{
      if(isFinishing()||isDestroyed()||target!=repo||!Objects.equals(targetAccount,AuthSession.namespace(this)))return;
      accountBusy=false;Toast.makeText(this,target.getSyncStatus(),Toast.LENGTH_LONG).show();refresh();
    };
    if(importGuest)target.importGuestProgress(callback);else target.syncAccount(callback);
  }
  private void signOut() {
    AuthSession session=new AuthSession(this);String capturedToken=session.getAccessToken();session.clear();selectCurrentAccount();
    java.util.concurrent.ExecutorService logoutWorker=java.util.concurrent.Executors.newSingleThreadExecutor();
    logoutWorker.execute(()->{
      boolean revoked=true;try{if(!capturedToken.isEmpty())new CloudApi(getApplicationContext(),capturedToken).request("POST","/v1/auth/logout",new org.json.JSONObject());}catch(Exception error){revoked=false;}
      final boolean serverRevoked=revoked;runOnUiThread(()->{if(!isDestroyed()&&!isFinishing()&&"guest".equals(new AuthSession(this).currentUserId()))Toast.makeText(this,serverRevoked?"로그아웃했어요.":"이 기기에서 로그아웃했어요. 서버 연결은 확인하지 못했습니다.",Toast.LENGTH_LONG).show();});
      logoutWorker.shutdown();
    });
    try {androidx.credentials.CredentialManager.create(this).clearCredentialStateAsync(new androidx.credentials.ClearCredentialStateRequest(),null,androidx.core.content.ContextCompat.getMainExecutor(this),new androidx.credentials.CredentialManagerCallback<Void,androidx.credentials.exceptions.ClearCredentialException>(){
      public void onResult(Void value){} public void onError(androidx.credentials.exceptions.ClearCredentialException error){}
    });}catch(RuntimeException unavailable){/* Local session is already removed even without a provider. */}
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (request == 100) refresh();
    if (request == 200 && result == RESULT_OK) {selectCurrentAccount();synchronizeAccount(false);}
  }
}
