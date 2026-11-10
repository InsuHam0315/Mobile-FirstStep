package com.example.firststep.game.composition.introquiz;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import androidx.room.Room;
import com.example.firststep.game.composition.integration.IntegrationComposition;
import com.example.firststep.game.composition.integration.IntegrationContext;
import com.example.firststep.game.data.local.*;
import com.example.firststep.game.domain.*;
import com.example.firststep.game.notifications.IntroQuizReminderReceiver;
import com.example.firststep.game.presentation.introquiz.IntroQuizController;
import com.example.firststep.game.speech.IntroSpeech;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Owns the H04 graph and its worker/database lifetimes. */
public final class IntroQuizHost implements AutoCloseable {
  private final Context context;
  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private final Handler main = new Handler(Looper.getMainLooper());
  private final String mode, run;
  private final IntegrationContext integration;
  private IntegrationComposition composition;
  private final boolean instant, fake;
  private volatile long now;
  private volatile boolean closed;
  private RoomProgressRepository repo;
  private IntroSpeech speech;
  private IntroQuizController ticking;
  private final Runnable tick =
      new Runnable() {
        @Override
        public void run() {
          if (closed || ticking == null) return;
          ticking.tick();
          main.postDelayed(this, 1000);
        }
      };

  public IntroQuizHost(
      Context c, String mode, boolean instant, String run, boolean fake, long now,
      IntegrationContext integration) {
    context = c.getApplicationContext();
    this.mode = mode;
    this.instant = instant;
    this.run = run;
    this.fake = fake;
    this.now = now;
    this.integration = integration;
  }

  public IntegrationComposition composition() {
    return composition;
  }

  public void startTicks(IntroQuizController controller) {
    stopTicks();
    if (closed || controller == null) return;
    ticking = controller;
    main.postDelayed(tick, 1000);
  }

  public void stopTicks() {
    ticking = null;
    main.removeCallbacks(tick);
  }

  public void pauseSpeech() {
    if (speech != null) speech.stop();
  }

  public void setClock(long time) {
    if (fake) now = time;
  }

  public void start(
      String[] sentences,
      String speechMode,
      Consumer<String> speechStatus,
      IntroQuizController.Observer observer,
      Consumer<IntroQuizController> ready,
      Runnable failed) {
    speech = new IntroSpeech(context, speechMode, speechStatus);
    worker.execute(
        () -> {
          try {
            GameContent content;
            String[] captions = sentences;
            if (integration != null) {
              composition = IntegrationComposition.open(context, integration);
              content = composition.content;
              repo = composition.repository;
              if (composition.narration != null)
                captions =
                    composition.narration.sentences().stream()
                        .map(sentence -> sentence.text)
                        .toArray(String[]::new);
            } else {
              ByteArrayOutputStream bytes = new ByteArrayOutputStream();
              try (InputStream in = context.getAssets().open("game_intro/catalog.json")) {
                byte[] b = new byte[4096];
                int n;
                while ((n = in.read(b)) != -1) bytes.write(b, 0, n);
              }
              content = GameJson.catalog(bytes.toString(StandardCharsets.UTF_8.name()));
              RoomProgressRepository r =
                  run != null
                      ? new RoomProgressRepository(
                          Room.databaseBuilder(
                                  context, GameProgressDatabase.class, "h04-debug-" + run + ".db")
                              .build())
                      : RoomProgressRepository.open(context);
              repo = r;
            }
            String[] spokenCaptions = captions;
            ProgressService svc =
                new ProgressService(
                    content,
                    repo,
                    () -> fake ? now : System.currentTimeMillis(),
                    new ProgressService.AttemptFactory() {
                      public String nextId() {
                        return UUID.randomUUID().toString();
                      }

                      public void shuffle(List<?> a) {
                        Collections.shuffle(a);
                      }
                    });
            IntroQuizController c =
                new IntroQuizController(
                    svc,
                    Progress.Mode.valueOf(mode),
                    integration == null ? IntegrationContext.TARGET : integration.targetId,
                    instant,
                    spokenCaptions.length,
                    worker,
                    main::post,
                    observer,
                    new IntroQuizController.Speech() {
                      public void speak(int index) {
                        speech.speak(spokenCaptions[index]);
                      }

                      public void stop() {
                        speech.stop();
                      }
                    },
                    () -> fake ? now : System.currentTimeMillis());
            main.post(
                () -> {
                  if (!closed) ready.accept(c);
                });
          } catch (Exception e) {
            main.post(
                () -> {
                  if (!closed) failed.run();
                });
          }
        });
  }

  public int speechGenerationForTest() {
    return speech == null ? -1 : speech.generationForTest();
  }

  public void speechErrorForTest(int token) {
    if (speech != null) speech.errorForTest(token);
  }

  public void remind(Progress p) {
    if (fake || run != null || p == null || p.introDoneAt == null) return;
    long due =
        p.instantDemo ? System.currentTimeMillis() : p.introDoneAt + ProgressService.WAIT_MILLIS;
    IntroQuizReminderReceiver.schedule(
        context, mode, "digital_information_software", Math.max(System.currentTimeMillis(), due));
  }

  public void close() {
    closed = true;
    stopTicks();
    if (speech != null) speech.close();
    worker.execute(
        () -> {
          if (repo != null) repo.close();
        });
    worker.shutdown();
  }
}
