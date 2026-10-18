package com.cloudres.health;

import android.content.Context;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import org.json.*;

final class Data {
  private final Context context;
  JSONObject state, plan;

  Data(Context c) {
    context = c.getApplicationContext();
    try {
      String saved = prefs().getString("state", "{}");
      state = new JSONObject(saved);
      String custom = prefs().getString("plan", null);
      if (custom == null) {
        try (java.io.InputStream input = c.getAssets().open("plan.json")) {
          custom = new String(readLimited(input, 2_000_000), StandardCharsets.UTF_8);
        }
      }
      plan = new JSONObject(custom);
      validatePlan(plan);
      upgradePlanner();
    } catch (Exception e) {
      throw new IllegalStateException("无法读取本地计划", e);
    }
    if (!state.has("start")) put(state, "start", Schedule.monday(LocalDate.now()).toString());
  }

  android.content.SharedPreferences prefs() {
    return context.getSharedPreferences("health-v1", Context.MODE_PRIVATE);
  }

  void save() {
    prefs().edit().putString("state", state.toString()).putString("plan", plan.toString()).apply();
  }

  private void upgradePlanner() throws Exception {
    JSONObject bundled;
    try (java.io.InputStream in = context.getAssets().open("plan.json")) {
      bundled = new JSONObject(new String(readLimited(in, 2_000_000), StandardCharsets.UTF_8));
    }
    if (!plan.has("foods")) {
      put(plan, "foods", bundled.getJSONArray("foods"));
      JSONArray meals = plan.getJSONArray("meals");
      for (int i = 0; i < meals.length(); i++) {
        JSONObject m = meals.getJSONObject(i);
        JSONObject original = PlanCoach.find(bundled.getJSONArray("meals"), m.optString("id"));
        if (!m.has("items")
            && original != null
            && m.optString("title").equals(original.optString("title")))
          put(m, "items", original.getJSONArray("items"));
      }
    }
    JSONArray catalog = bundled.getJSONArray("exercises"),
        existing = plan.getJSONArray("exercises");
    for (int i = 0; i < catalog.length(); i++) {
      JSONObject e = catalog.getJSONObject(i);
      JSONObject old = PlanCoach.find(existing, e.optString("id"));
      if (old == null && existing.length() < 100) existing.put(e);
      else if (old != null && !old.has("group")) put(old, "group", e.optString("group"));
    }
    validatePlan(plan);
  }

  int setCount(JSONObject r, JSONObject e) {
    JSONObject p = r.optJSONObject("prescriptions");
    if (r.optBoolean("customized") || (p != null && p.has(e.optString("id"))))
      return PlanCoach.sets(r, e);
    return week(LocalDate.now()) <= 1 ? 1 : e.optInt("sets", 2);
  }

  double referenceKg() {
    double manual = obj(state, "profile").optDouble("weight", 0);
    if (manual > 0) return manual;
    JSONObject body = obj(state, "body");
    List<String> dates = new ArrayList<>();
    Iterator<String> it = body.keys();
    while (it.hasNext()) {
      String d = it.next();
      if (d.compareTo(today()) <= 0) dates.add(d);
    }
    dates.sort(Collections.reverseOrder());
    for (String d : dates) {
      double kg = body.optJSONObject(d).optDouble("kg", 0);
      if (kg > 0) return kg;
    }
    return 0;
  }

  static void put(JSONObject o, String k, Object v) {
    try {
      o.put(k, v);
    } catch (JSONException e) {
      throw new IllegalArgumentException(e);
    }
  }

  static byte[] readLimited(java.io.InputStream input, int limit) throws java.io.IOException {
    java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
    byte[] buffer = new byte[8192];
    int read;
    while ((read = input.read(buffer)) != -1) {
      if (bytes.size() + read > limit) throw new java.io.IOException("文件不能超过2MB");
      bytes.write(buffer, 0, read);
    }
    return bytes.toByteArray();
  }

  static JSONObject obj(JSONObject o, String k) {
    JSONObject v = o.optJSONObject(k);
    if (v == null) {
      v = new JSONObject();
      put(o, k, v);
    }
    return v;
  }

  static JSONArray arr(JSONObject o, String k) {
    JSONArray v = o.optJSONArray(k);
    if (v == null) {
      v = new JSONArray();
      put(o, k, v);
    }
    return v;
  }

  LocalDate start() {
    try {
      return LocalDate.parse(state.optString("start"));
    } catch (Exception e) {
      return Schedule.monday(LocalDate.now());
    }
  }

  List<Integer> days() {
    List<Integer> r = new ArrayList<>();
    JSONArray a = plan.optJSONArray("trainingDays");
    for (int i = 0; i < a.length(); i++) r.add(a.optInt(i));
    return r;
  }

  int week(LocalDate d) {
    return Schedule.week(start(), d);
  }

  JSONObject routineFor(LocalDate d) {
    int i = Schedule.routineIndex(start(), d, days(), plan.optJSONArray("routines").length());
    return i < 0 ? null : plan.optJSONArray("routines").optJSONObject(i);
  }

  JSONObject exercise(String id) {
    JSONArray a = plan.optJSONArray("exercises");
    for (int i = 0; i < a.length(); i++) {
      JSONObject e = a.optJSONObject(i);
      if (e.optString("id").equals(id)) return e;
    }
    return null;
  }

  JSONObject routine(String id) {
    JSONArray a = plan.optJSONArray("routines");
    for (int i = 0; i < a.length(); i++)
      if (a.optJSONObject(i).optString("id").equals(id)) return a.optJSONObject(i);
    return null;
  }

  String today() {
    return LocalDate.now().toString();
  }

  boolean checked(String id) {
    return obj(obj(state, "checks"), today()).optBoolean(id);
  }

  void check(String id, boolean b) {
    put(obj(obj(state, "checks"), today()), id, b);
    save();
  }

  JSONObject workout(String date, String routine) {
    return obj(obj(obj(state, "workouts"), date), routine);
  }

  JSONObject set(String routine, String exercise, int index) {
    JSONArray a = arr(workout(today(), routine), exercise);
    while (a.length() <= index) a.put(new JSONObject());
    return a.optJSONObject(index);
  }

  String previous(String exercise) {
    JSONObject history = obj(state, "workouts");
    List<String> dates = new ArrayList<>();
    Iterator<String> it = history.keys();
    while (it.hasNext()) {
      String d = it.next();
      if (d.compareTo(today()) < 0) dates.add(d);
    }
    dates.sort(Collections.reverseOrder());
    for (String date : dates) {
      JSONObject routines = history.optJSONObject(date);
      Iterator<String> r = routines.keys();
      while (r.hasNext()) {
        JSONObject w = routines.optJSONObject(r.next());
        if (w == null) continue;
        JSONArray sets = w.optJSONArray(exercise);
        if (sets == null) continue;
        for (int i = 0; i < sets.length(); i++) {
          JSONObject s = sets.optJSONObject(i);
          if (s != null && s.optBoolean("done"))
            return date + "  ·  " + s.optString("kg", "0") + "kg × " + s.optString("reps", "—");
        }
      }
    }
    return "首次记录 · 先用轻重量找动作";
  }

  void finish(String id) {
    put(workout(today(), id), "finished", true);
    save();
  }

  boolean trained(String date) {
    JSONObject rs = obj(state, "workouts").optJSONObject(date);
    if (rs == null) return false;
    Iterator<String> it = rs.keys();
    while (it.hasNext()) {
      JSONObject w = rs.optJSONObject(it.next());
      if (w != null && w.optBoolean("finished")) return true;
    }
    return false;
  }

  void body(String date, Double kg, Double waist) {
    JSONObject entries = obj(state, "body");
    JSONObject v = entries.optJSONObject(date);
    if (v == null) v = new JSONObject();
    if (kg != null) put(v, "kg", kg);
    if (waist != null) put(v, "waist", waist);
    put(entries, date, v);
    save();
  }

  String export() throws JSONException {
    JSONObject all = new JSONObject();
    put(all, "format", "health-app-backup");
    put(all, "version", 1);
    put(all, "plan", plan);
    put(all, "state", state);
    return all.toString(2);
  }

  void importText(String text) throws Exception {
    if (text.length() > 2_000_000) throw new Exception("文件超过2MB");
    JSONObject root = new JSONObject(text);
    JSONObject oldPlan = plan, oldState = state;
    try {
      if (root.has("format")) {
        if (!root.optString("format").equals("health-app-backup") || root.optInt("version") != 1)
          throw new Exception("不支持的备份格式");
        JSONObject p = root.getJSONObject("plan"), s = root.getJSONObject("state");
        validatePlan(p);
        validateState(s, p);
        plan = p;
        state = s;
      } else {
        validatePlan(root);
        plan = root;
      }
      upgradePlanner();
      save();
    } catch (Exception e) {
      plan = oldPlan;
      state = oldState;
      throw e;
    }
  }

  static void validatePlan(JSONObject p) throws Exception {
    if (p.optInt("schemaVersion") != 1) throw new Exception("计划版本必须为1");
    String[] texts = {"title", "guidance", "nutrition", "recovery"};
    for (String k : texts)
      if (p.optString(k).isEmpty() || p.optString(k).length() > 10000)
        throw new Exception("缺少或过长字段：" + k);
    JSONArray days = p.getJSONArray("trainingDays");
    Set<Integer> unique = new HashSet<>();
    if (days.length() < 1 || days.length() > 7) throw new Exception("训练日数量不正确");
    for (int i = 0; i < days.length(); i++) {
      int d = days.getInt(i);
      if (d < 1 || d > 7 || !unique.add(d)) throw new Exception("训练日应为不重复的1～7");
    }
    Set<String> ids = new HashSet<>();
    JSONArray es = p.getJSONArray("exercises");
    if (es.length() < 1 || es.length() > 100) throw new Exception("动作数量不正确");
    for (int i = 0; i < es.length(); i++) {
      JSONObject e = es.getJSONObject(i);
      validId(e, ids);
      if (e.getString("name").isEmpty()
          || e.getString("reps").isEmpty()
          || e.getInt("sets") < 1
          || e.getInt("sets") > 6) throw new Exception("动作字段不正确");
      JSONArray steps = e.getJSONArray("steps");
      if (steps.length() < 1 || steps.length() > 12) throw new Exception("动作步骤数量不正确");
      for (int j = 0; j < steps.length(); j++) steps.getString(j);
    }
    JSONArray rs = p.getJSONArray("routines");
    if (rs.length() < 1 || rs.length() > 14) throw new Exception("训练模板数量不正确");
    Set<String> rids = new HashSet<>();
    for (int i = 0; i < rs.length(); i++) {
      JSONObject r = rs.getJSONObject(i);
      validId(r, rids);
      r.getString("name");
      JSONArray a = r.getJSONArray("exerciseIds");
      if (a.length() < 1 || a.length() > 12) throw new Exception("单次动作数量不正确");
      Set<String> used = new HashSet<>();
      for (int j = 0; j < a.length(); j++)
        if (!ids.contains(a.getString(j)) || !used.add(a.getString(j)))
          throw new Exception("动作引用重复或不存在");
    }
    for (String key : new String[] {"meals", "dailyTasks"}) {
      JSONArray a = p.getJSONArray(key);
      if (a.length() > 20) throw new Exception("任务过多");
      Set<String> names = new HashSet<>();
      for (int i = 0; i < a.length(); i++) {
        JSONObject t = a.getJSONObject(i);
        validId(t, names);
        t.getString("title");
        t.getString("description");
      }
    }
    PlanCoach.validateExtensions(p);
  }

  static void validId(JSONObject o, Set<String> ids) throws Exception {
    String id = o.getString("id");
    if (!id.matches("[A-Za-z0-9_]{1,40}") || !ids.add(id)) throw new Exception("ID不正确或重复");
  }

  static void validateState(JSONObject s, JSONObject p) throws Exception {
    LocalDate.parse(s.getString("start"));
    JSONObject profile = s.optJSONObject("profile");
    if (profile != null)
      for (String k : new String[] {"weight", "carbTarget", "energy", "minutes"})
        if (profile.has(k)) {
          double n = profile.getDouble(k);
          double max =
              k.equals("energy")
                  ? 10000
                  : k.equals("carbTarget") ? 1000 : k.equals("minutes") ? 180 : 350;
          if (!Double.isFinite(n) || n < 0 || n > max) throw new Exception("计划参考设置数值不正确");
        }
    JSONObject body = s.optJSONObject("body");
    if (body != null) {
      Iterator<String> it = body.keys();
      while (it.hasNext()) {
        String d = it.next();
        LocalDate.parse(d);
        JSONObject b = body.getJSONObject(d);
        for (String k : new String[] {"kg", "waist"})
          if (b.has(k)) {
            double v = b.getDouble(k);
            if (!Double.isFinite(v) || v <= 0 || v > 500) throw new Exception("身体记录数值不正确");
          }
      }
    }
    for (String key : new String[] {"checks", "workouts"})
      if (s.has(key)) {
        JSONObject dates = s.getJSONObject(key);
        Iterator<String> it = dates.keys();
        while (it.hasNext()) {
          String d = it.next();
          LocalDate.parse(d);
          JSONObject entry = dates.getJSONObject(d);
          Iterator<String> ks = entry.keys();
          while (ks.hasNext()) {
            String k = ks.next();
            if (key.equals("checks")) {
              entry.getBoolean(k);
              continue;
            }
            JSONObject w = entry.getJSONObject(k);
            Iterator<String> es = w.keys();
            while (es.hasNext()) {
              String e = es.next();
              if (e.equals("finished")) {
                w.getBoolean(e);
                continue;
              }
              JSONArray sets = w.getJSONArray(e);
              if (sets.length() > 20) throw new Exception("训练组数过多");
              for (int j = 0; j < sets.length(); j++) sets.getJSONObject(j);
            }
          }
        }
      }
    for (String k : new String[] {"reminderStart", "reminderEnd", "workoutHour"})
      if (s.has(k) && (s.getInt(k) < 0 || s.getInt(k) > 23)) throw new Exception("提醒时间不正确");
    if (s.has("interval") && (s.getInt("interval") < 15 || s.getInt("interval") > 180))
      throw new Exception("提醒间隔应为15～180分钟");
  }
}
