package com.example.firststep.game.content;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class GuideDomainTest {
  private static Map<String, Object> m(Object... values) {
    Map<String, Object> r = new LinkedHashMap<>();
    for (int i = 0; i < values.length; i += 2) r.put((String) values[i], values[i + 1]);
    return r;
  }

  private static Map<String, Object> source(String id, String url) {
    return m("id", id, "label", "label", "url", url, "checkedDate", "2026-11-08");
  }

  private static Map<String, Object> claim(String id, String source) {
    return m("id", id, "sourceId", source, "text", "text");
  }

  private static Map<String, Object> q(String id, String claim, String answer, String... opts) {
    List<Object> options = new ArrayList<>();
    for (String o : opts) options.add(m("id", o, "text", o));
    return m(
        "id",
        id,
        "claimId",
        claim,
        "text",
        "question",
        "options",
        options,
        "answerId",
        answer,
        "explanation",
        "explanation");
  }

  private static Map<String, Object> valid() {
    return m(
        "datasetId",
        "software_guide_v1",
        "targetId",
        "digital_information_software",
        "version",
        1,
        "contentVerification",
        "official_pages_checked",
        "arrivalVerification",
        "unverified",
        "sources",
        new ArrayList<>(
            Arrays.asList(
                source(
                    "sw_location",
                    "https://www.kunsan.ac.kr/sw/index.kunsan?menuCd=DOM_000009601004000000"),
                source(
                    "sw_greeting",
                    "https://www.kunsan.ac.kr/sw/index.kunsan?menuCd=DOM_000009601000000000"),
                source("sw_home", "https://www.kunsan.ac.kr/sw/index.kunsan"),
                source("college_home", "https://www.kunsan.ac.kr/ssw/index.kunsan"))),
        "claims",
        new ArrayList<>(
            Arrays.asList(
                claim("college_membership", "college_home"),
                claim("office_guidance", "sw_location"),
                claim("learning_values", "sw_greeting"),
                claim("help_resources", "sw_home"))),
        "questions",
        new ArrayList<>(
            Arrays.asList(
                q(
                    "location",
                    "office_guidance",
                    "digital_first",
                    "digital_first",
                    "library",
                    "student_center"),
                q("values", "learning_values", "communicate", "communicate", "memorize", "isolate"),
                q("notice", "help_resources", "official", "official", "old_capture", "rumor"))));
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> row(Map<String, Object> root, String key, int index) {
    return (Map<String, Object>) ((List<?>) root.get(key)).get(index);
  }

  private static void rejected(Map<String, Object> root) {
    try {
      GuideDecoder.decode(GuideDecoder.DATASET_ID, root);
      fail("accepted invalid content");
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void rejectsMalformedAndUnsupported() {
    for (Object v : Arrays.asList(1L, 1.0, "1", true, 2)) {
      Map<String, Object> x = valid();
      x.put("version", v);
      rejected(x);
    }
    Map<String, Object> x = valid();
    x.put("arrivalVerification", "verified");
    rejected(x);
    x = valid();
    x.put("coordinates", null);
    rejected(x);
    x = valid();
    x.put("contentVerification", "unknown");
    rejected(x);
    try {
      GuideDecoder.decode("unknown", valid());
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void rejectsProvenanceDuplicatesAndAnswers() {
    Map<String, Object> x = valid();
    row(x, "claims", 1).put("sourceId", "sw_home");
    rejected(x);
    x = valid();
    row(x, "questions", 0).put("claimId", "help_resources");
    rejected(x);
    x = valid();
    row(x, "questions", 0).put("answerId", "library");
    rejected(x);
    x = valid();
    row(x, "claims", 0).put("id", "office_guidance");
    rejected(x);
    x = valid();
    row(x, "questions", 0)
        .put(
            "options",
            Arrays.asList(
                m("id", "digital_first", "text", "x"),
                m("id", "digital_first", "text", "x"),
                m("id", "library", "text", "x")));
    rejected(x);
  }

  @Test
  public void rejectsUrlLookalikes() {
    for (String u :
        Arrays.asList(
            "https://www.kunsan.ac.kr:443/sw/index.kunsan",
            "https://www.kunsan.ac.kr@evil.example/sw/index.kunsan",
            "https://www.kunsan.ac.kr/sw/index.kunsan#x",
            "https://www.kunsan.ac.kr.evil.example/sw/index.kunsan")) {
      Map<String, Object> x = valid();
      row(x, "sources", 2).put("url", u);
      rejected(x);
    }
  }

  @Test
  public void officialUrlRegistryIsExact() {
    assertTrue(GuideDecoder.isOfficialUrl("https://www.kunsan.ac.kr/sw/index.kunsan"));
    assertFalse(GuideDecoder.isOfficialUrl(null));
    assertFalse(GuideDecoder.isOfficialUrl("https://www.kunsan.ac.kr/sw/index.kunsan#fragment"));
  }

  @Test
  public void practiceRestoresScoresAndRetries() {
    GuideBundle b = GuideDecoder.decode(GuideDecoder.DATASET_ID, valid());
    PracticeSession s = new PracticeSession(b);
    s.select("location", "library");
    try {
      s.submit();
      fail();
    } catch (IllegalArgumentException expected) {
    }
    s.select("values", "communicate");
    s.select("notice", "official");
    s.submit();
    assertEquals(2, s.getScore());
    assertFalse(s.isCorrect("location"));
    PracticeSession.Snapshot snap = s.snapshot();
    PracticeSession restored = PracticeSession.restore(b, snap.getAnswers(), snap.isSubmitted());
    assertEquals(2, restored.getScore());
    restored.retry();
    assertTrue(restored.getAnswers().isEmpty());
    restored.select("location", "digital_first");
    restored.select("values", "communicate");
    restored.select("notice", "official");
    restored.submit();
    assertEquals(3, restored.getScore());
    assertEquals(2, s.getScore());
    try {
      PracticeSession.restore(b, Collections.singletonMap("location", "official"), false);
      fail();
    } catch (IllegalArgumentException expected) {
    }
    try {
      PracticeSession.restore(b, Collections.singletonMap("location", "library"), true);
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void rejectsMissingAndWrongTypes() {
    Map<String, Object> x = valid();
    x.remove("targetId");
    rejected(x);
    x = valid();
    x.put("targetId", null);
    rejected(x);
    x = valid();
    x.put("targetId", true);
    rejected(x);
    x = valid();
    x.put("sources", "not a list");
    rejected(x);
    x = valid();
    ((List<Object>) x.get("sources")).set(0, "not a map");
    rejected(x);
    x = valid();
    row(x, "sources", 0).remove("label");
    rejected(x);
    x = valid();
    row(x, "claims", 0).put("text", "");
    rejected(x);
    x = valid();
    row(x, "claims", 0).put("text", new String(new char[1001]).replace('\0', 'a'));
    rejected(x);
    x = valid();
    row(x, "claims", 0).put("text", "\u2003\u00a0");
    rejected(x);
  }

  @Test
  public void rejectsMissingAndUnknownReferences() {
    Map<String, Object> x = valid();
    ((List<?>) x.get("sources")).remove(0);
    rejected(x);
    x = valid();
    ((List<?>) x.get("claims")).remove(0);
    rejected(x);
    x = valid();
    row(x, "claims", 0).put("sourceId", "unknown");
    rejected(x);
    x = valid();
    row(x, "sources", 0).put("id", "sw_home");
    rejected(x);
    x = valid();
    row(x, "questions", 0).put("id", "values");
    rejected(x);
    x = valid();
    ((Map<String, Object>) ((List<?>) row(x, "questions", 0).get("options")).get(0))
        .put("id", "unknown");
    rejected(x);
  }

  @Test
  public void modelsAndSnapshotsAreIsolated() {
    Map<String, Object> raw = valid();
    GuideBundle bundle = GuideDecoder.decode(GuideDecoder.DATASET_ID, raw);
    row(raw, "claims", 0).put("text", "changed");
    assertEquals("text", bundle.getClaims().get(0).getText());
    try {
      bundle.getClaims().clear();
      fail();
    } catch (UnsupportedOperationException expected) {
    }
    try {
      bundle.getQuestions().get(0).getOptions().clear();
      fail();
    } catch (UnsupportedOperationException expected) {
    }
    PracticeSession session = new PracticeSession(bundle);
    session.select("location", "library");
    Map<String, String> answers = session.getAnswers();
    PracticeSession.Snapshot snapshot = session.snapshot();
    try {
      answers.clear();
      fail();
    } catch (UnsupportedOperationException expected) {
    }
    try {
      snapshot.getAnswers().clear();
      fail();
    } catch (UnsupportedOperationException expected) {
    }
    session.retry();
    assertEquals("library", snapshot.getAnswers().get("location"));
  }

  @Test
  public void partialRestoreAndStateGuards() {
    GuideBundle bundle = GuideDecoder.decode(GuideDecoder.DATASET_ID, valid());
    PracticeSession session =
        PracticeSession.restore(
            bundle, Collections.singletonMap("location", "digital_first"), false);
    assertEquals("digital_first", session.getAnswers().get("location"));
    try {
      session.getScore();
      fail();
    } catch (IllegalStateException expected) {
    }
    try {
      session.isCorrect("location");
      fail();
    } catch (IllegalStateException expected) {
    }
    try {
      PracticeSession.restore(bundle, Collections.singletonMap("unknown", "official"), false);
      fail();
    } catch (IllegalArgumentException expected) {
    }
    session.select("values", "communicate");
    session.select("notice", "official");
    session.submit();
    assertEquals(3, session.getScore());
    try {
      session.select("location", "library");
      fail();
    } catch (IllegalStateException expected) {
    }
  }
}
