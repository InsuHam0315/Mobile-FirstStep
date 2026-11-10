package com.example.firststep.game.composition.home;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import androidx.room.Room;
import com.example.firststep.facility.*;
import com.example.firststep.game.composition.integration.IntegrationComposition;
import com.example.firststep.game.composition.integration.IntegrationContext;
import com.example.firststep.game.data.local.*;
import com.example.firststep.game.domain.*;
import com.example.firststep.game.presentation.home.HomeController;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Owns blocking adapters and serializes all reads and desired-state favorite writes. */
public final class HomeHost implements HomeController.Port, AutoCloseable {
  public static final String TARGET = "digital_information_software";
  private final Context context;
  private final String run;
  private final IntegrationContext integration;
  private final boolean softwareDemo;
  private final boolean fake;
  private volatile long fakeNow;
  private volatile boolean closed;
  private static final ExecutorService FAVORITES = Executors.newSingleThreadExecutor();
  private static FavoriteCoordinator sharedFavorites;

  private static synchronized FavoriteCoordinator favorites(FacilityRepository repository) {
    if (sharedFavorites == null)
      sharedFavorites =
          new FavoriteCoordinator(
              FAVORITES,
              new FavoriteCoordinator.Store() {
                public Set<String> read() {
                  return repository.favorites();
                }

                public void set(String id, boolean desired) {
                  repository.setFavorite(id, desired);
                }
              });
    return sharedFavorites;
  }

  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private final Handler main = new Handler(Looper.getMainLooper());
  private RoomProgressRepository repo;
  private ProgressService service;
  private final FacilityRepository facilities;
  private final FavoriteCoordinator favoriteCoordinator;
  private HomeController controller;

  public HomeHost(
      Context context, String run, boolean fake, long now, IntegrationContext integration) {
    this.context = context.getApplicationContext();
    this.run = run;
    this.fake = fake;
    fakeNow = now;
    this.integration = integration;
    softwareDemo =
        integration != null && IntegrationContext.SOFTWARE_DATASET.equals(integration.datasetId);
    facilities = softwareDemo ? null : new FacilityRepository(context);
    favoriteCoordinator = softwareDemo ? null : favorites(facilities);
  }

  public void attach(HomeController c) {
    controller = c;
  }

  public long now() {
    return fake ? fakeNow : System.currentTimeMillis();
  }

  public void setFakeNow(long value) {
    if (fake) fakeNow = value;
  }

  private void initialize() throws Exception {
    if (service != null) return;
    GameContent content;
    if (integration != null) {
      IntegrationComposition composition = IntegrationComposition.open(context, integration);
      content = composition.content;
      repo = composition.repository;
    } else {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      try (InputStream in = context.getAssets().open("game_intro/catalog.json")) {
        byte[] b = new byte[4096];
        int n;
        while ((n = in.read(b)) != -1) bytes.write(b, 0, n);
      }
      content = GameJson.catalog(bytes.toString(StandardCharsets.UTF_8.name()));
      if (!content.buildings.containsKey(TARGET)) throw new IllegalStateException("missing target");
      repo =
          run == null
              ? RoomProgressRepository.open(context)
              : new RoomProgressRepository(
                  Room.databaseBuilder(
                          context, GameProgressDatabase.class, "h04-debug-" + run + ".db")
                      .build());
    }
    service =
        new ProgressService(
            content,
            repo,
            this::now,
            new ProgressService.AttemptFactory() {
              public String nextId() {
                return UUID.randomUUID().toString();
              }

              public void shuffle(List<?> list) {
                Collections.shuffle(list);
              }
            });
  }

  @Override
  public void load(
      long generation, Progress.Mode mode, String query, String category, boolean only) {
    if (softwareDemo) {
      loadSoftwareDemo(generation, mode);
      return;
    }
    favoriteCoordinator.read(
        favoriteSnapshot -> {
          if (closed) return;
          if (favoriteSnapshot == null) {
            main.post(
                () -> {
                  if (!closed) controller.failed(generation);
                });
            return;
          }
          try {
            worker.execute(
                () -> {
                  try {
                    initialize();
                    List<Facility> all = facilities.load();
                    List<Facility> filtered =
                        FacilitySearch.filter(all, query, category, only, favoriteSnapshot.ids);
                    Progress p = service.read(mode, TARGET);
                    HomeController.Data data =
                        new HomeController.Data(
                            all,
                            filtered,
                            favoriteSnapshot.ids,
                            p,
                            service.eligible(p),
                            service.endingAvailable(mode),
                            now(),
                            favoriteSnapshot.failedFavorites);
                    main.post(
                        () -> {
                          if (!closed) {
                            if (favoriteSnapshot.revision != favoriteCoordinator.revision())
                              load(generation, mode, query, category, only);
                            else controller.loaded(generation, data);
                          }
                        });
                  } catch (Exception failure) {
                    main.post(
                        () -> {
                          if (!closed) controller.failed(generation);
                        });
                  }
                });
          } catch (RejectedExecutionException closedDuringDispatch) {
            // Lifecycle closure owns the worker; no result belongs to a closed host.
          }
        });
  }

  private void loadSoftwareDemo(long generation, Progress.Mode mode) {
    worker.execute(
        () -> {
          try {
            if (mode != integration.mode) throw new IllegalArgumentException("demo mode");
            initialize();
            Progress progress = service.read(mode, integration.targetId);
            HomeController.Data data =
                new HomeController.Data(
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptySet(),
                    progress,
                    service.eligible(progress),
                    false,
                    now(),
                    Collections.emptyMap());
            main.post(
                () -> {
                  if (!closed) controller.loaded(generation, data);
                });
          } catch (Exception failure) {
            main.post(
                () -> {
                  if (!closed) controller.failed(generation);
                });
          }
        });
  }

  @Override
  public void setFavorite(
      String id, boolean desired, java.util.function.Consumer<Boolean> complete) {
    if (softwareDemo) {
      complete.accept(false);
      return;
    }
    favoriteCoordinator.set(
        id,
        desired,
        ok ->
            main.post(
                () -> {
                  if (!closed) complete.accept(ok);
                }));
  }

  /** Recompute against the last authoritative progress without reading the database. */
  public void tick(Progress progress) {
    if (progress == null) return;
    worker.execute(
        () -> {
          if (service == null) return;
          long time = now();
          boolean eligible = service.eligible(progress);
          main.post(
              () -> {
                if (!closed && controller.snapshot().progress == progress)
                  controller.tick(time, eligible);
              });
        });
  }

  @Override
  public void close() {
    closed = true;
    worker.execute(
        () -> {
          if (repo != null) repo.close();
        });
    worker.shutdown();
  }
}
