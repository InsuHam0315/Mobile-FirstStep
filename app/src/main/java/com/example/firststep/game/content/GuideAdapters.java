package com.example.firststep.game.content;

import android.content.*;
import android.net.Uri;
import com.example.firststep.BuildConfig;

/**
 * Android I/O and navigation adapters. No progress/profile access, fallback or background network.
 */
public final class GuideAdapters {
  public interface Factory {
    Environment create(Context context);
  }

  public static final class Environment {
    public final GuidePorts.BundleLoader loader;
    public final GuidePorts.SourceOpener opener;
    public final boolean simulatedInputs;

    public Environment(
        GuidePorts.BundleLoader loader, GuidePorts.SourceOpener opener, boolean simulatedInputs) {
      if (loader == null || opener == null) throw new IllegalArgumentException("Missing adapter");
      this.loader = loader;
      this.opener = opener;
      this.simulatedInputs = simulatedInputs;
    }
  }

  private static Factory testFactory;

  private GuideAdapters() {}

  /** In-process instrumentation only; unreachable via intents and always rejected in release. */
  public static void setTestFactory(Factory factory) {
    if (!BuildConfig.DEBUG) throw new IllegalStateException("Test overrides unavailable");
    testFactory = factory;
  }

  public static Environment create(Context context) {
    return create(context, GuideDecoder.DATASET_ID);
  }

  public static Environment create(Context context, String datasetId) {
    if (!GuideDecoder.isRegistered(datasetId)) throw new IllegalArgumentException("Unregistered guide");
    Context app = context.getApplicationContext();
    if (BuildConfig.DEBUG && testFactory != null) return testFactory.create(app);
    return new Environment(
        new AssetGuideRepository(app, datasetId), source -> openOfficialSource(app, source), false);
  }

  public static boolean openOfficialSource(Context context, GuideBundle.Source source) {
    if (source == null || !GuideDecoder.isOfficialUrl(source.getUrl())) return false;
    Intent intent =
        new Intent(Intent.ACTION_VIEW, Uri.parse(source.getUrl()))
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    try {
      context.startActivity(intent);
      return true;
    } catch (ActivityNotFoundException | SecurityException unavailable) {
      return false;
    }
  }
}
