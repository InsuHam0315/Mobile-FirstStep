package com.example.firststep.game.notifications.authored;

import android.Manifest;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import com.example.firststep.game.sync.DatasetRegistry;
import com.example.firststep.game.sync.SessionOwner;
import com.example.firststep.game.sync.SyncScope;
import java.util.Objects;
import java.util.UUID;

public final class AuthoredReminderAndroid {
  static final String ACTION_ENTRY = "com.example.firststep.AUTHORED_REMINDER_ENTRY";
  static final String ACTION_ALARM = "com.example.firststep.AUTHORED_REMINDER_ALARM";
  private static final String SCHEME = "firststep-authored-reminder";
  static final ReminderTickets TICKETS = new ReminderTickets(System::currentTimeMillis);
  private AuthoredReminderAndroid() {}

  public static AuthoredReminder create(Context context, DatasetRegistry registry, SessionOwner owner) {
    Context app = Objects.requireNonNull(context).getApplicationContext();
    Objects.requireNonNull(registry); Objects.requireNonNull(owner);
    return (scope, committed) -> TICKETS.schedule(scope, committed, owner,
        registry::requireEligible, new ReminderTickets.Delivery() {
          public boolean permitted() { return notificationsAllowed(app); }
          public void schedule(String token, long at) {
            AlarmManager alarm = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
            if (alarm == null) throw new IllegalStateException("Alarm unavailable");
            Intent intent = new Intent(app, AuthoredReminderReceiver.class)
                .setAction(ACTION_ALARM).setData(uri(token));
            PendingIntent pending = PendingIntent.getBroadcast(app, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            alarm.set(AlarmManager.RTC_WAKEUP, at, pending);
          }
        });
  }

  public static boolean isReminderEntry(Intent intent) {
    return intent != null && (ACTION_ENTRY.equals(intent.getAction())
        || (intent.getData() != null && SCHEME.equals(intent.getData().getScheme())));
  }

  public static SyncScope requireCurrentEntry(Intent intent) {
    if (intent == null || !ACTION_ENTRY.equals(intent.getAction()))
      throw new IllegalArgumentException("Not a reminder entry");
    return TICKETS.valid(token(intent)).scope;
  }

  static boolean notificationsAllowed(Context context) {
    if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
        != PackageManager.PERMISSION_GRANTED) return false;
    NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    return manager != null && manager.areNotificationsEnabled();
  }

  static Uri uri(String token) {
    return new Uri.Builder().scheme(SCHEME).authority("ticket").appendPath(token).build();
  }

  static String token(Intent intent) {
    Uri data = intent == null ? null : intent.getData();
    if (data == null || !SCHEME.equals(data.getScheme()) || !"ticket".equals(data.getAuthority())
        || data.getPathSegments().size() != 1 || data.getQuery() != null || data.getFragment() != null)
      throw new IllegalArgumentException("Malformed reminder ticket");
    String token = data.getPathSegments().get(0);
    try {
      if (!UUID.fromString(token).toString().equals(token)) throw new IllegalArgumentException();
    } catch (RuntimeException invalid) { throw new IllegalArgumentException("Malformed reminder ticket", invalid); }
    return token;
  }
}
