package com.example.firststep.game.content;

import static org.junit.Assert.*;
import java.util.*;
import org.junit.Test;

public final class NarrationContentTest {
  private static Map<String, Object> map(Object... entries) {
    Map<String, Object> m = new LinkedHashMap<>();
    for (int i = 0; i < entries.length; i += 2) m.put((String) entries[i], entries[i + 1]);
    return m;
  }
  private static Map<String, Object> valid() {
    String[] ids = {"greeting", "invitation", "college", "visit", "values", "resources", "freshness"};
    String[] types = {"fictional", "editorial", "claim", "claim", "claim", "claim", "editorial"};
    String[] claims = {"", "", "college_membership", "office_guidance", "learning_values", "help_resources", ""};
    List<Object> rows = new ArrayList<>();
    for (int i = 0; i < ids.length; i++) rows.add(map("id", ids[i], "provenance", types[i], "claimId", claims[i], "text", "caption"));
    return map("datasetId", NarrationContent.DATASET_ID, "version", 1, "sentences", rows);
  }
  @SuppressWarnings("unchecked") private static Map<String,Object> row(Map<String,Object> m, int i) {
    return (Map<String,Object>) ((List<?>)m.get("sentences")).get(i);
  }
  private static void reject(String id, Map<String,Object> m) {
    try { NarrationContent.decode(id, m); fail("invalid narration accepted"); }
    catch (IllegalArgumentException expected) { }
  }
  @Test public void validatesRegisteredIdentityAndProvenance() {
    NarrationContent content = NarrationContent.decode(NarrationContent.DATASET_ID, valid());
    assertEquals(7, content.sentences().size());
    assertEquals(NarrationContent.Provenance.FICTIONAL, content.sentences().get(0).provenance);
    assertEquals("office_guidance", content.sentences().get(3).claimId);
    reject("h04_fixture_v1", valid());
    reject("../databases/firststep.db", valid());
    Map<String,Object> m = valid(); row(m, 3).put("claimId", "help_resources"); reject(NarrationContent.DATASET_ID, m);
    m = valid(); row(m, 0).put("provenance", "claim"); reject(NarrationContent.DATASET_ID, m);
    m = valid(); row(m, 1).put("claimId", "college_membership"); reject(NarrationContent.DATASET_ID, m);
    m = valid(); row(m, 3).put("id", "college"); reject(NarrationContent.DATASET_ID, m);
    m = valid(); m.put("version", 1.0); reject(NarrationContent.DATASET_ID, m);
    m = valid(); m.put("coordinates", null); reject(NarrationContent.DATASET_ID, m);
    m = valid(); row(m, 2).put("text", " "); reject(NarrationContent.DATASET_ID, m);
    m = valid(); ((List<?>)m.get("sentences")).remove(0); reject(NarrationContent.DATASET_ID, m);
    try { content.sentences().clear(); fail(); } catch (UnsupportedOperationException expected) { }
  }
  @Test public void navigationBoundariesAndRestorationAreTransient() {
    NarrationState state = new NarrationState(7, 0);
    state.previous(); assertEquals(0, state.position());
    state.next(); state.next(); state.next();
    NarrationState restored = new NarrationState(7, state.position());
    assertEquals(3, restored.position());
    restored.previous(); assertEquals(2, restored.position());
    assertEquals(3, state.position());
    for (int i = 0; i < 20; i++) restored.next();
    assertEquals(6, restored.position());
    assertEquals(0, new NarrationState(7, -1).position());
    assertEquals(0, new NarrationState(7, 7).position());
    try { new NarrationState(0, 0); fail(); } catch (IllegalArgumentException expected) { }
  }
}
