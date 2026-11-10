package com.example.firststep.game.presentation.authored;

/** Main-thread ownership for the visible sync operation. */
public final class AuthoredSyncPublication {
  private Object lastAutomatic;
  private Ticket current;

  public static final class Ticket {
    private final boolean explicit;
    private Ticket(boolean explicit) { this.explicit = explicit; }
  }

  public Ticket observeAutomatic(Object operation) {
    if (operation == null || operation == lastAutomatic || explicitRunning()) return null;
    lastAutomatic = operation;
    current = new Ticket(false);
    return current;
  }

  public Ticket beginExplicit() {
    if (explicitRunning()) return null;
    current = new Ticket(true);
    return current;
  }

  public boolean accepts(Ticket ticket) { return ticket != null && current == ticket; }

  public boolean complete(Ticket ticket) {
    if (!accepts(ticket)) return false;
    current = null;
    return true;
  }

  public boolean running() { return current != null; }

  public boolean explicitRunning() { return current != null && current.explicit; }

  public void reset() {
    current = null;
    lastAutomatic = null;
  }
}
