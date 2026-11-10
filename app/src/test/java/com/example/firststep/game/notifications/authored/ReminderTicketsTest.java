package com.example.firststep.game.notifications.authored;

import static org.junit.Assert.*;
import com.example.firststep.game.domain.Progress;
import com.example.firststep.game.domain.ProgressService;
import com.example.firststep.game.sync.ConfirmedSessionOwner;
import com.example.firststep.game.sync.SessionOwner;
import com.example.firststep.game.sync.SyncScope;
import java.util.Collections;
import org.junit.Test;

public final class ReminderTicketsTest {
  private long now = 100;
  private final ReminderTickets tickets = new ReminderTickets(() -> now);
  private final ConfirmedSessionOwner owner = new ConfirmedSessionOwner("project");
  private final ReminderTickets.Eligibility eligible = scope -> {};
  private String token;
  private boolean permission = true, fail;
  private int calls;
  private final ReminderTickets.Delivery delivery = new ReminderTickets.Delivery() {
    public boolean permitted() { return permission; }
    public void schedule(String value, long at) {
      calls++;
      if (fail) throw new IllegalStateException();
      token = value;
      assertEquals(100 + ProgressService.WAIT_MILLIS, at);
    }
  };
  private SyncScope scope() {
    owner.onAuthChanged("alice"); owner.confirm("alice");
    return owner.capture("dataset", Progress.Mode.NORMAL, "building");
  }
  private Progress progress() {
    return new Progress(Progress.Mode.NORMAL, "building", 100L, false, false, null,
        Collections.emptyMap());
  }
  @Test public void replayAndDueValidation() {
    SyncScope scope = scope();
    assertEquals(AuthoredReminder.Result.SCHEDULED, tickets.schedule(scope, progress(), owner, eligible, delivery));
    assertEquals(AuthoredReminder.Result.ALREADY_SCHEDULED, tickets.schedule(scope, progress(), owner, eligible, delivery));
    assertEquals(1, calls);
    assertThrows(IllegalArgumentException.class, () -> tickets.valid(token));
    now += ProgressService.WAIT_MILLIS;
    assertSame(scope, tickets.valid(token).scope);
  }
  @Test public void coldProcessRejectsPreviouslyValidTicket() {
    SyncScope scope = scope();
    tickets.schedule(scope, progress(), owner, eligible, delivery);
    now += ProgressService.WAIT_MILLIS;
    assertSame(scope, tickets.valid(token).scope);
    ReminderTickets coldProcess = new ReminderTickets(() -> now);
    assertThrows(IllegalArgumentException.class, () -> coldProcess.valid(token));
  }

  @Test public void deniedAndFailureAllowRetry() {
    SyncScope scope = scope(); permission = false;
    assertEquals(AuthoredReminder.Result.PERMISSION_DENIED, tickets.schedule(scope, progress(), owner, eligible, delivery));
    permission = true; fail = true;
    assertEquals(AuthoredReminder.Result.UNAVAILABLE, tickets.schedule(scope, progress(), owner, eligible, delivery));
    fail = false;
    assertEquals(AuthoredReminder.Result.SCHEDULED, tickets.schedule(scope, progress(), owner, eligible, delivery));
  }
  @Test public void ownerSwitchAndRevocationInvalidateTickets() {
    SyncScope scope = scope();
    tickets.schedule(scope, progress(), owner, eligible, delivery);
    now += ProgressService.WAIT_MILLIS;
    owner.onAuthChanged("bob"); owner.confirm("bob");
    assertThrows(SessionOwner.StaleSession.class, () -> tickets.valid(token));
    assertEquals(AuthoredReminder.Result.INELIGIBLE, tickets.schedule(scope, progress(), owner, eligible, delivery));
    owner.onAuthChanged("alice"); owner.confirm("alice");
    SyncScope reauthenticated = owner.capture("dataset", Progress.Mode.NORMAL, "building");
    assertEquals(AuthoredReminder.Result.SCHEDULED, tickets.schedule(reauthenticated, progress(), owner, eligible, delivery));
  }
  @Test public void negativeIntroNeverSchedules() {
    SyncScope scope = scope();
    Progress negative = new Progress(Progress.Mode.NORMAL, "building", -1L, false, false, null,
        Collections.emptyMap());
    assertEquals(AuthoredReminder.Result.INELIGIBLE,
        tickets.schedule(scope, negative, owner, eligible, delivery));
    assertEquals(0, calls);
  }
  @Test public void forbiddenScopeNeverSchedules() {
    SyncScope scope = scope();
    assertEquals(AuthoredReminder.Result.INELIGIBLE, tickets.schedule(scope, progress(), owner,
        s -> { throw new IllegalArgumentException(); }, delivery));
    assertEquals(0, calls);
    assertThrows(IllegalArgumentException.class, () -> tickets.valid("missing"));
  }
}
