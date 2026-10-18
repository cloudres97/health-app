package com.cloudres.health;

import static org.junit.Assert.*;

import java.nio.file.*;
import java.util.*;
import org.json.*;
import org.junit.Test;

public class CoachTest {
  JSONObject plan() throws Exception {
    return new JSONObject(
        new String(
            Files.readAllBytes(Paths.get("src/main/assets/plan.json")),
            java.nio.charset.StandardCharsets.UTF_8));
  }

  @Test
  public void portionsScaleMacrosWithoutCountingFoodMassAsProtein() throws Exception {
    JSONObject p = plan(), meal = p.getJSONArray("meals").getJSONObject(0);
    meal.put("items", new JSONArray("[{\"foodId\":\"rice\",\"quantity\":1.5}]"));
    PlanCoach.Macro m = PlanCoach.meal(p, meal);
    assertEquals(6, m.protein, .001);
    assertEquals(63, m.carbs, .001);
    assertEquals(.75, m.fat, .001);
    assertEquals(6 * 4 + 63 * 4 + .75 * 9, m.kcal(), .001);
  }

  @Test
  public void changingQuantityReplacesRatherThanDuplicates() throws Exception {
    JSONObject m = new JSONObject();
    PlannerEditor.setQuantity(m, "rice", 1);
    PlannerEditor.setQuantity(m, "rice", 2);
    assertEquals(1, m.getJSONArray("items").length());
    assertEquals(2, m.getJSONArray("items").getJSONObject(0).getDouble("quantity"), 0);
  }

  @Test
  public void draftChangesDoNotMutateOriginal() throws Exception {
    JSONObject original = plan(), draft = new JSONObject(original.toString());
    double before = PlanCoach.day(original).carbs;
    PlannerEditor.setQuantity(draft.getJSONArray("meals").getJSONObject(0), "rice", 3);
    assertEquals(before, PlanCoach.day(original).carbs, 0);
    assertTrue(PlanCoach.day(draft).carbs > before);
  }

  @Test
  public void prescriptionOverridesOnlyItsOwnRoutine() throws Exception {
    JSONObject p = plan(),
        a = p.getJSONArray("routines").getJSONObject(0),
        b = p.getJSONArray("routines").getJSONObject(1),
        e = p.getJSONArray("exercises").getJSONObject(0);
    Data.obj(a, "prescriptions")
        .put(e.getString("id"), new JSONObject("{\"sets\":5,\"reps\":\"6-8\",\"rir\":1}"));
    assertEquals(5, PlanCoach.sets(a, e));
    assertEquals(2, PlanCoach.sets(b, e));
    Data.validatePlan(p);
  }

  @Test
  public void invalidQuantityIsRejected() throws Exception {
    JSONObject p = plan();
    p.getJSONArray("meals")
        .getJSONObject(0)
        .getJSONArray("items")
        .getJSONObject(0)
        .put("quantity", -1);
    assertThrows(Exception.class, () -> Data.validatePlan(p));
  }

  @Test
  public void missingFoodReferencesAreRejected() throws Exception {
    JSONObject p = plan();
    p.getJSONArray("meals")
        .getJSONObject(0)
        .getJSONArray("items")
        .getJSONObject(0)
        .put("foodId", "unknown");
    assertThrows(Exception.class, () -> Data.validatePlan(p));
  }

  @Test
  public void invalidRepRangesAndEffortAreRejected() throws Exception {
    JSONObject p = plan(), r = p.getJSONArray("routines").getJSONObject(0);
    JSONObject s = new JSONObject("{\"sets\":2,\"reps\":\"12-8\",\"rir\":3}");
    Data.obj(r, "prescriptions").put("leg_press", s);
    assertThrows(Exception.class, () -> Data.validatePlan(p));
    s.put("reps", "8-12");
    s.put("rir", 6);
    assertThrows(Exception.class, () -> Data.validatePlan(p));
  }

  @Test
  public void emptyDietDoesNotPretendToBeSufficient() throws Exception {
    JSONObject p = plan();
    for (int i = 0; i < p.getJSONArray("meals").length(); i++)
      p.getJSONArray("meals").getJSONObject(i).put("items", new JSONArray());
    assertTrue(PlanCoach.nutrition(p, 67, 8, 0, 0).get(0).caution);
  }

  @Test
  public void missingWeightDoesNotGeneratePerKgWarnings() throws Exception {
    List<PlanCoach.Tip> tips = PlanCoach.nutrition(plan(), 0, 8, 0, 0);
    assertEquals(1, tips.size());
    assertFalse(tips.get(0).caution);
    assertTrue(tips.get(0).text.contains("体重"));
  }

  @Test
  public void highEffortIsCautionedForBeginners() throws Exception {
    JSONObject p = plan(), r = p.getJSONArray("routines").getJSONObject(0);
    Data.obj(r, "prescriptions")
        .put("leg_press", new JSONObject("{\"sets\":2,\"reps\":\"8-12\",\"rir\":0}"));
    assertTrue(
        PlanCoach.training(p, r, true, 30).stream()
            .anyMatch(t -> t.caution && t.text.contains("力竭")));
  }

  @Test
  public void carbReferenceChangesWithPlannedVolume() {
    assertEquals(201, PlanCoach.carbTarget(67, 8), 0);
    assertEquals(268, PlanCoach.carbTarget(67, 12), 0);
    assertEquals(0, PlanCoach.totalSets(new JSONObject(), null));
  }

  @Test
  public void customCarbTargetIsRespected() throws Exception {
    List<PlanCoach.Tip> low = PlanCoach.nutrition(plan(), 67, 8, 100, 0);
    assertTrue(low.stream().anyMatch(t -> t.text.contains("碳水高于")));
  }

  @Test
  public void newPlanSurvivesJsonRoundtrip() throws Exception {
    JSONObject p = plan();
    Data.validatePlan(new JSONObject(p.toString()));
    assertEquals(PlanCoach.day(p).carbs, PlanCoach.day(new JSONObject(p.toString())).carbs, 0);
  }
}
