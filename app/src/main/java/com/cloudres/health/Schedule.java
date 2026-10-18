package com.cloudres.health;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

/** Pure date logic: training day numbers use ISO Monday=1, Sunday=7. */
public final class Schedule {
  private Schedule() {}

  public static LocalDate monday(LocalDate date) {
    return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
  }

  public static int week(LocalDate start, LocalDate date) {
    if (date.isBefore(start)) return 0;
    return (int) ChronoUnit.WEEKS.between(monday(start), monday(date)) + 1;
  }

  public static int routineIndex(LocalDate start, LocalDate date, List<Integer> days, int count) {
    if (date.isBefore(start) || !days.contains(date.getDayOfWeek().getValue())) return -1;
    long total = 0;
    long elapsed = ChronoUnit.DAYS.between(start, date);
    total += (elapsed / 7) * days.size();
    LocalDate cursor = start.plusDays((elapsed / 7) * 7);
    for (; cursor.isBefore(date); cursor = cursor.plusDays(1))
      if (days.contains(cursor.getDayOfWeek().getValue())) total++;
    return (int) (total % count);
  }
}
