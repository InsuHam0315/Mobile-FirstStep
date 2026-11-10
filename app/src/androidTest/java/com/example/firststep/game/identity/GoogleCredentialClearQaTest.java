package com.example.firststep.game.identity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeNotNull;

import android.app.Instrumentation;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.SystemClock;
import android.os.UserHandle;
import android.util.Log;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.firststep.BuildConfig;
import com.example.firststep.game.auth.Failure;
import com.example.firststep.game.composition.identity.GoogleCredentialSource;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Opt-in Android regression for the real credential provider on the isolated account-free AVD. */
@RunWith(AndroidJUnit4.class)
public final class GoogleCredentialClearQaTest {
  private static final String QA_AVD = "FirstStep_H07_QA_Clear_API37";
  private static final String TAG = "H07ClearQa";

  @Test
  public void providerClearSucceedsAndCompletesOnMainLooper() throws Exception {
    Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    String qaTarget = InstrumentationRegistry.getArguments().getString("h07ClearQa");
    assumeNotNull(qaTarget);
    assertEquals("Use only the explicit H07 QA invocation", QA_AVD, qaTarget);
    assertTrue(
        "Use only the owned QA AVD",
        isOnlyLine(instrumentation, "getprop ro.boot.qemu.avd_name", QA_AVD));
    assertTrue("Expected Android user 0", isOnlyLine(instrumentation, "am get-current-user", "0"));
    assertEquals(
        "Instrumentation must run as Android user 0",
        UserHandle.getUserHandleForUid(0),
        Process.myUserHandle());
    assertTrue(
        "Expected no registered accounts for QA user 0", userZeroHasNoAccounts(instrumentation));
    assertTrue("Use the configured QA APK", BuildConfig.H07_CONFIGURED);

    CountDownLatch terminal = new CountDownLatch(1);
    AtomicInteger deliveries = new AtomicInteger();
    AtomicReference<Failure> outcome = new AtomicReference<>();
    AtomicReference<Looper> callbackLooper = new AtomicReference<>();
    instrumentation.runOnMainSync(
        () -> {
          GoogleCredentialSource source =
              new GoogleCredentialSource(
                  instrumentation.getTargetContext(), BuildConfig.H07_WEB_CLIENT_ID);
          long start = SystemClock.elapsedRealtime();
          Log.i(TAG, "clear_started");
          source.clear(
              failure -> {
                if (deliveries.incrementAndGet() != 1) return;
                callbackLooper.set(Looper.myLooper());
                outcome.set(failure);
                Log.i(
                    TAG,
                    "terminal="
                        + (failure == null ? "provider_success" : failure.name())
                        + " main_looper="
                        + (Looper.myLooper() == Looper.getMainLooper())
                        + " elapsed_ms="
                        + (SystemClock.elapsedRealtime() - start));
                terminal.countDown();
              });
        });
    assertTrue("Clear delivered no terminal callback", terminal.await(15, TimeUnit.SECONDS));
    assertSame(
        "Clear completion must run on the Android main Looper",
        Looper.getMainLooper(),
        callbackLooper.get());
    assertNull("Provider error or deadline must not count as provider success", outcome.get());
    instrumentation.waitForIdleSync();
    assertEquals("Clear delivered duplicate callbacks", 1, deliveries.get());
  }

  private static boolean isOnlyLine(Instrumentation instrumentation, String command, String expected)
      throws IOException {
    try (BufferedReader output = shell(instrumentation, command)) {
      return expected.equals(output.readLine()) && output.readLine() == null;
    }
  }

  private static boolean userZeroHasNoAccounts(Instrumentation instrumentation) throws IOException {
    try (BufferedReader output = shell(instrumentation, "dumpsys account")) {
      boolean userZero = false;
      for (String line; (line = output.readLine()) != null; ) {
        String value = line.trim();
        if (value.startsWith("User UserInfo{")) {
          if (userZero) return false;
          userZero = value.startsWith("User UserInfo{0:");
        } else if (userZero && value.startsWith("Accounts:")) {
          return value.equals("Accounts: 0");
        }
      }
      return false;
    }
  }

  private static BufferedReader shell(Instrumentation instrumentation, String command) {
    ParcelFileDescriptor descriptor = instrumentation.getUiAutomation().executeShellCommand(command);
    return new BufferedReader(
        new InputStreamReader(
            new ParcelFileDescriptor.AutoCloseInputStream(descriptor), StandardCharsets.UTF_8));
  }
}
