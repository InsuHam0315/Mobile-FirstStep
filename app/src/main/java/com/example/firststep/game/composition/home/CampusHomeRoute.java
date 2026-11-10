package com.example.firststep.game.composition.home;

import android.content.Context;
import android.content.Intent;
import com.example.firststep.game.ui.home.CampusHomeActivity;

/** Entry for a caller that has already established its own access policy. */
public final class CampusHomeRoute {
  private CampusHomeRoute() {}

  public static Intent intent(Context context) {
    return new Intent(context, CampusHomeActivity.class);
  }
}
