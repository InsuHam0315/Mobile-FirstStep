package com.example.firststep.game.auth;

import java.util.Objects;

public final class Profile {
  private final String name, studentNumber, department;

  public Profile(String name, String studentNumber, String department) {
    this.name = clean(name);
    this.studentNumber = clean(studentNumber);
    this.department = clean(department);
  }

  private static String clean(String value) {
    return value == null ? "" : value.trim();
  }

  public String getName() {
    return name;
  }

  public String getStudentNumber() {
    return studentNumber;
  }

  public String getDepartment() {
    return department;
  }

  public boolean isComplete() {
    return valid(name, 80) && valid(studentNumber, 40) && valid(department, 120);
  }

  private static boolean valid(String value, int max) {
    return !value.isEmpty() && value.length() <= max;
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof Profile)) return false;
    Profile p = (Profile) other;
    return name.equals(p.name)
        && studentNumber.equals(p.studentNumber)
        && department.equals(p.department);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, studentNumber, department);
  }
}
