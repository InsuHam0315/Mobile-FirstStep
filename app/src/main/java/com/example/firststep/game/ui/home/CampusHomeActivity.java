package com.example.firststep.game.ui.home;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.example.firststep.FacilityDirectoryActivity;
import com.example.firststep.R;
import com.example.firststep.RouteMapActivity;
import com.example.firststep.game.ui.authored.AuthoredEpisodeActivity;
import com.example.firststep.game.ui.guide.GuideActivity;

/** Campus browsing surface. Session validation belongs to its caller. */
public final class CampusHomeActivity extends AppCompatActivity {
  @Override
  protected void onCreate(Bundle state) {
    super.onCreate(state);
    setContentView(R.layout.game_campus_home_activity);
    ViewCompat.setAccessibilityHeading(findViewById(R.id.game_campus_home_title), true);
    ViewCompat.setOnApplyWindowInsetsListener(
        findViewById(R.id.game_campus_home_scroll),
        (view, insets) -> {
          Insets bars = insets.getInsets(
              WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
          view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
          return insets;
        });
    findViewById(R.id.game_campus_home_map)
        .setOnClickListener(v -> startActivity(RouteMapActivity.browseIntent(this)));
    findViewById(R.id.game_campus_home_facilities)
        .setOnClickListener(v -> startActivity(new Intent(this, FacilityDirectoryActivity.class)));
    findViewById(R.id.game_campus_home_guide)
        .setOnClickListener(v -> startActivity(new Intent(this, GuideActivity.class)));
    findViewById(R.id.game_campus_home_authored)
        .setOnClickListener(v -> startActivity(AuthoredEpisodeActivity.intent(this)));
    findViewById(R.id.game_campus_home_back).setOnClickListener(v -> finish());
  }
}
