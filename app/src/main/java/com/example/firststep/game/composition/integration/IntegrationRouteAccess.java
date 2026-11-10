package com.example.firststep.game.composition.integration;

import android.content.Context;
import com.example.firststep.game.domain.*;
import com.example.firststep.game.presentation.integration.NavigationPolicy;

/** Blocking validated fixture access. Never opens a default/legacy database. */
public final class IntegrationRouteAccess {
  private IntegrationRouteAccess() {}

  public static NavigationPolicy.State bound(Context context, IntegrationContext requested) {
    if (!IntegrationComposition.allowedContext(requested, com.example.firststep.BuildConfig.DEBUG))
      throw new IllegalArgumentException("unregistered context");
    NavigationStore store = new NavigationStore(context);
    if (!store.bindClock(requested)) throw new IllegalStateException("context storage unavailable");
    NavigationPolicy.State state = store.load(requested);
    if (state == null) throw new IllegalStateException("invalid navigation record");
    return state;
  }

  public static Progress readProgress(Context context, IntegrationContext requested)
      throws Exception {
    try (IntegrationComposition composition = IntegrationComposition.open(context, requested)) {
      return composition.repository.read(composition.context.mode, composition.context.targetId);
    }
  }

  public static IntegrationContext requireIntroduction(Context context, IntegrationContext c)
      throws Exception {
    NavigationPolicy.State state = bound(context, c);
    if (state.phase != NavigationPolicy.Phase.CONSUMED) {
      if (IntegrationContext.SOFTWARE_DATASET.equals(state.context.datasetId)
          || readProgress(context, state.context).introDoneAt == null)
        throw new IllegalStateException("discovery continuation required");
    }
    return state.context;
  }
}
