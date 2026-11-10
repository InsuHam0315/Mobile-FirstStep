package com.example.firststep.game.notifications.authored;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.app.Instrumentation;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.SystemClock;
import android.os.UserHandle;
import android.service.notification.StatusBarNotification;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.firststep.BuildConfig;
import com.example.firststep.R;
import com.example.firststep.game.domain.Progress;
import com.example.firststep.game.domain.ProgressService;
import com.example.firststep.game.sync.AuthoredRouteTestRegistry;
import com.example.firststep.game.sync.ConfirmedSessionOwner;
import com.example.firststep.game.sync.DatasetRegistry;
import com.example.firststep.game.sync.SessionOwner;
import com.example.firststep.game.sync.SyncScope;
import com.example.firststep.game.ui.authored.AuthoredEpisodeActivity;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Real permission check and notification posting from an explicitly invoked receiver. */
@RunWith(AndroidJUnit4.class)
public final class AuthoredReminderAndroidRuntimeTest {
  private static final String BUILDING = "reminder-building";
  private static final String USER = "reminder-owner";
  private static final int NOTIFICATION_ID = 1;

  private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
  private final Context context = instrumentation.getTargetContext();
  private final String dataset = "reminder-contract-" + UUID.randomUUID();
  private final DatasetRegistry registry = AuthoredRouteTestRegistry.create(dataset, BUILDING);
  private final ConfirmedSessionOwner owner = new ConfirmedSessionOwner("reminder-test-project");

  @Test
  public void deniedPermissionRefusesScheduling() throws Exception {
    requireOptIn();
    assertEquals(PackageManager.PERMISSION_DENIED,
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS));
    assertFalse(AuthoredReminderAndroid.notificationsAllowed(context));

    AuthoredReminder reminder = AuthoredReminderAndroid.create(context, registry, owner);
    SyncScope scope = confirmOwner();
    assertEquals(AuthoredReminder.Result.PERMISSION_DENIED,
        reminder.schedule(scope, introduction(System.currentTimeMillis())));
  }

  @Test
  public void dueReceiverPostsImmutableEntryAndRejectsRevokedTicket() throws Exception {
    requireOptIn();
    assertEquals(PackageManager.PERMISSION_GRANTED,
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS));
    assertTrue("Notifications must be enabled on the disposable AVD",
        AuthoredReminderAndroid.notificationsAllowed(context));
    NotificationManager manager = context.getSystemService(NotificationManager.class);
    assertNotNull(manager);

    SyncScope original = confirmOwner();
    long due = System.currentTimeMillis() - 2000L;
    long intro = due - ProgressService.WAIT_MILLIS;
    AtomicReference<String> token = new AtomicReference<>();
    try {
      assertEquals(AuthoredReminder.Result.SCHEDULED,
          AuthoredReminderAndroid.TICKETS.schedule(
              original, introduction(intro), owner, registry::requireEligible,
              new ReminderTickets.Delivery() {
                @Override public boolean permitted() {
                  return AuthoredReminderAndroid.notificationsAllowed(context);
                }

                @Override public void schedule(String ticket, long at) {
                  assertEquals(due, at);
                  token.set(ticket);
                }
              }));
      String tag = token.get();
      assertNotNull("The fixture must create a ticket", tag);
      Intent entry = AuthoredEpisodeActivity.intent(context)
          .setAction(AuthoredReminderAndroid.ACTION_ENTRY)
          .setData(AuthoredReminderAndroid.uri(tag));
      assertNull(entry.getExtras());
      assertSame(original, AuthoredReminderAndroid.requireCurrentEntry(entry));
      assertNull("The receiver must create the tap action", findTap(entry));
      assertNull(findNotification(manager, tag));

      Intent alarm = new Intent(context, AuthoredReminderReceiver.class)
          .setAction(AuthoredReminderAndroid.ACTION_ALARM)
          .setData(AuthoredReminderAndroid.uri(tag));
      invokeReceiver(alarm);
      Notification posted = awaitNotification(manager, tag);
      assertEquals("authored_quiz_reminders", posted.getChannelId());
      assertEquals(context.getString(R.string.authored_reminder_title),
          posted.extras.getCharSequence(Notification.EXTRA_TITLE).toString());
      assertEquals(context.getString(R.string.authored_reminder_text),
          posted.extras.getCharSequence(Notification.EXTRA_TEXT).toString());
      assertNotNull(posted.contentIntent);
      assertEquals(context.getPackageName(), posted.contentIntent.getCreatorPackage());
      assertTrue(posted.contentIntent.isActivity());
      assertTrue(posted.contentIntent.isImmutable());
      assertEquals("Tap must target this ticket's explicit authored entry",
          findTap(entry), posted.contentIntent);

      // Cancellation is scoped to the fixture. A stale receiver must not recreate the tap action.
      manager.cancel(tag, NOTIFICATION_ID);
      posted.contentIntent.cancel();
      awaitCancellation(manager, tag);
      owner.revoke();
      assertThrows(SessionOwner.StaleSession.class,
          () -> AuthoredReminderAndroid.requireCurrentEntry(entry));
      SyncScope next = confirmOwner();
      assertNotEquals(original.sessionGeneration, next.sessionGeneration);
      assertThrows(SessionOwner.StaleSession.class,
          () -> AuthoredReminderAndroid.requireCurrentEntry(entry));
      invokeReceiver(alarm);
      assertNull(findTap(entry));
      assertNull(findNotification(manager, tag));
    } finally {
      String tag = token.get();
      if (tag != null) {
        manager.cancel(tag, NOTIFICATION_ID);
        Intent entry = AuthoredEpisodeActivity.intent(context)
            .setAction(AuthoredReminderAndroid.ACTION_ENTRY)
            .setData(AuthoredReminderAndroid.uri(tag));
        PendingIntent tap = findTap(entry);
        if (tap != null) tap.cancel();
        AuthoredReminderAndroid.create(context, registry, owner)
            .schedule(confirmOwner(), completed(intro));
      }
      owner.revoke();
    }
  }

  private void requireOptIn() throws IOException {
    String avd = InstrumentationRegistry.getArguments().getString("firststepAuthoredReminderAvd");
    assumeTrue("Requires the isolated authored reminder check", avd != null);
    assertTrue("Requires a debug APK", BuildConfig.DEBUG);
    assertTrue("Requires notification runtime permissions", Build.VERSION.SDK_INT >= 33);
    assertTrue("Requires a disposable FirstStep AVD name", avd.matches("FirstStep_[A-Za-z0-9_]+"));
    assertTrue("Wrong AVD", onlyLine("getprop ro.boot.qemu.avd_name", avd));
    assertTrue("Wrong Android user", onlyLine("am get-current-user", "0"));
    assertEquals(UserHandle.getUserHandleForUid(0), Process.myUserHandle());
  }

  private boolean onlyLine(String command, String expected) throws IOException {
    ParcelFileDescriptor output = instrumentation.getUiAutomation().executeShellCommand(command);
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(
        new ParcelFileDescriptor.AutoCloseInputStream(output), StandardCharsets.UTF_8))) {
      return expected.equals(reader.readLine()) && reader.readLine() == null;
    }
  }

  private SyncScope confirmOwner() {
    owner.onAuthChanged(USER);
    owner.confirm(USER);
    return owner.capture(dataset, Progress.Mode.NORMAL, BUILDING);
  }

  private static Progress introduction(long intro) {
    return new Progress(Progress.Mode.NORMAL, BUILDING, intro, false, false, null,
        Collections.emptyMap());
  }

  private static Progress completed(long intro) {
    Progress.Result pass = new Progress.Result("reminder-pass", "reminder-content",
        Map.of("q1", "a1", "q2", "a2", "q3", "a3"), 2);
    return new Progress(Progress.Mode.NORMAL, BUILDING, intro, false, true, null,
        Collections.singletonMap(pass.attemptId, pass));
  }

  private void invokeReceiver(Intent alarm) {
    instrumentation.runOnMainSync(() -> new AuthoredReminderReceiver().onReceive(context, alarm));
  }

  private PendingIntent findTap(Intent entry) {
    return PendingIntent.getActivity(context, 0, entry,
        PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
  }

  private static Notification findNotification(NotificationManager manager, String tag) {
    for (StatusBarNotification active : manager.getActiveNotifications()) {
      if (tag.equals(active.getTag()) && active.getId() == NOTIFICATION_ID) {
        return active.getNotification();
      }
    }
    return null;
  }

  private static Notification awaitNotification(NotificationManager manager, String tag) {
    long deadline = SystemClock.elapsedRealtime() + 5000L;
    do {
      Notification notification = findNotification(manager, tag);
      if (notification != null) return notification;
      SystemClock.sleep(40L);
    } while (SystemClock.elapsedRealtime() < deadline);
    throw new AssertionError("The real receiver did not post the synthetic notification");
  }

  private static void awaitCancellation(NotificationManager manager, String tag) {
    long deadline = SystemClock.elapsedRealtime() + 5000L;
    do {
      if (findNotification(manager, tag) == null) return;
      SystemClock.sleep(40L);
    } while (SystemClock.elapsedRealtime() < deadline);
    throw new AssertionError("The synthetic notification was not cancelled");
  }
}
