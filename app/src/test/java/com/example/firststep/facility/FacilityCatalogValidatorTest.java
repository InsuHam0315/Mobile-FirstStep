package com.example.firststep.facility;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class FacilityCatalogValidatorTest {
  private Facility entry(String id, String url, String date) {
    return new Facility(
        id,
        "도서관",
        "학습",
        "책과 학습 공간 안내",
        Collections.singletonList("책 빌리기"),
        Collections.singletonList("도서관"),
        url,
        "대학 공식 안내",
        date,
        "현장 확인 전",
        "공식 안내 확인");
  }

  @Test
  public void allowsUniversitySitesAndRejectsLookalikeOrUntrustedSchemes() {
    assertTrue(
        FacilityCatalogValidator.isOfficialUrl("https://library.kunsan.ac.kr/main/sub.php?mno=37"));
    assertTrue(FacilityCatalogValidator.isOfficialUrl("https://www.kunsan.ac.kr/job/index.kunsan"));
    assertFalse(FacilityCatalogValidator.isOfficialUrl("https://kunsan.ac.kr.example.com/"));
    assertFalse(FacilityCatalogValidator.isOfficialUrl("https://notkunsan.ac.kr/"));
    assertFalse(FacilityCatalogValidator.isOfficialUrl("https://name:password@www.kunsan.ac.kr/"));
    assertFalse(FacilityCatalogValidator.isOfficialUrl("http://www.kunsan.ac.kr/"));
    assertFalse(FacilityCatalogValidator.isOfficialUrl("javascript:alert(1)"));
  }

  @Test
  public void rejectsDuplicateIdsAndInvalidReviewDates() {
    Facility valid = entry("library-central", "https://library.kunsan.ac.kr/", "2026-11-05");
    FacilityCatalogValidator.validate(Collections.singletonList(valid));
    try {
      FacilityCatalogValidator.validate(Arrays.asList(valid, valid));
      fail("duplicate ID accepted");
    } catch (IllegalArgumentException expected) {
    }
    try {
      FacilityCatalogValidator.validate(
          Collections.singletonList(
              entry("library-central", "https://library.kunsan.ac.kr/", "2026-02-30")));
      fail("invalid date accepted");
    } catch (IllegalArgumentException expected) {
    }
  }
}
