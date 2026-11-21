package com.example.firststep.game.content;

import java.util.*;

/** Registered narration: sentence identity and provenance are independent of presentation. */
public final class NarrationContent {
  public static final String DATASET_ID = "software_narration_v1";
  public static final String KOREAN_DATASET_ID = "korean_narration_v1";
  public static boolean isRegistered(String id) {
    return DATASET_ID.equals(id) || KOREAN_DATASET_ID.equals(id);
  }
  public enum Provenance { FICTIONAL, EDITORIAL, CLAIM }
  public static final class Sentence {
    public final String id, text, claimId;
    public final Provenance provenance;
    private Sentence(String id, String text, String claimId, Provenance provenance) {
      this.id = id; this.text = text; this.claimId = claimId; this.provenance = provenance;
    }
  }
  private final List<Sentence> sentences;
  private NarrationContent(List<Sentence> sentences) { this.sentences = Collections.unmodifiableList(sentences); }
  public List<Sentence> sentences() { return sentences; }
  public static NarrationContent decode(String registeredId, Map<String, Object> root) {
    if (!isRegistered(registeredId) || root == null || !root.keySet().equals(new HashSet<>(Arrays.asList("datasetId", "version", "sentences"))) || !registeredId.equals(root.get("datasetId")) || !Integer.valueOf(1).equals(root.get("version"))) throw new IllegalArgumentException("Invalid narration registration");
    String[] ids = {"greeting", "invitation", "college", "visit", "values", "resources", "freshness"};
    String[] types = {"fictional", "editorial", "claim", "claim", "claim", "claim", "editorial"};
    String[] claims = KOREAN_DATASET_ID.equals(registeredId)
        ? new String[] {"", "", "ko_college_claim", "ko_office_claim", "ko_study_claim", "ko_info_claim", ""}
        : new String[] {"", "", "college_membership", "office_guidance", "learning_values", "help_resources", ""};
    Object data = root.get("sentences");
    if (!(data instanceof List) || ((List<?>) data).size() != ids.length) throw new IllegalArgumentException("Invalid narration sentences");
    List<Sentence> result = new ArrayList<>();
    for (int i = 0; i < ids.length; i++) {
      Object item = ((List<?>) data).get(i);
      if (!(item instanceof Map)) throw new IllegalArgumentException("Invalid sentence");
      Map<?, ?> m = (Map<?, ?>) item;
      if (!m.keySet().equals(new HashSet<>(Arrays.asList("id", "provenance", "claimId", "text"))) || !ids[i].equals(m.get("id")) || !types[i].equals(m.get("provenance")) || !claims[i].equals(m.get("claimId")) || !(m.get("text") instanceof String)) throw new IllegalArgumentException("Invalid sentence provenance");
      String text = (String) m.get("text");
      if (text.trim().isEmpty() || text.length() > 300 || !text.equals(text.trim())) throw new IllegalArgumentException("Invalid caption");
      result.add(new Sentence(ids[i], text, claims[i], Provenance.valueOf(types[i].toUpperCase(Locale.ROOT))));
    }
    return new NarrationContent(result);
  }
}
