package com.cloudres.health;

import android.app.*;
import android.content.*;
import java.time.*;

/** Inexact alarms deliberately avoid exact-alarm and persistent-service permissions. */
public class ReminderReceiver extends BroadcastReceiver {
  static final String CHANNEL = "health-reminders";

  static void channel(Context c) {
    NotificationManager nm = c.getSystemService(NotificationManager.class);
    nm.createNotificationChannel(
        new NotificationChannel(CHANNEL, "训练与起身提醒", NotificationManager.IMPORTANCE_DEFAULT));
  }

  static PendingIntent pending(Context c, String action, int id) {
    return PendingIntent.getBroadcast(
        c,
        id,
        new Intent(c, ReminderReceiver.class).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
  }

  static void schedule(Context c) {
    Data d = new Data(c);
    AlarmManager am = c.getSystemService(AlarmManager.class);
    channel(c);
    for (int id : new int[] {1, 2}) am.cancel(pending(c, id == 1 ? "desk" : "workout", id));
    if (!allowed(c)) return;
    ZonedDateTime now = ZonedDateTime.now();
    if (d.state.optBoolean("deskReminder")) {
      int interval = Math.max(15, Math.min(180, d.state.optInt("interval", 45)));
      long paused = d.state.optLong("pausedUntil", 0);
      ZonedDateTime next = now.plusMinutes(interval);
      if (next.toInstant().toEpochMilli() < paused)
        next = Instant.ofEpochMilli(paused).atZone(ZoneId.systemDefault());
      int start = d.state.optInt("reminderStart", 9), end = d.state.optInt("reminderEnd", 18);
      for (int i = 0; i < 9; i++) {
        int day = next.getDayOfWeek().getValue();
        if (day > 5) {
          next = next.plusDays(1).withHour(start).withMinute(0).withSecond(0);
          continue;
        }
        if (next.getHour() < start) next = next.withHour(start).withMinute(0).withSecond(0);
        if (next.getHour() >= end) {
          next = next.plusDays(1).withHour(start).withMinute(0).withSecond(0);
          continue;
        }
        if (next.getHour() == 12) {
          next = next.withHour(13).withMinute(0).withSecond(0);
        }
        break;
      }
      am.setAndAllowWhileIdle(
          AlarmManager.RTC_WAKEUP, next.toInstant().toEpochMilli(), pending(c, "desk", 1));
    }
    if (d.state.optBoolean("workoutReminder")) {
      ZonedDateTime next =
          now.withHour(d.state.optInt("workoutHour", 18)).withMinute(30).withSecond(0).withNano(0);
      if (!next.isAfter(now)) next = next.plusDays(1);
      for (int i = 0; i < 15; i++) {
        if (d.routineFor(next.toLocalDate()) != null) break;
        if (next.toLocalDate().isBefore(d.start()))
          next = d.start().atTime(next.toLocalTime()).atZone(next.getZone());
        else next = next.plusDays(1);
      }
      am.setAndAllowWhileIdle(
          AlarmManager.RTC_WAKEUP, next.toInstant().toEpochMilli(), pending(c, "workout", 2));
    }
  }

  static boolean allowed(Context c) {
    return c.getSystemService(NotificationManager.class).areNotificationsEnabled();
  }

  @Override
  public void onReceive(Context c, Intent intent) {
    String action = intent.getAction();
    if ("pause".equals(action)) {
      Data d = new Data(c);
      Data.put(d.state, "pausedUntil", System.currentTimeMillis() + 60 * 60 * 1000);
      d.save();
      c.getSystemService(NotificationManager.class).cancel(11);
      schedule(c);
      return;
    }
    Data d = new Data(c);
    if (allowed(c) && ("desk".equals(action) || "workout".equals(action))) {
      boolean desk = "desk".equals(action);
      ZonedDateTime now = ZonedDateTime.now();
      boolean inWindow =
          now.getDayOfWeek().getValue() <= 5
              && now.getHour() >= d.state.optInt("reminderStart", 9)
              && now.getHour() < d.state.optInt("reminderEnd", 18)
              && now.getHour() != 12
              && System.currentTimeMillis() >= d.state.optLong("pausedUntil", 0);
      boolean trainingToday = d.routineFor(LocalDate.now()) != null && !d.trained(d.today());
      if ((desk && d.state.optBoolean("deskReminder") && inWindow)
          || (!desk && d.state.optBoolean("workoutReminder") && trainingToday)) {
        channel(c);
        PendingIntent open =
            PendingIntent.getActivity(
                c,
                0,
                new Intent(c, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b =
            new Notification.Builder(c, CHANNEL)
                .setSmallIcon(com.cloudres.health.R.drawable.ic_launcher)
                .setContentTitle(desk ? "起来活动一下" : "今天有一场30分钟训练")
                .setContentText(desk ? "走动2～3分钟，放松肩颈，给眼睛休息。" : "打开日日有练，查看今天的动作和上次重量。")
                .setContentIntent(open)
                .setAutoCancel(true);
        if (desk)
          b.addAction(
              new Notification.Action.Builder(null, "暂停1小时", pending(c, "pause", 3)).build());
        c.getSystemService(NotificationManager.class).notify(desk ? 11 : 12, b.build());
      }
    }
    schedule(c);
  }
}
