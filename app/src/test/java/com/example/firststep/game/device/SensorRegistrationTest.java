package com.example.firststep.game.device;

import static org.junit.Assert.*;

import org.junit.Test;

public class SensorRegistrationTest {
  @Test
  public void failedRegistrationsAreUnavailableAndCloseIsIdempotent() {
    int[] registered = {0}, unregistered = {0};
    SensorRegistration session =
        new SensorRegistration(
            new SensorRegistration.Port() {
              public boolean register(SensorRegistration.Kind kind) {
                registered[0]++;
                return kind == SensorRegistration.Kind.MOTION;
              }

              public void unregister() {
                unregistered[0]++;
              }
            });
    SensorRegistration.Availability a = session.start();
    assertFalse(a.heading);
    assertTrue(a.motion);
    assertFalse(a.light);
    session.start();
    assertEquals(3, registered[0]);
    session.close();
    session.close();
    assertEquals(1, unregistered[0]);
    assertFalse(session.start().motion);
    assertEquals(3, registered[0]);
  }

  @Test
  public void closeBeforeStartNeverRegistersOrUnregisters() {
    int[] calls = {0};
    SensorRegistration session =
        new SensorRegistration(
            new SensorRegistration.Port() {
              public boolean register(SensorRegistration.Kind kind) {
                calls[0]++;
                return true;
              }

              public void unregister() {
                calls[0]++;
              }
            });
    session.close();
    assertFalse(session.start().heading);
    assertEquals(0, calls[0]);
  }
}
