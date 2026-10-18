package com.cloudres.health;

import static org.junit.Assert.*;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import org.json.*;
import org.junit.Test;

public class PlanTest {
  JSONObject plan() throws Exception {
    return new JSONObject(
        new String(
            Files.readAllBytes(Paths.get("src/main/assets/plan.json")),
            java.nio.charset.StandardCharsets.UTF_8));
  }

  @Test
  public void bundledPlanIsValid() throws Exception {
    Data.validatePlan(plan());
  }

  @Test
  public void routinesAlternateAcrossWeeks() {
    LocalDate start = LocalDate.of(2026, 10, 19);
    List<Integer> days = Arrays.asList(3, 5, 7);
    assertEquals(-1, Schedule.routineIndex(start, start, days, 2));
    assertEquals(0, Schedule.routineIndex(start, LocalDate.of(2026, 10, 21), days, 2));
    assertEquals(1, Schedule.routineIndex(start, LocalDate.of(2026, 10, 23), days, 2));
    assertEquals(0, Schedule.routineIndex(start, LocalDate.of(2026, 10, 25), days, 2));
    assertEquals(1, Schedule.routineIndex(start, LocalDate.of(2026, 10, 28), days, 2));
    assertEquals(2, Schedule.week(start, LocalDate.of(2026, 10, 26)));
    assertEquals(0, Schedule.week(start, LocalDate.of(2026, 10, 18)));
  }

  @Test
  public void midweekStartDoesNotCountEarlierSessions() {
    assertEquals(
        0,
        Schedule.routineIndex(
            LocalDate.of(2026, 10, 22), LocalDate.of(2026, 10, 23), Arrays.asList(3, 5, 7), 2));
  }

  @Test
  public void scheduleMatchesNaiveCounterForTwoYears() {
    List<Integer> days = Arrays.asList(3, 5, 7);
    for (int offset = 0; offset < 7; offset++) {
      LocalDate start = LocalDate.of(2026, 10, 19).plusDays(offset);
      int sessions = 0;
      for (int d = 0; d < 730; d++) {
        LocalDate day = start.plusDays(d);
        int actual = Schedule.routineIndex(start, day, days, 2);
        if (days.contains(day.getDayOfWeek().getValue())) assertEquals(sessions++ % 2, actual);
        else assertEquals(-1, actual);
      }
    }
  }

  @Test
  public void rejectsMissingExerciseReferences() throws Exception {
    JSONObject p = plan();
    p.getJSONArray("routines").getJSONObject(0).getJSONArray("exerciseIds").put(0, "missing");
    assertThrows(Exception.class, () -> Data.validatePlan(p));
  }

  @Test
  public void rejectsDuplicateDays() throws Exception {
    JSONObject p = plan();
    p.put("trainingDays", new JSONArray("[3,3,7]"));
    assertThrows(Exception.class, () -> Data.validatePlan(p));
  }

  @Test
  public void rejectsInvalidBodyDatesAndValues() throws Exception {
    JSONObject p = plan(),
        state = new JSONObject("{\"start\":\"2026-10-19\",\"body\":{\"2026-10-22\":{\"kg\":-1}}}");
    assertThrows(Exception.class, () -> Data.validateState(state, p));
    state.getJSONObject("body").getJSONObject("2026-10-22").put("kg", 67);
    Data.validateState(state, p);
    state.put("start", "not-a-date");
    assertThrows(Exception.class, () -> Data.validateState(state, p));
  }

  @Test
  public void validSetRejectsDecimalsInRepetitionsAndNonfiniteWeights() {
    assertTrue(MainActivity.validSet("0", "12"));
    assertTrue(MainActivity.validSet("12.5", "10"));
    assertFalse(MainActivity.validSet("NaN", "10"));
    assertFalse(MainActivity.validSet("5", "1.5"));
    assertFalse(MainActivity.validSet("-1", "10"));
    assertFalse(MainActivity.validSet("5", "0"));
  }

  @Test
  public void oversizedImportStopsBeforeParsing() throws Exception {
    assertThrows(
        IOException.class, () -> Data.readLimited(new ByteArrayInputStream(new byte[2001]), 2000));
    assertEquals(16, Data.readLimited(new ByteArrayInputStream(new byte[16]), 2000).length);
  }
}
