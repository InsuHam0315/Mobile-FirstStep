package com.example.firststep.game.notifications;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.Build;
import com.example.firststep.R;
import com.example.firststep.game.ui.intro.IntroQuizActivity;

public final class IntroQuizReminderReceiver extends BroadcastReceiver {
  public static final String BUILDING = "digital_information_software";

  public static Uri identity(String mode, String building) {
    return new Uri.Builder()
        .scheme("firststep-h04")
        .authority("reminder")
        .appendPath(mode)
        .appendPath(building)
        .build();
  }

  public static void schedule(Context c, String mode, String building, long when) {
    Intent i =
        new Intent(c, IntroQuizReminderReceiver.class)
            .setData(identity(mode, building))
            .putExtra("mode", mode)
            .putExtra("building", building);
    PendingIntent pi =
        PendingIntent.getBroadcast(
            c, 0, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    ((AlarmManager) c.getSystemService(Context.ALARM_SERVICE))
        .set(AlarmManager.RTC_WAKEUP, when, pi);
  }

  @Override
  public void onReceive(Context c, Intent i) {
    String mode = i.getStringExtra("mode"), building = i.getStringExtra("building");
    if (!BUILDING.equals(building)
        || !("NORMAL".equals(mode) || "DEMO".equals(mode))
        || !identity(mode, building).equals(i.getData())) return;
    NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
    if (Build.VERSION.SDK_INT >= 26)
      nm.createNotificationChannel(
          new NotificationChannel(
              "h04_reminder",
              c.getString(R.string.game_intro_notification_1),
              NotificationManager.IMPORTANCE_DEFAULT));
    Intent open =
        new Intent(c, IntroQuizActivity.class)
            .setData(identity(mode, building))
            .putExtra("game_intro_building_id", building)
            .putExtra("game_intro_mode", mode)
            .putExtra("game_intro_notification_entry", true);
    PendingIntent tap =
        PendingIntent.getActivity(
            c, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    Notification.Builder b =
        Build.VERSION.SDK_INT >= 26
            ? new Notification.Builder(c, "h04_reminder")
            : new Notification.Builder(c);
    try {
      nm.notify(
          identity(mode, building).toString(),
          1,
          b.setSmallIcon(android.R.drawable.ic_dialog_info)
              .setContentTitle(c.getString(R.string.game_intro_notification_2))
              .setContentText(c.getString(R.string.game_intro_notification_3))
              .setContentIntent(tap)
              .setAutoCancel(true)
              .build());
    } catch (SecurityException ignored) {
    }
  }
}
