package com.example.firststep.game.presentation.authored;

import static org.junit.Assert.*;

import com.example.firststep.game.domain.Progress;
import com.example.firststep.game.sync.SyncScope;
import org.junit.Test;

public final class AuthoredReminderEntryBindingTest {
  @Test public void acceptedActivityKeepsExpectedOwnerAfterPassingRevokesOriginalTicket() {
    AuthoredReminderEntryBinding binding = new AuthoredReminderEntryBinding();
    SyncScope expected = new SyncScope("test-project", "test-user", 1,
        "software_authored_v1", Progress.Mode.NORMAL, "digital_information_software");
    assertSame(expected, binding.resolve("original-marker", () -> expected));
    assertSame(expected, binding.resolve("original-marker", () -> {
      throw new AssertionError("Revoked opaque ticket must not be resolved again in same activity");
    }));
    try {
      binding.resolve("different-marker", () -> expected);
      fail("Different entry must not borrow prior validation");
    } catch (IllegalArgumentException expectedFailure) { }
    try {
      new AuthoredReminderEntryBinding().resolve("original-marker", () -> {
        throw new IllegalArgumentException("Fresh activity still validates revoked ticket");
      });
      fail("A new binding must verify the opaque ticket");
    } catch (IllegalArgumentException expectedFailure) { }
  }
}
