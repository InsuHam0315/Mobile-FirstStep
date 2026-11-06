package com.example.firststep.facility;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.Test;

public class FacilitySearchTest {
  private final Facility library =
      new Facility(
          "library",
          "중앙도서관",
          "학습",
          "자료 열람",
          Arrays.asList("조용히 공부하기", "자료 검색"),
          Arrays.asList("도서관", "Library"),
          "https://example.org",
          "공식",
          "2026-11-05",
          "위치 현장 확인 전",
          "운영시간 현장 확인 전");
  private final Facility career =
      new Facility(
          "career",
          "진로지원센터",
          "진로·취업",
          "취업 상담",
          Arrays.asList("취업 준비", "상담"),
          Arrays.asList("Career Center"),
          "https://example.org",
          "공식",
          "2026-11-05",
          "위치 현장 확인 전",
          "운영시간 현장 확인 전");

  @Test
  public void termsAreAndAcrossDifferentFields() {
    assertTrue(FacilitySearch.matches(library, "공부 도서관"));
    assertFalse(FacilitySearch.matches(library, "공부 취업"));
    assertTrue(FacilitySearch.matches(career, "취업   상담"));
  }

  @Test
  public void caseWhitespaceAndHangulNormalization() {
    assertTrue(FacilitySearch.matches(library, "  liBrArY  "));
    assertTrue(FacilitySearch.matches(library, "도서관"));
    assertTrue(FacilitySearch.matches(library, "  "));
  }

  @Test
  public void categoryAndFavoriteUseStableIds() {
    List<Facility> all = Arrays.asList(library, career);
    Set<String> saved = new HashSet<>(Arrays.asList("library"));
    assertEquals(
        Collections.singletonList(library), FacilitySearch.filter(all, "", "학습", true, saved));
    assertTrue(FacilitySearch.filter(all, "", "진로·취업", true, saved).isEmpty());
    assertEquals(
        Collections.singletonList(career), FacilitySearch.filter(all, "상담", null, false, saved));
  }
}
