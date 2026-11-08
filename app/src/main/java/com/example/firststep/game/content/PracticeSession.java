package com.example.firststep.game.content;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** In-memory practice only; no progress or rewards. */
public final class PracticeSession {
  private final GuideBundle bundle;
  private final Map<String, String> answers = new LinkedHashMap<>();
  private boolean submitted;

  public PracticeSession(GuideBundle bundle) {
    if (bundle == null) throw new IllegalArgumentException("Missing guide");
    this.bundle = bundle;
  }

  private GuideBundle.Question question(String id) {
    for (GuideBundle.Question q : bundle.getQuestions()) if (q.getId().equals(id)) return q;
    throw new IllegalArgumentException("Unknown question");
  }

  public void select(String questionId, String optionId) {
    if (submitted) throw new IllegalStateException("Already submitted");
    GuideBundle.Question q = question(questionId);
    for (GuideBundle.Option o : q.getOptions())
      if (o.getId().equals(optionId)) {
        answers.put(questionId, optionId);
        return;
      }
    throw new IllegalArgumentException("Unknown option");
  }

  public void submit() {
    if (submitted) throw new IllegalStateException("Already submitted");
    if (answers.size() != bundle.getQuestions().size())
      throw new IllegalArgumentException("Answers incomplete");
    submitted = true;
  }

  public void retry() {
    answers.clear();
    submitted = false;
  }

  public Map<String, String> getAnswers() {
    return Collections.unmodifiableMap(new LinkedHashMap<>(answers));
  }

  public boolean isSubmitted() {
    return submitted;
  }

  public boolean isCorrect(String questionId) {
    if (!submitted) throw new IllegalStateException("Not submitted");
    GuideBundle.Question q = question(questionId);
    return q.getAnswerId().equals(answers.get(questionId));
  }

  public int getScore() {
    if (!submitted) throw new IllegalStateException("Not submitted");
    int score = 0;
    for (GuideBundle.Question q : bundle.getQuestions()) if (isCorrect(q.getId())) score++;
    return score;
  }

  public Snapshot snapshot() {
    return new Snapshot(getAnswers(), submitted);
  }

  public static PracticeSession restore(
      GuideBundle bundle, Map<String, String> answers, boolean submitted) {
    if (answers == null) throw new IllegalArgumentException("Missing answers");
    PracticeSession session = new PracticeSession(bundle);
    for (Map.Entry<String, String> answer : answers.entrySet())
      session.select(answer.getKey(), answer.getValue());
    if (submitted) session.submit();
    return session;
  }

  public static final class Snapshot {
    private final Map<String, String> answers;
    private final boolean submitted;

    private Snapshot(Map<String, String> answers, boolean submitted) {
      this.answers = answers;
      this.submitted = submitted;
    }

    public Map<String, String> getAnswers() {
      return answers;
    }

    public boolean isSubmitted() {
      return submitted;
    }
  }
}
