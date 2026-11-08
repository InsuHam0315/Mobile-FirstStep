package com.example.firststep.game.content;

/** Typed contracts; loading runs on a worker, source-open actions run on the UI thread. */
public final class GuidePorts {
  private GuidePorts() {}

  public interface BundleLoader {
    GuideBundle load() throws Exception;
  }

  public interface SourceOpener {
    boolean open(GuideBundle.Source source);
  }
}
