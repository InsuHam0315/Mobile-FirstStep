package com.example.firststep;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.example.firststep.facility.Facility;
import com.example.firststep.facility.FacilityCatalogValidator;
import com.example.firststep.facility.FacilityRepository;
import com.example.firststep.facility.FacilitySearch;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/** Offline campus facility guide; no locations or hours are claimed as verified. */
public final class FacilityDirectoryActivity extends AppCompatActivity {
  private static final int ROSE = Color.rgb(166, 59, 99),
      INK = Color.rgb(41, 43, 53),
      MUTED = Color.rgb(102, 99, 102),
      LINE = Color.rgb(232, 222, 221),
      BLUSH = Color.rgb(249, 231, 235);
  private static final String[] CATEGORIES = {"전체", "학습", "학생지원", "진로·취업", "생활"};
  private FacilityRepository repository;
  private List<Facility> facilities = Collections.emptyList();
  private LinearLayout content, filters, results;
  private TextView count;
  private EditText search;
  private Button favoriteFilter;
  private String category = "전체";
  private boolean favoritesOnly;
  private boolean loadFailed;

  @Override
  protected void onCreate(Bundle state) {
    super.onCreate(state);
    setContentView(R.layout.activity_facility_directory);
    Ui.applyInsets(findViewById(R.id.facility_root));
    repository = new FacilityRepository(this);
    try {
      facilities = repository.load();
    } catch (Exception e) {
      loadFailed = true;
    }
    if (state != null) {
      category = state.getString("category", "전체");
      favoritesOnly = state.getBoolean("favorites");
    }
    content = findViewById(R.id.facility_content);
    build(state == null ? "" : state.getString("query", ""));
  }

  @Override
  protected void onSaveInstanceState(Bundle out) {
    out.putString("query", search.getText().toString());
    out.putString("category", category);
    out.putBoolean("favorites", favoritesOnly);
    super.onSaveInstanceState(out);
  }

  private TextView text(String value, int size, int color, boolean bold) {
    return Ui.text(this, value, size, color, bold);
  }

  private Button button(String label, boolean selected) {
    Button b = Ui.button(this, label, false);
    b.setMinHeight(Ui.dp(this, 48));
    b.setTextColor(selected ? Color.WHITE : INK);
    b.setBackground(Ui.bg(selected ? ROSE : BLUSH, Ui.dp(this, 14)));
    return b;
  }

  private void build(String query) {
    TextView back = text("←  돌아가기", 15, MUTED, true);
    back.setGravity(Gravity.CENTER_VERTICAL);
    back.setMinHeight(Ui.dp(this, 48));
    back.setOnClickListener(v -> finish());
    content.addView(back);
    Ui.gap(content, 12);
    TextView overline = text("CAMPUS GUIDE", 12, ROSE, true);
    overline.setLetterSpacing(.1f);
    content.addView(overline);
    Ui.gap(content, 8);
    content.addView(text("필요할 때 찾아보는\n캠퍼스 시설", 29, INK, true));
    Ui.gap(content, 10);
    content.addView(text("공식 링크 확인 · 위치·운영시간 현장 확인 전", 13, MUTED, false));
    Ui.gap(content, 22);
    search = new EditText(this);
    search.setSingleLine(true);
    search.setTextSize(16);
    search.setTextColor(INK);
    search.setHintTextColor(MUTED);
    search.setHint("예: 공부, 학식, 상담, 증명서");
    search.setContentDescription("시설 검색");
    search.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), 0);
    search.setBackground(Ui.bg(Color.WHITE, Ui.dp(this, 14)));
    search.setMinHeight(Ui.dp(this, 52));
    search.setText(query);
    content.addView(search, new LinearLayout.LayoutParams(-1, Ui.dp(this, 54)));
    search.addTextChangedListener(
        new TextWatcher() {
          public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

          public void onTextChanged(CharSequence s, int start, int before, int count) {
            renderResults();
          }

          public void afterTextChanged(Editable s) {}
        });
    Ui.gap(content, 16);
    HorizontalScrollView filterScroll = new HorizontalScrollView(this);
    filterScroll.setHorizontalScrollBarEnabled(false);
    filters = new LinearLayout(this);
    filters.setOrientation(LinearLayout.HORIZONTAL);
    filterScroll.addView(filters);
    content.addView(filterScroll);
    Ui.gap(content, 12);
    LinearLayout row = new LinearLayout(this);
    row.setGravity(Gravity.CENTER_VERTICAL);
    count = text("", 14, MUTED, true);
    row.addView(count, new LinearLayout.LayoutParams(0, -2, 1));
    favoriteFilter = button("☆ 즐겨찾기만", favoritesOnly);
    favoriteFilter.setOnClickListener(v -> {
      favoritesOnly = !favoritesOnly;
      renderFilters();
      renderResults();
    });
    row.addView(favoriteFilter);
    content.addView(row);
    Ui.gap(content, 12);
    results = Ui.column(this);
    content.addView(results);
    renderFilters();
    renderResults();
  }

  private void renderFilters() {
    filters.removeAllViews();
    for (String label : CATEGORIES) {
      boolean selected = label.equals(category);
      Button b = button(label, selected);
      b.setContentDescription(label + " 분류, " + (selected ? "선택됨" : "선택 안 됨"));
      LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, Ui.dp(this, 48));
      p.setMargins(0, 0, Ui.dp(this, 8), 0);
      filters.addView(b, p);
      b.setOnClickListener(v -> {
        category = label;
        renderFilters();
        renderResults();
      });
    }
    favoriteFilter.setText(favoritesOnly ? "★ 즐겨찾기만" : "☆ 즐겨찾기만");
    favoriteFilter.setContentDescription("즐겨찾기만 보기, " + (favoritesOnly ? "선택됨" : "선택 안 됨"));
    favoriteFilter.setBackground(Ui.bg(favoritesOnly ? ROSE : BLUSH, Ui.dp(this, 14)));
    favoriteFilter.setTextColor(favoritesOnly ? Color.WHITE : INK);
  }

  private void renderResults() {
    if (results == null) return;
    results.removeAllViews();
    if (loadFailed) {
      count.setText("안내를 불러오지 못했어요");
      results.addView(text("시설 안내를 불러오지 못했어요. 앱을 다시 열어 주세요.", 15, MUTED, false));
      return;
    }
    Set<String> favorites = repository.favorites();
    List<Facility> visible =
        FacilitySearch.filter(
            facilities,
            search.getText().toString(),
            category.equals("전체") ? null : category,
            favoritesOnly,
            favorites);
    count.setText(visible.size() + "개 시설");
    if (visible.isEmpty()) {
      Ui.gap(results, 24);
      results.addView(
          text(
              favoritesOnly && favorites.isEmpty() ? "아직 즐겨찾기가 없어요" : "검색 결과가 없어요", 20, INK, true));
      Ui.gap(results, 8);
      results.addView(
          text(
              favoritesOnly && favorites.isEmpty()
                  ? "자주 찾는 시설의 별을 눌러 저장해 보세요."
                  : "다른 검색어나 분류로 다시 찾아보세요.",
              15,
              MUTED,
              false));
      Ui.gap(results, 16);
      Button clear = button("검색과 필터 지우기", false);
      results.addView(clear);
      clear.setOnClickListener(v -> {
        search.setText("");
        category = "전체";
        favoritesOnly = false;
        renderFilters();
        renderResults();
      });
      return;
    }
    for (Facility f : visible) {
      LinearLayout row = new LinearLayout(this);
      row.setGravity(Gravity.CENTER_VERTICAL);
      row.setPadding(0, Ui.dp(this, 15), 0, Ui.dp(this, 15));
      LinearLayout info = Ui.column(this);
      info.setMinimumHeight(Ui.dp(this, 56));
      info.addView(text(f.category, 12, ROSE, true));
      Ui.gap(info, 4);
      info.addView(text(f.name, 19, INK, true));
      Ui.gap(info, 4);
      info.addView(text(f.summary, 14, MUTED, false));
      info.setContentDescription(f.name + ", " + f.category + ". " + f.summary + ". 상세 보기");
      info.setFocusable(true);
      info.setOnClickListener(v -> details(f));
      row.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
      boolean saved = favorites.contains(f.id);
      Button star = button(saved ? "★" : "☆", false);
      star.setTextSize(23);
      star.setContentDescription(f.name + " 즐겨찾기 " + (saved ? "해제" : "추가"));
      LinearLayout.LayoutParams starParams =
          new LinearLayout.LayoutParams(Ui.dp(this, 52), Ui.dp(this, 52));
      starParams.setMargins(Ui.dp(this, 10), 0, 0, 0);
      row.addView(star, starParams);
      star.setOnClickListener(v -> {
        repository.setFavorite(f.id, !saved);
        renderResults();
      });
      results.addView(row);
      View line = new View(this);
      line.setBackgroundColor(LINE);
      results.addView(line, new LinearLayout.LayoutParams(-1, Ui.dp(this, 1)));
    }
  }

  private void details(Facility f) {
    LinearLayout panel = Ui.column(this);
    panel.setPadding(Ui.dp(this, 24), Ui.dp(this, 8), Ui.dp(this, 24), Ui.dp(this, 20));
    panel.addView(text(f.category, 13, ROSE, true));
    Ui.gap(panel, 8);
    panel.addView(text(f.summary, 16, INK, false));
    Ui.gap(panel, 20);
    panel.addView(text("이럴 때 찾아보세요", 16, INK, true));
    for (String use : f.useCases) {
      Ui.gap(panel, 7);
      panel.addView(text("•  " + use, 15, MUTED, false));
    }
    Ui.gap(panel, 20);
    panel.addView(text("위치와 운영시간", 16, INK, true));
    Ui.gap(panel, 7);
    panel.addView(text(f.locationStatus + "\n" + f.hoursStatus, 14, MUTED, false));
    Ui.gap(panel, 18);
    panel.addView(
        text(
            "출처: "
                + f.sourceLabel
                + "\n공식 링크 확인일: "
                + f.sourceCheckedOn
                + "\n운영시간과 위치의 현장 확인일은 아닙니다.",
            13,
            MUTED,
            false));
    Ui.gap(panel, 18);
    Button link = button("공식 안내 열기 ↗", true);
    panel.addView(link, new LinearLayout.LayoutParams(-1, Ui.dp(this, 52)));
    link.setOnClickListener(v -> {
      Uri uri = Uri.parse(f.officialUrl);
      if (!FacilityCatalogValidator.isOfficialUrl(f.officialUrl)) {
        Toast.makeText(this, "공식 링크를 열 수 없어요.", Toast.LENGTH_SHORT).show();
        return;
      }
      try {
        startActivity(new Intent(Intent.ACTION_VIEW, uri));
      } catch (ActivityNotFoundException e) {
        Toast.makeText(this, "링크를 열 브라우저가 없어요.", Toast.LENGTH_SHORT).show();
      }
    });
    ScrollView scroll = new ScrollView(this);
    scroll.addView(panel);
    new AlertDialog.Builder(this)
        .setTitle(f.name)
        .setView(scroll)
        .setPositiveButton("닫기", null)
        .show();
  }
}
