package com.example.firststep.game.presentation.home.entry;

import static org.junit.Assert.*;

import org.junit.Test;

public final class CampusChoiceTest {
  @Test
  public void routesOnlyExactPairsAfterTrimming() {
    assertEquals(
        CampusChoice.Guide.SOFTWARE,
        new CampusChoice(" 컴퓨터소프트웨어특성화대학 ", "소프트웨어학과").getGuide());
    assertEquals(
        CampusChoice.Guide.KOREAN, new CampusChoice("인문콘텐츠융합대학", "국어국문학과").getGuide());
    assertEquals(
        CampusChoice.Guide.NONE, new CampusChoice("인문콘텐츠융합대학", "소프트웨어학과").getGuide());
    assertEquals(CampusChoice.Guide.NONE, new CampusChoice("Other", "Other").getGuide());
    assertEquals(CampusChoice.Guide.NONE, new CampusChoice(null, null).getGuide());
  }

  @Test
  public void boundsTrimmedValues() {
    String maximum = new String(new char[120]).replace('\0', 'x');
    assertEquals(maximum, new CampusChoice(" " + maximum + " ", "").getCollege());
    assertThrows(IllegalArgumentException.class, () -> new CampusChoice(maximum + "x", ""));
    assertThrows(IllegalArgumentException.class, () -> new CampusChoice("", maximum + "x"));
  }
}
