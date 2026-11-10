package com.example.firststep.game.notifications.authored;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import com.example.firststep.R;
import com.example.firststep.game.ui.authored.AuthoredEpisodeActivity;

/** Non-exported alarm receiver; a cold process has no ticket and drops the alarm. */
public final class AuthoredReminderReceiver extends BroadcastReceiver {
  @Override public void onReceive(Context context, Intent intent) {
    try {
      if (intent == null || !AuthoredReminderAndroid.ACTION_ALARM.equals(intent.getAction())) return;
      String token = AuthoredReminderAndroid.token(intent);
      ReminderTickets.Ticket ticket = AuthoredReminderAndroid.TICKETS.valid(token);
      ticket.owner.whileCurrent(ticket.scope, () -> {
        AuthoredReminderAndroid.TICKETS.valid(token);
        if (!AuthoredReminderAndroid.notificationsAllowed(context)) return null;
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return null;
        String channel = "authored_quiz_reminders";
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(new NotificationChannel(
            channel, context.getString(R.string.authored_reminder_channel), NotificationManager.IMPORTANCE_DEFAULT));
        Intent entry = AuthoredEpisodeActivity.intent(context)
            .setAction(AuthoredReminderAndroid.ACTION_ENTRY).setData(AuthoredReminderAndroid.uri(token));
        PendingIntent tap = PendingIntent.getActivity(context, 0, entry,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
            ? new Notification.Builder(context, channel) : new Notification.Builder(context);
        manager.notify(token, 1, builder.setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(context.getString(R.string.authored_reminder_title))
            .setContentText(context.getString(R.string.authored_reminder_text))
            .setContentIntent(tap).setAutoCancel(true).build());
        return null;
      });
    } catch (RuntimeException ignored) {
      // Best effort: revoked sessions, malformed tickets, and platform failures are dropped.
    }
  }
}
