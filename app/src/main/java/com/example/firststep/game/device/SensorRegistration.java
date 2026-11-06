package com.example.firststep.game.device;

/** Main-thread ownership of registration and a single unregister on session closure. */
public final class SensorRegistration {
  public enum Kind {
    HEADING,
    MOTION,
    LIGHT
  }

  public interface Port {
    boolean register(Kind kind);

    void unregister();
  }

  public static final class Availability {
    public final boolean heading, motion, light;

    private Availability(boolean heading, boolean motion, boolean light) {
      this.heading = heading;
      this.motion = motion;
      this.light = light;
    }
  }

  private final Port port;
  private boolean started, closed;
  private Availability availability;

  public SensorRegistration(Port port) {
    this.port = port;
  }

  public Availability start() {
    if (closed) return new Availability(false, false, false);
    if (!started) {
      started = true;
      availability =
          new Availability(
              port.register(Kind.HEADING), port.register(Kind.MOTION), port.register(Kind.LIGHT));
    }
    return availability;
  }

  public void close() {
    if (!closed) {
      closed = true;
      if (started) port.unregister();
    }
  }
}
