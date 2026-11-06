package com.example.firststep.game.speech;

import static org.junit.Assert.*;

import org.junit.Test;

public class SpeechRequestGateTest {
  @Test
  public void oldCallbacksAreRejectedAfterStopAndClose() {
    SpeechRequestGate gate = new SpeechRequestGate();
    int first = gate.current();
    assertTrue(gate.accepts(first));
    gate.invalidate();
    assertFalse(gate.accepts(first));
    int second = gate.current();
    assertTrue(gate.accepts("h04-" + second));
    gate.close();
    assertFalse(gate.accepts(second));
    assertFalse(gate.accepts("h04-" + second));
  }

  @Test
  public void unrelatedAndMalformedUtteranceIdsAreRejected() {
    SpeechRequestGate gate = new SpeechRequestGate();
    assertFalse(gate.accepts("h04-99"));
    assertFalse(gate.accepts((String) null));
  }

  @Test
  public void delayedDeliveryRechecksToken() {
    SpeechRequestGate gate = new SpeechRequestGate();
    int token = gate.current();
    boolean[] delivered = {false};
    Runnable delivery =
        () -> {
          if (gate.accepts(token)) delivered[0] = true;
        };
    gate.invalidate();
    delivery.run();
    assertFalse(delivered[0]);
  }
}
