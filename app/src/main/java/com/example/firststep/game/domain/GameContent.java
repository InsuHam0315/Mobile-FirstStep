package com.example.firststep.game.domain;

import java.util.*;

/** Immutable, versioned content. IDs, rather than labels or positions, are authoritative. */
public final class GameContent {
  public final String version;
  public final Map<String, Building> buildings;

  public GameContent(String version, List<Building> buildings) {
    this.version = id(version);
    Map<String, Building> m = new LinkedHashMap<>();
    for (Building b : buildings)
      if (m.put(b.id, b) != null) throw new IllegalArgumentException("duplicate building");
    if (m.isEmpty() || m.size() > 5)
      throw new IllegalArgumentException("one to five buildings required");
    this.buildings = Collections.unmodifiableMap(m);
  }

  public static String id(String s) {
    if (s == null || s.trim().isEmpty()) throw new IllegalArgumentException("empty ID");
    return s;
  }

  public static final class Building {
    public final String id, label;
    public final List<Question> questions;

    public Building(String id, String label, List<Question> questions) {
      this.id = GameContent.id(id);
      this.label = GameContent.id(label);
      if (questions.size() != 3) throw new IllegalArgumentException("three questions required");
      Set<String> ids = new HashSet<>();
      for (Question q : questions)
        if (!ids.add(q.id)) throw new IllegalArgumentException("duplicate question");
      this.questions = Collections.unmodifiableList(new ArrayList<>(questions));
    }
  }

  public static final class Option {
    public final String id, text;

    public Option(String id, String text) {
      this.id = GameContent.id(id);
      this.text = GameContent.id(text);
    }
  }

  public static final class Question {
    public final String id, text, correctOptionId;
    public final List<Option> options;

    public Question(String id, String text, String correctOptionId, List<Option> options) {
      this.id = GameContent.id(id);
      this.text = GameContent.id(text);
      this.correctOptionId = GameContent.id(correctOptionId);
      Set<String> ids = new HashSet<>();
      for (Option o : options)
        if (!ids.add(o.id)) throw new IllegalArgumentException("duplicate option");
      if (ids.size() < 2 || !ids.contains(correctOptionId))
        throw new IllegalArgumentException("invalid answer");
      this.options = Collections.unmodifiableList(new ArrayList<>(options));
    }
  }
}
