package com.example.firststep.game.composition.authored;

import android.content.Context;
import com.example.firststep.game.composition.identity.AccountProgressAccess;
import com.example.firststep.game.composition.identity.FirebaseIdentityRuntime;
import com.example.firststep.game.content.AssetGuideRepository;
import com.example.firststep.game.content.GuideBundle;
import com.example.firststep.game.content.NarrationAsset;
import com.example.firststep.game.content.NarrationContent;
import com.example.firststep.game.domain.GameContent;
import com.example.firststep.game.domain.Progress;
import com.example.firststep.game.domain.ProgressService;
import com.example.firststep.game.sync.CommittedMilestoneSource;
import com.example.firststep.game.sync.DatasetRegistry;
import com.example.firststep.game.sync.OutboxState;
import com.example.firststep.game.sync.SessionOwner;
import com.example.firststep.game.sync.SyncScope;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Account-scoped NORMAL episode. All methods except rendering callbacks run off the UI thread. */
public final class AuthoredEpisodeHost implements AutoCloseable {
  public static final String DATASET = "software_authored_v1";
  public static final String TARGET = "digital_information_software";

  public enum Arrival { UNAVAILABLE, NOT_ARRIVED, ADMITTED }
  public enum Block { NO_ACCOUNT, UNREGISTERED, ARRIVAL_UNAVAILABLE, NOT_ARRIVED, INVALID_CONTENT }

  /** A trusted provider must supply fresh surveyed arrival evidence. */
  interface ArrivalGate { Arrival check(SyncScope scope); }
  interface Access {
    SyncScope capture();
    DatasetRegistry registry();
    SessionOwner owner();
    CompletableFuture<OutboxState> reconcile(CommittedMilestoneSource source, SyncScope scope);
    CompletableFuture<OutboxState> flush(SyncScope scope);
  }
  interface ContentLoader { Content load() throws Exception; }

  static final class Content {
    final GuideBundle guide;
    final NarrationContent captions;
    final GameContent game;
    Content(GuideBundle guide, NarrationContent captions, GameContent game) {
      this.guide = guide;
      this.captions = captions;
      this.game = Objects.requireNonNull(game);
    }
  }

  public static final class OpenResult {
    public final AuthoredEpisodeHost host;
    public final Block reason;
    private OpenResult(AuthoredEpisodeHost host, Block reason) {
      this.host = host;
      this.reason = reason;
    }
    private static OpenResult blocked(Block reason) { return new OpenResult(null, reason); }
  }

  private final Access access;
  private final SyncScope scope;
  private final ArrivalGate arrival;
  private final AutoCloseable storeClose;
  private final AuthoredProgressRepository repository;
  private final ProgressService service;
  private final Content content;
  private CompletableFuture<OutboxState> reconciliation;
  private volatile boolean closed;
  private int pending;

  private AuthoredEpisodeHost(Access access, SyncScope scope, ArrivalGate arrival,
      AutoCloseable storeClose, AuthoredProgressRepository repository,
      ProgressService service, Content content) {
    this.access = access;
    this.scope = scope;
    this.arrival = arrival;
    this.storeClose = storeClose;
    this.repository = repository;
    this.service = service;
    this.content = content;
  }

  /** Opens only the fixed software target. No external argument can select a registry or arrival. */
  public static OpenResult openProduction(Context context) {
    return productionGate(() -> openConfigured(context));
  }

  /** Check the closed production registration before touching the Android or identity graph. */
  static OpenResult productionGate(Supplier<OpenResult> whenRegistered) {
    try {
      SyncScope eligibility = new SyncScope("registration-check", "registration-check", 1,
          DATASET, Progress.Mode.NORMAL, TARGET);
      DatasetRegistry.production().requireEligible(eligibility);
    } catch (IllegalArgumentException ineligible) {
      return OpenResult.blocked(Block.UNREGISTERED);
    }
    return whenRegistered.get();
  }

  private static OpenResult openConfigured(Context context) {
    Context app = context.getApplicationContext();
    Optional<AccountProgressAccess> configured = FirebaseIdentityRuntime.get(app).accountProgress();
    if (!configured.isPresent()) return OpenResult.blocked(Block.NO_ACCOUNT);
    AccountProgressAccess bound = configured.get();
    Access access = new Access() {
      @Override public SyncScope capture() { return bound.capture(DATASET, TARGET); }
      @Override public DatasetRegistry registry() { return bound.registry(); }
      @Override public SessionOwner owner() { return bound.owner(); }
      @Override public CompletableFuture<OutboxState> reconcile(
          CommittedMilestoneSource source, SyncScope scope) {
        return bound.reconciler(source).reconcile(scope);
      }
      @Override public CompletableFuture<OutboxState> flush(SyncScope scope) {
        return bound.flush(scope);
      }
    };
    return open(access, scope -> Arrival.UNAVAILABLE,
        () -> new SQLiteAuthoredProgressStore(app, access.registry()),
        () -> loadApprovedContent(app), System::currentTimeMillis, new ProgressService.AttemptFactory() {
          @Override public String nextId() { return UUID.randomUUID().toString(); }
          @Override public void shuffle(List<?> values) { Collections.shuffle(values); }
        });
  }

  static OpenResult open(Access access, ArrivalGate gate, Supplier<AuthoredProgressStore> storeFactory,
      ContentLoader loader, ProgressService.Clock clock, ProgressService.AttemptFactory attempts) {
    if (access == null) return OpenResult.blocked(Block.NO_ACCOUNT);
    final SyncScope scope;
    try {
      scope = access.capture();
      if (scope.mode != Progress.Mode.NORMAL || !DATASET.equals(scope.datasetId)
          || !TARGET.equals(scope.buildingId)) return OpenResult.blocked(Block.UNREGISTERED);
      access.registry().requireEligible(scope);
      access.owner().whileCurrent(scope, () -> null);
    } catch (SessionOwner.StaleSession stale) {
      return OpenResult.blocked(Block.NO_ACCOUNT);
    } catch (RuntimeException unregistered) {
      return OpenResult.blocked(Block.UNREGISTERED);
    }
    final Arrival admission;
    try {
      admission = access.owner().whileCurrent(scope, () -> gate.check(scope));
    } catch (SessionOwner.StaleSession stale) {
      return OpenResult.blocked(Block.NO_ACCOUNT);
    } catch (RuntimeException unavailable) {
      return OpenResult.blocked(Block.ARRIVAL_UNAVAILABLE);
    }
    if (admission != Arrival.ADMITTED) {
      return OpenResult.blocked(admission == Arrival.NOT_ARRIVED ? Block.NOT_ARRIVED : Block.ARRIVAL_UNAVAILABLE);
    }
    try {
      Content content = loader.load();
      if (!DATASET.equals(content.game.version) || !content.game.buildings.containsKey(TARGET)) {
        return OpenResult.blocked(Block.INVALID_CONTENT);
      }
      AuthoredEpisodeHost host = access.owner().whileCurrent(scope, () -> {
        AuthoredProgressStore store = storeFactory.get();
        AutoCloseable close = store instanceof AutoCloseable ? (AutoCloseable) store : () -> {};
        try {
          AuthoredProgressRepository repository = new AuthoredProgressRepository(
              access.registry(), access.owner(), scope, store, clock);
          return new AuthoredEpisodeHost(access, scope, gate, close, repository,
              new ProgressService(content.game, repository, clock, attempts), content);
        } catch (RuntimeException failure) {
          try { close.close(); } catch (Exception ignored) { }
          throw failure;
        }
      });
      host.scheduleReconciliation(); // Reopen recovers a commit made before outbox enqueue.
      return new OpenResult(host, null);
    } catch (SessionOwner.StaleSession stale) {
      return OpenResult.blocked(Block.NO_ACCOUNT);
    } catch (Exception failure) {
      return OpenResult.blocked(Block.INVALID_CONTENT);
    }
  }

  private static Content loadApprovedContent(Context context) throws Exception {
    GuideBundle guide = new AssetGuideRepository(context).load();
    NarrationContent narration = NarrationAsset.load(context);
    if (!TARGET.equals(guide.getTargetId())) throw new IllegalArgumentException("target");
    java.util.Set<String> claims = new java.util.HashSet<>();
    for (GuideBundle.Claim claim : guide.getClaims()) claims.add(claim.getId());
    for (NarrationContent.Sentence sentence : narration.sentences()) {
      if (sentence.provenance == NarrationContent.Provenance.CLAIM
          && !claims.contains(sentence.claimId)) throw new IllegalArgumentException("claim");
    }
    List<GameContent.Question> questions = new ArrayList<>();
    for (GuideBundle.Question q : guide.getQuestions()) {
      List<GameContent.Option> options = new ArrayList<>();
      for (GuideBundle.Option option : q.getOptions()) {
        options.add(new GameContent.Option(option.getId(), option.getText()));
      }
      questions.add(new GameContent.Question(q.getId(), q.getText(), q.getAnswerId(), options));
    }
    return new Content(guide, narration, new GameContent(DATASET, Collections.singletonList(
        new GameContent.Building(TARGET, "소프트웨어학과", questions))));
  }

  public GuideBundle guide() { return content.guide; }
  public NarrationContent captions() { return content.captions; }

  private void requireCurrent() {
    if (closed) throw new IllegalStateException("Closed episode");
    access.registry().requireEligible(scope);
    access.owner().whileCurrent(scope, () -> null);
  }

  private <T> T withArrival(Supplier<T> action) {
    requireCurrent();
    return access.owner().whileCurrent(scope, () -> {
      if (arrival.check(scope) != Arrival.ADMITTED) {
        throw new IllegalStateException("Arrival unavailable or no longer current");
      }
      return action.get();
    });
  }

  public synchronized Progress read() { requireCurrent(); return service.read(Progress.Mode.NORMAL, TARGET); }
  public synchronized boolean eligible(Progress progress) { requireCurrent(); return service.eligible(progress); }
  public synchronized Progress finishIntroduction() {
    Progress saved = withArrival(() -> service.intro(Progress.Mode.NORMAL, TARGET, false));
    scheduleReconciliation();
    return saved;
  }
  public synchronized Progress startQuiz() {
    return withArrival(() -> service.start(Progress.Mode.NORMAL, TARGET));
  }
  public synchronized Progress.Result submit(String attemptId, String contentVersion,
      Map<String, String> answers) {
    Progress.Result result = withArrival(
        () -> service.submit(Progress.Mode.NORMAL, TARGET, attemptId, contentVersion, answers));
    if (result.passed) scheduleReconciliation();
    return result;
  }

  /** Run only brief UI state publication in this owner-serialized callback. */
  public boolean renderIfCurrent(Runnable render) {
    if (closed) return false;
    try {
      access.registry().requireEligible(scope);
      return access.owner().whileCurrent(scope, () -> {
        if (closed) return false;
        render.run();
        return true;
      });
    } catch (SessionOwner.StaleSession stale) {
      return false;
    }
  }

  public synchronized boolean isCurrent() {
    try { requireCurrent(); return true; }
    catch (IllegalStateException | IllegalArgumentException stale) { return false; }
  }

  /** Retry only local reconciliation. Does not contact the server. */
  public synchronized CompletableFuture<OutboxState> reconcile() {
    requireCurrent();
    return scheduleReconciliation();
  }

  public synchronized CompletableFuture<OutboxState> reconciliation() { return reconciliation; }

  /** Explicit network operation; caller should first reconcile successfully. */
  public synchronized CompletableFuture<OutboxState> flush() {
    requireCurrent();
    try {
      return track(access.flush(scope).thenApply(result -> {
        access.owner().whileCurrent(scope, () -> null);
        return result;
      }));
    } catch (RuntimeException failure) {
      return failed(failure);
    }
  }

  private synchronized CompletableFuture<OutboxState> scheduleReconciliation() {
    try { reconciliation = track(access.reconcile(repository, scope)); }
    catch (RuntimeException failure) { reconciliation = failed(failure); }
    return reconciliation;
  }

  private static <T> CompletableFuture<T> failed(Throwable failure) {
    CompletableFuture<T> future = new CompletableFuture<>();
    future.completeExceptionally(failure);
    return future;
  }

  private synchronized <T> CompletableFuture<T> track(CompletableFuture<T> future) {
    Objects.requireNonNull(future);
    pending++;
    future.whenComplete((value, error) -> release());
    return future;
  }

  private synchronized void release() {
    pending--;
    if (closed && pending == 0) closeStore();
  }

  @Override public synchronized void close() {
    if (closed) return;
    closed = true;
    if (pending == 0) closeStore();
  }

  private void closeStore() {
    try { storeClose.close(); }
    catch (Exception ignored) { /* No operation can use this closed host. */ }
  }
}
