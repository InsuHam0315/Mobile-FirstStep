package com.example.firststep.game.domain;

import java.util.*;

/** Pure policy over a transactional repository. Clock and random sources are injected. */
public final class ProgressService {
  public interface Clock {
    long nowMillis();
  }

  public interface AttemptFactory {
    String nextId();

    void shuffle(List<?> values);
  }

  public static final long WAIT_MILLIS = 15 * 60 * 1000L;
  private final GameContent content;
  private final ProgressRepository repo;
  private final Clock clock;
  private final AttemptFactory factory;

  public ProgressService(
      GameContent content, ProgressRepository repo, Clock clock, AttemptFactory factory) {
    this.content = Objects.requireNonNull(content);
    this.repo = Objects.requireNonNull(repo);
    this.clock = Objects.requireNonNull(clock);
    this.factory = Objects.requireNonNull(factory);
  }

  private GameContent.Building building(String id) {
    GameContent.Building b = content.buildings.get(id);
    if (b == null) throw new IllegalArgumentException("unknown building");
    return b;
  }

  public Progress read(Progress.Mode mode, String id) {
    building(id);
    return repo.read(mode, id);
  }

  public Progress intro(Progress.Mode mode, String id, boolean instantDemo) {
    building(id);
    Objects.requireNonNull(mode);
    if (instantDemo && mode != Progress.Mode.DEMO)
      throw new IllegalArgumentException("instant only in demo");
    return repo.atomicUpdate(
        mode,
        id,
        p ->
            p.introDoneAt != null
                ? p
                : new Progress(
                    mode, id, clock.nowMillis(), instantDemo, p.stamp, p.active, p.history));
  }

  public boolean eligible(Progress p) {
    long now = clock.nowMillis();
    return p.introDoneAt != null
        && (p.instantDemo
            || (now >= p.introDoneAt
                && p.introDoneAt <= Long.MAX_VALUE - WAIT_MILLIS
                && now >= p.introDoneAt + WAIT_MILLIS));
  }

  private Progress.Attempt fresh(GameContent.Building b, Progress p) {
    List<GameContent.Question> qs = new ArrayList<>();
    for (GameContent.Question q : b.questions) {
      List<GameContent.Option> os = new ArrayList<>(q.options);
      factory.shuffle(os);
      qs.add(new GameContent.Question(q.id, q.text, q.correctOptionId, os));
    }
    factory.shuffle(qs);
    String id = GameContent.id(factory.nextId());
    if (p.history.containsKey(id) || (p.active != null && p.active.id.equals(id)))
      throw new IllegalStateException("attempt ID reused");
    return new Progress.Attempt(id, content.version, qs);
  }

  public Progress start(Progress.Mode mode, String id) {
    GameContent.Building b = building(id);
    return repo.atomicUpdate(
        mode,
        id,
        p -> {
          if (p.stamp) throw new IllegalStateException("quiz unavailable");
          if (p.active != null) return p;
          if (!eligible(p)) throw new IllegalStateException("quiz unavailable");
          return new Progress(
              mode, id, p.introDoneAt, p.instantDemo, false, fresh(b, p), p.history);
        });
  }

  public Progress.Result submit(
      Progress.Mode mode,
      String id,
      String attemptId,
      String contentVersion,
      Map<String, String> answers) {
    GameContent.Building b = building(id);
    GameContent.id(attemptId);
    GameContent.id(contentVersion);
    Objects.requireNonNull(answers);
    Map<String, String> submitted = Collections.unmodifiableMap(new LinkedHashMap<>(answers));
    final Progress.Result[] out = new Progress.Result[1];
    repo.atomicUpdate(
        mode,
        id,
        p -> {
          Progress.Result prior = p.history.get(attemptId);
          if (prior != null) {
            if (!prior.contentVersion.equals(contentVersion) || !prior.answers.equals(submitted))
              throw new IllegalArgumentException("conflicting submission");
            out[0] = prior;
            return p;
          }
          if (p.stamp
              || p.active == null
              || !p.active.id.equals(attemptId)
              || !p.active.contentVersion.equals(contentVersion))
            throw new IllegalArgumentException("unknown attempt");
          if (submitted.size() != 3)
            throw new IllegalArgumentException("complete answers required");
          int score = 0;
          for (GameContent.Question q : p.active.questions) {
            String option = submitted.get(q.id);
            boolean found = false;
            for (GameContent.Option o : q.options) if (o.id.equals(option)) found = true;
            if (!found) throw new IllegalArgumentException("unknown answer");
            if (q.correctOptionId.equals(option)) score++;
          }
          Progress.Result result = new Progress.Result(attemptId, contentVersion, submitted, score);
          Map<String, Progress.Result> history = new LinkedHashMap<>(p.history);
          history.put(attemptId, result);
          Progress next =
              new Progress(mode, id, p.introDoneAt, p.instantDemo, result.passed, null, history);
          if (!result.passed)
            next =
                new Progress(
                    mode, id, p.introDoneAt, p.instantDemo, false, fresh(b, next), history);
          out[0] = result;
          return next;
        });
    return out[0];
  }

  public boolean endingAvailable(Progress.Mode mode) {
    Set<String> stamped = new HashSet<>();
    for (Progress p : repo.readAll(mode))
      if (p.mode == mode && p.stamp && content.buildings.containsKey(p.buildingId))
        stamped.add(p.buildingId);
    return stamped.size() == 5;
  }
}
