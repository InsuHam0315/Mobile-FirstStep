package com.example.firststep.game.presentation.home.entry;

import java.util.Objects;

/** A bounded, locally selected college and department. */
public final class CampusChoice {
  public static final int MAX_LENGTH = 120;
  public static final String SOFTWARE_COLLEGE = "컴퓨터소프트웨어특성화대학";
  public static final String SOFTWARE_DEPARTMENT = "소프트웨어학과";
  public static final String KOREAN_COLLEGE = "인문콘텐츠융합대학";
  public static final String KOREAN_DEPARTMENT = "국어국문학과";

  public enum Guide {
    NONE,
    SOFTWARE,
    KOREAN
  }

  private final String college;
  private final String department;

  public CampusChoice(String college, String department) {
    this.college = clean(college);
    this.department = clean(department);
  }

  private static String clean(String value) {
    String result = value == null ? "" : value.trim();
    if (result.length() > MAX_LENGTH) {
      throw new IllegalArgumentException("Campus choice is too long");
    }
    return result;
  }

  public String getCollege() {
    return college;
  }

  public String getDepartment() {
    return department;
  }

  public Guide getGuide() {
    if (SOFTWARE_COLLEGE.equals(college) && SOFTWARE_DEPARTMENT.equals(department)) {
      return Guide.SOFTWARE;
    }
    if (KOREAN_COLLEGE.equals(college) && KOREAN_DEPARTMENT.equals(department)) {
      return Guide.KOREAN;
    }
    return Guide.NONE;
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof CampusChoice)) return false;
    CampusChoice choice = (CampusChoice) other;
    return college.equals(choice.college) && department.equals(choice.department);
  }

  @Override
  public int hashCode() {
    return Objects.hash(college, department);
  }
}
