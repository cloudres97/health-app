package com.cloudres.health;

import static org.junit.Assert.*;

import android.app.AlertDialog;
import android.view.*;
import android.widget.EditText;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class PlannerUiTest {
  ActivityController<MainActivity> controller;
  MainActivity activity;

  @Before
  public void open() {
    controller = Robolectric.buildActivity(MainActivity.class).setup();
    activity = controller.get();
  }

  @After
  public void close() {
    controller.pause().stop().destroy();
  }

  @Test
  public void allFourScreensAndEditorsOpen() {
    for (int i = 0; i < 4; i++) {
      activity.tab = i;
      activity.render();
      assertTrue(activity.content.getChildCount() > 0);
    }
    PlannerEditor training = new PlannerEditor(activity, true);
    training.dialog.dismiss();
    PlannerEditor food = new PlannerEditor(activity, false);
    food.dialog.dismiss();
  }

  @Test
  public void cancelledDraftPreservesPlanAndBody() throws Exception {
    activity.data.body(activity.data.today(), 67.0, 80.0);
    String original = activity.data.plan.toString();
    PlannerEditor editor = new PlannerEditor(activity, false);
    PlannerEditor.setQuantity(editor.draft.getJSONArray("meals").getJSONObject(0), "rice", 10);
    editor.dialog.dismiss();
    assertEquals(original, activity.data.plan.toString());
    assertEquals(
        67,
        Data.obj(activity.data.state, "body").getJSONObject(activity.data.today()).getDouble("kg"),
        0);
  }

  @Test
  public void savingDraftPersistsAndDoesNotClearRecords() throws Exception {
    activity.data.body(activity.data.today(), 67.0, null);
    PlannerEditor editor = new PlannerEditor(activity, false);
    JSONObject meal = editor.draft.getJSONArray("meals").getJSONObject(0);
    PlannerEditor.setQuantity(meal, "rice", 2);
    editor.save();
    Data restored = new Data(activity);
    JSONObject saved = PlanCoach.find(restored.plan.getJSONArray("meals"), meal.getString("id"));
    boolean found = false;
    JSONArray entries = saved.getJSONArray("items");
    for (int i = 0; i < entries.length(); i++)
      if (entries.getJSONObject(i).getString("foodId").equals("rice")) {
        assertEquals(2, entries.getJSONObject(i).getDouble("quantity"), 0);
        found = true;
      }
    assertTrue(found);
    assertEquals(67, restored.referenceKg(), 0);
  }

  @Test
  public void editingPortionUpdatesPreviewAndSaves() throws Exception {
    PlannerEditor editor = new PlannerEditor(activity, false);
    JSONObject m = editor.meal(), f = PlanCoach.find(editor.draft.getJSONArray("foods"), "rice");
    editor.quantity(m, f);
    Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
    EditText input = findEdit(dialog.getWindow().getDecorView());
    assertNotNull(input);
    input.setText("2.5");
    dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
    Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    JSONArray items = m.getJSONArray("items");
    boolean found = false;
    for (int i = 0; i < items.length(); i++)
      if (items.getJSONObject(i).getString("foodId").equals("rice")) {
        assertEquals(2.5, items.getJSONObject(i).getDouble("quantity"), 0);
        found = true;
      }
    assertTrue(found);
    editor.dialog.dismiss();
  }

  @Test
  public void legacyPlanMigratesWithoutClearingHistory() throws Exception {
    JSONObject legacy = new JSONObject(activity.data.plan.toString());
    legacy.remove("foods");
    for (int i = 0; i < legacy.getJSONArray("meals").length(); i++)
      legacy.getJSONArray("meals").getJSONObject(i).remove("items");
    activity.data.body(activity.data.today(), 67.0, null);
    activity.data.prefs().edit().putString("plan", legacy.toString()).commit();
    Data migrated = new Data(activity);
    assertTrue(migrated.plan.has("foods"));
    assertTrue(PlanCoach.day(migrated.plan).protein > 0);
    assertEquals(67, migrated.referenceKg(), 0);
  }

  @Test
  public void invalidImportCannotReplaceCurrentPlan() throws Exception {
    String before = activity.data.plan.toString();
    JSONObject bad = new JSONObject(before);
    bad.getJSONArray("meals")
        .getJSONObject(0)
        .getJSONArray("items")
        .getJSONObject(0)
        .put("quantity", -2);
    assertThrows(Exception.class, () -> activity.data.importText(bad.toString()));
    assertEquals(before, activity.data.plan.toString());
  }

  @Test
  public void savedTrainingUsesChosenSetsDuringIntroWeek() throws Exception {
    Data.put(activity.data.state, "start", Schedule.monday(java.time.LocalDate.now()).toString());
    PlannerEditor editor = new PlannerEditor(activity, true);
    JSONObject r = editor.routine(),
        e =
            PlanCoach.find(
                editor.draft.getJSONArray("exercises"), r.getJSONArray("exerciseIds").getString(0));
    editor.save();
    JSONObject saved = activity.data.routine(r.getString("id"));
    assertEquals(2, activity.data.setCount(saved, e));
  }

  EditText findEdit(View v) {
    if (v instanceof EditText) return (EditText) v;
    if (v instanceof ViewGroup)
      for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) {
        EditText e = findEdit(((ViewGroup) v).getChildAt(i));
        if (e != null) return e;
      }
    return null;
  }
}
