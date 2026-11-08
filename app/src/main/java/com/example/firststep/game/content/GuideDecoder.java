package com.example.firststep.game.content;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Strict decoder for the one registered, local guide. */
public final class GuideDecoder {
  public static final String DATASET_ID = "software_guide_v1";

  private GuideDecoder() {}

  private static final Map<String, String> SOURCES =
      registry(
          "sw_location", "https://www.kunsan.ac.kr/sw/index.kunsan?menuCd=DOM_000009601004000000",
          "sw_greeting", "https://www.kunsan.ac.kr/sw/index.kunsan?menuCd=DOM_000009601000000000",
          "sw_home", "https://www.kunsan.ac.kr/sw/index.kunsan",
          "college_home", "https://www.kunsan.ac.kr/ssw/index.kunsan");

  public static boolean isOfficialUrl(String url) {
    return url != null && SOURCES.containsValue(url);
  }

  private static final Map<String, String> CLAIMS =
      registry(
          "college_membership",
          "college_home",
          "office_guidance",
          "sw_location",
          "learning_values",
          "sw_greeting",
          "help_resources",
          "sw_home");
  private static final Map<String, String> QUESTIONS =
      registry(
          "location", "office_guidance", "values", "learning_values", "notice", "help_resources");
  private static final Map<String, String> ANSWERS =
      registry("location", "digital_first", "values", "communicate", "notice", "official");
  private static final Map<String, String> OPTIONS =
      registry(
          "location",
          "digital_first,library,student_center",
          "values",
          "communicate,memorize,isolate",
          "notice",
          "official,old_capture,rumor");

  private static Map<String, String> registry(String... entries) {
    Map<String, String> m = new HashMap<>();
    for (int i = 0; i < entries.length; i += 2) m.put(entries[i], entries[i + 1]);
    return Collections.unmodifiableMap(m);
  }

  private static IllegalArgumentException bad() {
    return new IllegalArgumentException("Invalid guide content");
  }

  private static Map<?, ?> obj(Object o, String... keys) {
    if (!(o instanceof Map)) throw bad();
    Map<?, ?> m = (Map<?, ?>) o;
    if (!m.keySet().equals(new HashSet<>(Arrays.asList(keys)))) throw bad();
    return m;
  }

  private static String str(Map<?, ?> m, String key) {
    Object o = m.get(key);
    if (!(o instanceof String)) throw bad();
    String s = (String) o;
    if (s.isEmpty() || s.length() > 1000 || !s.equals(s.trim())) throw bad();
    boolean hasVisibleCharacter = false;
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (!Character.isWhitespace(c) && !Character.isSpaceChar(c)) {
        hasVisibleCharacter = true;
        break;
      }
    }
    if (!hasVisibleCharacter) throw bad();
    return s;
  }

  private static List<?> list(Map<?, ?> m, String key, int size) {
    Object o = m.get(key);
    if (!(o instanceof List) || ((List<?>) o).size() != size) throw bad();
    return (List<?>) o;
  }

  private static void exact(String actual, String expected) {
    if (!actual.equals(expected)) throw bad();
  }

  private static void unique(Set<String> ids, String id) {
    if (!ids.add(id)) throw bad();
  }

  public static GuideBundle decode(String datasetId, Map<String, Object> raw) {
    if (!DATASET_ID.equals(datasetId)) throw bad();
    Map<?, ?> root =
        obj(
            raw,
            "datasetId",
            "targetId",
            "version",
            "contentVerification",
            "arrivalVerification",
            "sources",
            "claims",
            "questions");
    exact(str(root, "datasetId"), DATASET_ID);
    exact(str(root, "targetId"), "digital_information_software");
    if (!(root.get("version") instanceof Integer) || ((Integer) root.get("version")) != 1)
      throw bad();
    exact(str(root, "contentVerification"), "official_pages_checked");
    exact(str(root, "arrivalVerification"), "unverified");
    Set<String> ids = new HashSet<>();
    List<GuideBundle.Source> sources = new ArrayList<>();
    for (Object item : list(root, "sources", 4)) {
      Map<?, ?> m = obj(item, "id", "label", "url", "checkedDate");
      String id = str(m, "id"), url = str(m, "url");
      unique(ids, id);
      if (!url.equals(SOURCES.get(id))) throw bad();
      exact(str(m, "checkedDate"), "2026-11-08");
      sources.add(new GuideBundle.Source(id, str(m, "label"), url, str(m, "checkedDate")));
    }
    ids.clear();
    List<GuideBundle.Claim> claims = new ArrayList<>();
    for (Object item : list(root, "claims", 4)) {
      Map<?, ?> m = obj(item, "id", "sourceId", "text");
      String id = str(m, "id"), source = str(m, "sourceId");
      unique(ids, id);
      if (!source.equals(CLAIMS.get(id))) throw bad();
      claims.add(new GuideBundle.Claim(id, source, str(m, "text")));
    }
    ids.clear();
    List<GuideBundle.Question> questions = new ArrayList<>();
    for (Object item : list(root, "questions", 3)) {
      Map<?, ?> m = obj(item, "id", "claimId", "text", "options", "answerId", "explanation");
      String id = str(m, "id"), claim = str(m, "claimId"), answer = str(m, "answerId");
      unique(ids, id);
      if (!claim.equals(QUESTIONS.get(id)) || !answer.equals(ANSWERS.get(id))) throw bad();
      Set<String> optionIds = new HashSet<>();
      List<GuideBundle.Option> options = new ArrayList<>();
      for (Object x : list(m, "options", 3)) {
        Map<?, ?> option = obj(x, "id", "text");
        String optionId = str(option, "id");
        unique(optionIds, optionId);
        options.add(new GuideBundle.Option(optionId, str(option, "text")));
      }
      if (!optionIds.equals(new HashSet<>(Arrays.asList(OPTIONS.get(id).split(","))))) throw bad();
      questions.add(
          new GuideBundle.Question(
              id, claim, str(m, "text"), options, answer, str(m, "explanation")));
    }
    return new GuideBundle(
        DATASET_ID,
        "digital_information_software",
        1,
        "official_pages_checked",
        "unverified",
        sources,
        claims,
        questions);
  }
}
