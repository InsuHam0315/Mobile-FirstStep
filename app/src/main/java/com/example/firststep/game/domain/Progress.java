package com.example.firststep.game.domain;

import java.util.*;

/** Durable immutable aggregate; selected answers are persisted only on full submission. */
public final class Progress {
  public enum Mode {
    NORMAL,
    DEMO
  }

  public final Mode mode;
  public final String buildingId;
  public final Long introDoneAt;
  public final boolean instantDemo, stamp;
  public final Attempt active;
  public final Map<String, Result> history;

  public Progress(
      Mode mode,
      String buildingId,
      Long introDoneAt,
      boolean instantDemo,
      boolean stamp,
      Attempt active,
      Map<String, Result> history) {
    this.mode = Objects.requireNonNull(mode);
    this.buildingId = GameContent.id(buildingId);
    this.introDoneAt = introDoneAt;
    if (instantDemo && mode != Mode.DEMO)
      throw new IllegalArgumentException("instant only in demo");
    this.instantDemo = instantDemo;
    this.stamp = stamp;
    this.active = active;
    Objects.requireNonNull(history);
    boolean passed = false;
    for (Map.Entry<String, Result> e : history.entrySet()) {
      if (e.getValue() == null || !e.getKey().equals(e.getValue().attemptId))
        throw new IllegalArgumentException("history key mismatch");
      if (e.getValue().passed) {
        if (passed) throw new IllegalArgumentException("multiple passes");
        passed = true;
      }
    }
    if ((active != null || !history.isEmpty() || stamp) && introDoneAt == null)
      throw new IllegalArgumentException("missing intro");
    if (active != null && (stamp || history.containsKey(active.id)))
      throw new IllegalArgumentException("invalid active attempt");
    if (stamp != passed) throw new IllegalArgumentException("stamp/result mismatch");
    this.history = Collections.unmodifiableMap(new LinkedHashMap<>(history));
  }

  public static Progress empty(Mode mode, String building) {
    return new Progress(mode, building, null, false, false, null, Collections.emptyMap());
  }

  public static final class Attempt {
    public final String id, contentVersion;
    public final List<GameContent.Question> questions;

    public Attempt(String id, String version, List<GameContent.Question> questions) {
      this.id = GameContent.id(id);
      this.contentVersion = GameContent.id(version);
      if (questions.size() != 3) throw new IllegalArgumentException("three questions required");
      Set<String> ids = new HashSet<>();
      for (GameContent.Question q : questions)
        if (!ids.add(q.id)) throw new IllegalArgumentException("duplicate question");
      this.questions = Collections.unmodifiableList(new ArrayList<>(questions));
    }
  }

  public static final class Result {
    public final String attemptId, contentVersion;
    public final Map<String, String> answers;
    public final int correct;
    public final boolean passed;

    public Result(String id, String contentVersion, Map<String, String> answers, int correct) {
      this.attemptId = GameContent.id(id);
      this.contentVersion = GameContent.id(contentVersion);
      Objects.requireNonNull(answers);
      for (Map.Entry<String, String> e : answers.entrySet()) {
        GameContent.id(e.getKey());
        GameContent.id(e.getValue());
      }
      this.answers = Collections.unmodifiableMap(new LinkedHashMap<>(answers));
      if (correct < 0 || correct > 3 || answers.size() != 3)
        throw new IllegalArgumentException("invalid result");
      this.correct = correct;
      this.passed = correct >= 2;
    }
  }
}
