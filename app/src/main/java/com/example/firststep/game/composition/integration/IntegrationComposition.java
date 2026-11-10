package com.example.firststep.game.composition.integration;

import android.content.Context;
import androidx.room.Room;
import com.example.firststep.BuildConfig;
import com.example.firststep.game.content.*;
import com.example.firststep.game.data.local.*;
import com.example.firststep.game.domain.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Closed, blocking registry for the two integration datasets. Open off the UI thread. */
public final class IntegrationComposition implements AutoCloseable {
  public final IntegrationContext context;
  public final GameContent content;
  public final GuideBundle guide;
  public final NarrationContent narration;
  public final RoomProgressRepository repository;

  private IntegrationComposition(IntegrationContext context, GameContent content, GuideBundle guide,
      NarrationContent narration, RoomProgressRepository repository) {
    this.context = context; this.content = content; this.guide = guide;
    this.narration = narration; this.repository = repository;
  }

  /** Structural helper permits release guard testing without a release Firebase build. */
  public static boolean allowedDataset(String dataset, boolean debug) {
    return IntegrationContext.DATASET.equals(dataset)
        || (debug && IntegrationContext.SOFTWARE_DATASET.equals(dataset));
  }

  public static boolean allowedContext(IntegrationContext c, boolean debug) {
    if (c == null || !allowedDataset(c.datasetId, debug)) return false;
    try {
      IntegrationContext.create(c.datasetId, c.runId, c.targetId, c.mode, c.clock, c.fakeNow);
      return true;
    } catch (RuntimeException invalid) { return false; }
  }

  public static String databaseName(IntegrationContext c, boolean debug) {
    if (!allowedContext(c, debug)) throw new IllegalArgumentException("unregistered context");
    return (IntegrationContext.DATASET.equals(c.datasetId) ? "h04-debug-" : "software-episode-demo-")
        + c.runId + ".db";
  }

  public static IntegrationComposition open(Context androidContext, IntegrationContext requested)
      throws Exception {
    if (!allowedContext(requested, BuildConfig.DEBUG)) throw new IllegalArgumentException("unregistered context");
    Context app = androidContext.getApplicationContext();
    GuideBundle guide = null;
    NarrationContent narration = null;
    GameContent content;
    if (IntegrationContext.SOFTWARE_DATASET.equals(requested.datasetId)) {
      guide = new AssetGuideRepository(app).load();
      narration = NarrationAsset.load(app);
      if (!requested.targetId.equals(guide.getTargetId())) throw new IOException("guide target mismatch");
      Set<String> claims = new HashSet<>();
      for (GuideBundle.Claim claim : guide.getClaims()) claims.add(claim.getId());
      for (NarrationContent.Sentence sentence : narration.sentences())
        if (sentence.provenance == NarrationContent.Provenance.CLAIM && !claims.contains(sentence.claimId))
          throw new IOException("narration claim mismatch");
      List<GameContent.Question> questions = new ArrayList<>();
      for (GuideBundle.Question q : guide.getQuestions()) {
        List<GameContent.Option> options = new ArrayList<>();
        for (GuideBundle.Option option : q.getOptions()) options.add(new GameContent.Option(option.getId(), option.getText()));
        questions.add(new GameContent.Question(q.getId(), q.getText(), q.getAnswerId(), options));
      }
      content = new GameContent(requested.datasetId, Collections.singletonList(
          new GameContent.Building(requested.targetId, "소프트웨어학과", questions)));
    } else {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      try (InputStream in = app.getAssets().open("game_intro/catalog.json")) {
        byte[] buffer = new byte[4096]; int n;
        while ((n = in.read(buffer)) != -1) {
          if (bytes.size() + n > 65536) throw new IOException("catalog too large");
          bytes.write(buffer, 0, n);
        }
      }
      content = GameJson.catalog(bytes.toString(StandardCharsets.UTF_8.name()));
    }
    if (!requested.datasetId.equals(content.version) || !content.buildings.containsKey(requested.targetId))
      throw new IOException("registered content mismatch");
    IntegrationContext bound = IntegrationRouteAccess.bound(app, requested).context;
    RoomProgressRepository repository = new RoomProgressRepository(Room.databaseBuilder(
        app, GameProgressDatabase.class, databaseName(bound, BuildConfig.DEBUG)).build());
    return new IntegrationComposition(bound, content, guide, narration, repository);
  }

  @Override public void close() { repository.close(); }
}
