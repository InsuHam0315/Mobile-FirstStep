package com.example.firststep.game.ui;

import android.content.Context;
import android.widget.Button;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import com.example.firststep.R;

/** Applies the shared visual roles to buttons created by Activity renderers. */
public final class GameVisuals {
  private GameVisuals() {}

  public static void action(Button button, boolean primary) {
    Context context = button.getContext();
    button.setAllCaps(false);
    button.setLetterSpacing(0);
    button.setMinHeight(context.getResources().getDimensionPixelSize(R.dimen.game_touch_min));
    button.setTextColor(ContextCompat.getColorStateList(context,
        primary ? R.color.game_reskin_primary_text : R.color.game_reskin_secondary_text));
    button.setBackgroundResource(R.drawable.game_reskin_action_background);
    ViewCompat.setBackgroundTintList(button, ContextCompat.getColorStateList(context,
        primary ? R.color.game_reskin_primary_tint : R.color.game_reskin_secondary_tint));
  }
}
