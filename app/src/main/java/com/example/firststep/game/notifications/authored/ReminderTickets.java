package com.example.firststep.game.notifications.authored;

import com.example.firststep.game.domain.Progress;
import com.example.firststep.game.domain.ProgressService;
import com.example.firststep.game.sync.SessionOwner;
import com.example.firststep.game.sync.SyncScope;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Process-local tickets are capped at 128 until replacement or process exit;
 * no lock is held while acquiring a session guard. */
final class ReminderTickets {
  interface Eligibility { void validate(SyncScope scope); }
  interface Delivery { boolean permitted(); void schedule(String token, long at); }
  interface Clock { long now(); }
  static final class Ticket {
    final String token, key;
    final SyncScope scope;
    final long due;
    final SessionOwner owner;
    final Eligibility eligibility;
    Ticket(String token, String key, SyncScope scope, long due, SessionOwner owner,
        Eligibility eligibility) {
      this.token = token; this.key = key; this.scope = scope; this.due = due;
      this.owner = owner; this.eligibility = eligibility;
    }
  }
  private final Map<String, Ticket> tickets = new HashMap<>();
  private final Map<String, Ticket> keys = new HashMap<>();
  private final Clock clock;
  ReminderTickets(Clock clock) { this.clock = clock; }

  AuthoredReminder.Result schedule(SyncScope scope, Progress progress, SessionOwner owner,
      Eligibility eligibility, Delivery delivery) {
    try {
      eligibility.validate(scope);
      if (progress == null || progress.mode != Progress.Mode.NORMAL || scope.mode != progress.mode
          || !scope.buildingId.equals(progress.buildingId)
          || progress.introDoneAt == null || progress.introDoneAt < 0
          || progress.introDoneAt > Long.MAX_VALUE - ProgressService.WAIT_MILLIS)
        return AuthoredReminder.Result.INELIGIBLE;
      return owner.whileCurrent(scope, () -> {

        String key = scope.projectId + "/" + scope.uid + "/" + scope.datasetId + "/"
            + scope.mode.name() + "/" + scope.buildingId;
        synchronized (this) {
          Ticket previous = keys.get(key);
          if (progress.stamp) {
            if (previous != null) { tickets.remove(previous.token); keys.remove(key); }
            return AuthoredReminder.Result.INELIGIBLE;
          }
          if (!delivery.permitted()) return AuthoredReminder.Result.PERMISSION_DENIED;
          if (previous != null && previous.scope.sessionGeneration == scope.sessionGeneration)
            return AuthoredReminder.Result.ALREADY_SCHEDULED;
          if (previous != null) tickets.remove(previous.token);
          keys.remove(key);
          if (tickets.size() >= 128) return AuthoredReminder.Result.UNAVAILABLE;
          Ticket ticket = new Ticket(UUID.randomUUID().toString(), key, scope,
              progress.introDoneAt + ProgressService.WAIT_MILLIS, owner, eligibility);
          tickets.put(ticket.token, ticket); keys.put(key, ticket);
          try {
            delivery.schedule(ticket.token, ticket.due);
            return AuthoredReminder.Result.SCHEDULED;
          } catch (RuntimeException failure) {
            tickets.remove(ticket.token); keys.remove(key);
            return AuthoredReminder.Result.UNAVAILABLE;
          }
        }
      });
    } catch (IllegalArgumentException | SessionOwner.StaleSession invalid) {
      return AuthoredReminder.Result.INELIGIBLE;
    } catch (RuntimeException unavailable) {
      return AuthoredReminder.Result.UNAVAILABLE;
    }
  }

  Ticket valid(String token) {
    Ticket ticket;
    synchronized (this) { ticket = tickets.get(token); }
    if (ticket == null) throw new IllegalArgumentException("Unknown reminder");
    ticket.eligibility.validate(ticket.scope);
    return ticket.owner.whileCurrent(ticket.scope, () -> {
      synchronized (this) {
        if (tickets.get(token) != ticket || clock.now() < ticket.due)
          throw new IllegalArgumentException("Stale or early reminder");
        return ticket;
      }
    });
  }
}
