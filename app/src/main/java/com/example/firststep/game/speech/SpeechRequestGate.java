package com.example.firststep.game.speech;

/** Monotonic lifecycle token shared by real SDK and debug callback paths. */
public final class SpeechRequestGate {
  private int generation;
  private boolean closed;

  public synchronized int current() {
    return generation;
  }

  public synchronized void invalidate() {
    generation++;
  }

  public synchronized void close() {
    generation++;
    closed = true;
  }

  public synchronized boolean accepts(int token) {
    return !closed && token == generation;
  }

  public synchronized boolean accepts(String id) {
    return accepts(current()) && ("h04-" + generation).equals(id);
  }
}
