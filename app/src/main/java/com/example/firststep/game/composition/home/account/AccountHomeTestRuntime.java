package com.example.firststep.game.composition.home.account;

import com.example.firststep.BuildConfig;
import com.example.firststep.game.domain.ProgressService;
import com.example.firststep.game.presentation.home.account.AccountHomeController;
import java.util.Objects;

/** Process-local instrumentation override. No Intent can select it. */
public final class AccountHomeTestRuntime {
  public static final class Binding {
    public final AccountHomeController.Source source;
    public final boolean registered;
    public final ProgressService.Clock clock;

    private Binding(AccountHomeController.Source source, boolean registered,
        ProgressService.Clock clock) {
      this.source = Objects.requireNonNull(source);
      this.registered = registered;
      this.clock = Objects.requireNonNull(clock);
    }
  }

  private static Binding active;

  private AccountHomeTestRuntime() {}

  /** Close the Activity before releasing the binding. Release builds reject installation. */
  public static synchronized AutoCloseable install(AccountHomeController.Source source,
      boolean registered, ProgressService.Clock clock) {
    if (!BuildConfig.DEBUG) throw new IllegalStateException("Test binding unavailable");
    if (active != null) throw new IllegalStateException("Test binding already installed");
    Binding binding = new Binding(source, registered, clock);
    active = binding;
    return () -> release(binding);
  }

  public static synchronized Binding current() {
    return BuildConfig.DEBUG ? active : null;
  }

  private static synchronized void release(Binding binding) {
    if (active == binding) active = null;
  }
}
