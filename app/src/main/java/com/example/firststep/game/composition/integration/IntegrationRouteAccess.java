package com.example.firststep.game.composition.integration;

import android.content.Context;
import androidx.room.Room;
import com.example.firststep.game.data.local.*;
import com.example.firststep.game.domain.*;
import com.example.firststep.game.presentation.integration.NavigationPolicy;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Blocking validated fixture access. Never opens a default/legacy database. */
public final class IntegrationRouteAccess {
  private IntegrationRouteAccess() {}

  public static NavigationPolicy.State bound(Context context, IntegrationContext requested) {
    NavigationStore store = new NavigationStore(context);
    if (!store.bindClock(requested)) throw new IllegalStateException("context storage unavailable");
    NavigationPolicy.State state = store.load(requested);
    if (state == null) throw new IllegalStateException("invalid navigation record");
    return state;
  }

  public static Progress readProgress(Context context, IntegrationContext requested)
      throws Exception {
    IntegrationContext c = bound(context, requested).context;
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (InputStream in = context.getAssets().open("game_intro/catalog.json")) {
      byte[] buffer = new byte[4096];
      int n;
      while ((n = in.read(buffer)) != -1) bytes.write(buffer, 0, n);
    }
    GameContent catalog = GameJson.catalog(bytes.toString(StandardCharsets.UTF_8.name()));
    if (!c.datasetId.equals(catalog.version) || !catalog.buildings.containsKey(c.targetId))
      throw new IllegalStateException("fixture catalog mismatch");
    try (RoomProgressRepository repository =
        new RoomProgressRepository(
            Room.databaseBuilder(
                    context.getApplicationContext(),
                    GameProgressDatabase.class,
                    "h04-debug-" + c.runId + ".db")
                .build())) {
      return repository.read(c.mode, c.targetId);
    }
  }

  public static IntegrationContext requireIntroduction(Context context, IntegrationContext c)
      throws Exception {
    NavigationPolicy.State state = bound(context, c);
    Progress progress = readProgress(context, state.context);
    if (state.phase != NavigationPolicy.Phase.CONSUMED && progress.introDoneAt == null)
      throw new IllegalStateException("discovery continuation required");
    return state.context;
  }
}
