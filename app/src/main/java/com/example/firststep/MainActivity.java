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

public class MainActivity extends AppCompatActivity {
  private MissionRepository repo;
  private AppMode mode;
  private List<Mission> missions;
  private List<MissionCompletion> completions = new ArrayList<>();
  private LinearLayout page, nav;
  private String tab = "home";

  @Override
  protected void onCreate(Bundle b) {
    super.onCreate(b);
    repo = MissionRepository.getInstance(this);
    mode = repo.getMode();
    missions = repo.getMissions();
    if (repo.getNickname().isEmpty()) onboarding();
    else refresh();
  }

  private void refresh() {
    AppMode requested = mode;
    repo.getCompletions(
        requested,
        list -> {
          if (requested != mode) return;
          completions = list;
          render();
        });
  }

  private boolean done(String id) {
    for (MissionCompletion c : completions) if (c.missionId.equals(id)) return true;
    return false;
  }

  private void shell() {
    setContentView(R.layout.activity_main);
    LinearLayout root = findViewById(R.id.shell);
    Ui.applyInsets(root);
    page = findViewById(R.id.page);
    nav = findViewById(R.id.navigation);
    String[] labels = {"코스", "시설 도감", "내 기록"};
    String[] tabs = {"home", "catalog", "profile"};
    for (int i = 0; i < 3; i++) {
      final String target = tabs[i];
      TextView t = Ui.text(this, labels[i], 14, target.equals(tab) ? Ui.RUST : Ui.MUTED, true);
      t.setGravity(android.view.Gravity.CENTER);
      nav.addView(t, new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1));
      t.setOnClickListener(v -> {
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
                : "현재 장소 좌표는 현장 검증 전이라 미션을 시작할 수 없습니다.",
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
    Button routeMap = Ui.button(this, "코스 안내도", false);
    page.addView(routeMap);
    routeMap.setOnClickListener(v -> startActivity(new Intent(this, RouteMapActivity.class)));
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
    header("MY JOURNAL", repo.getNickname() + "의 기록", "기억은 이 기기에 저장됩니다.");
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
    Button reset = Ui.button(this, "현재 모드 기록 초기화", false);
    page.addView(reset);
    reset.setOnClickListener(v ->
        new AlertDialog.Builder(this)
            .setTitle("기록을 초기화할까요?")
            .setMessage((mode == AppMode.DEMO ? "테스트" : "현장") + " 모드의 완료 기록과 연결된 사진이 삭제됩니다.")
            .setPositiveButton(
                "초기화",
                (d, w) ->
                    repo.resetProgress(
                        mode,
                        ok -> {
                          refresh();
                          if (!ok)
                            Toast.makeText(
                                    this,
                                    "기록 초기화 또는 사진 정리에 문제가 있어요. 현재 기록을 다시 확인해 주세요.",
                                    Toast.LENGTH_LONG)
                                .show();
                        }))
            .setNegativeButton("취소", null)
            .show());
    Ui.gap(page, 24);
    page.addView(
        Ui.text(
            this,
            "콘텐츠 초안 2026.10.06 · 정보 확인 전\n시설 정보와 좌표는 현장 확인 전의 후보 내용입니다.",
            13,
            Ui.MUTED,
            false));
  }

  private String methodLabel(String method) {
    if (method == null) return "이전 버전 기록";
    switch (method) {
      case "PHOTO":
        return "사진 촬영";
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
      default:
        return "이전 버전 기록";
    }
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (request == 100) refresh();
  }
}
