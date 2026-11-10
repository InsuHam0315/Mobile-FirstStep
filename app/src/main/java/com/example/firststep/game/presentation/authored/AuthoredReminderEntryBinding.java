package com.example.firststep.game.presentation.authored;

import com.example.firststep.game.sync.SyncScope;
import java.util.Objects;
import java.util.function.Supplier;

/** Retains validated reminder ownership in memory for this Activity and its recreation. */
public final class AuthoredReminderEntryBinding {
  private String marker;
  private SyncScope expected;

  public synchronized SyncScope resolve(String entry, Supplier<SyncScope> validate) {
    Objects.requireNonNull(entry, "entry");
    if (expected == null) {
      expected = Objects.requireNonNull(validate.get(), "verified reminder");
      marker = entry;
    } else if (!marker.equals(entry)) {
      throw new IllegalArgumentException("Unexpected reminder entry");
    }
    return expected;
  }
}
