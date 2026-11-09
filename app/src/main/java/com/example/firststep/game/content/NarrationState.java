package com.example.firststep.game.content;

/** Transient, pure navigation state. Speech never advances it. */
public final class NarrationState {
  private final int size;
  private int position;
  public NarrationState(int size, int position) {
    if (size <= 0) throw new IllegalArgumentException("Empty narration");
    this.size = size;
    this.position = position >= 0 && position < size ? position : 0;
  }
  public int position() { return position; }
  public void previous() { if (position > 0) position--; }
  public void next() { if (position + 1 < size) position++; }
}
