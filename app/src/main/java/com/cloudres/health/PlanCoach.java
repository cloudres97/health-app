package com.cloudres.health;

import java.util.*;
import org.json.*;

/** Local, explainable planning heuristics. No diagnosis or claim of measured energy needs. */
final class PlanCoach {
  static final class Macro {
    double protein, carbs, fat;

    double kcal() {
      return protein * 4 + carbs * 4 + fat * 9;
    }
  }

  static final class Tip {
    final boolean caution;
    final String text;

    Tip(boolean caution, String text) {
      this.caution = caution;
      this.text = text;
    }
  }

  static JSONObject find(JSONArray array, String id) {
    if (array != null)
      for (int i = 0; i < array.length(); i++) {
        JSONObject o = array.optJSONObject(i);
        if (o != null && id.equals(o.optString("id"))) return o;
      }
    return null;
  }

  static JSONObject prescription(JSONObject routine, JSONObject exercise) {
    JSONObject settings = routine.optJSONObject("prescriptions");
    JSONObject custom = settings == null ? null : settings.optJSONObject(exercise.optString("id"));
    return custom == null ? exercise : custom;
  }

  static int sets(JSONObject r, JSONObject e) {
    return prescription(r, e).optInt("sets", e.optInt("sets", 2));
  }

  static String reps(JSONObject r, JSONObject e) {
    return prescription(r, e).optString("reps", e.optString("reps", "10–12"));
  }

  static int rir(JSONObject r, JSONObject e) {
    return prescription(r, e).optInt("rir", 3);
  }

  static int totalSets(JSONObject plan, JSONObject routine) {
    if (routine == null) return 0;
    int total = 0;
    JSONArray ids = routine.optJSONArray("exerciseIds");
    for (int i = 0; ids != null && i < ids.length(); i++) {
      JSONObject e = find(plan.optJSONArray("exercises"), ids.optString(i));
      if (e != null) total += sets(routine, e);
    }
    return total;
  }

  static int minutes(JSONObject p, JSONObject r) {
    return 5 + totalSets(p, r) * 3;
  }

  static List<Tip> training(JSONObject p, JSONObject r, boolean beginner, int budget) {
    List<Tip> out = new ArrayList<>();
    int total = totalSets(p, r);
    JSONArray ids = r.optJSONArray("exerciseIds");
    int hard = 0;
    Set<String> groups = new HashSet<>();
    for (int i = 0; ids != null && i < ids.length(); i++) {
      JSONObject e = find(p.optJSONArray("exercises"), ids.optString(i));
      if (e == null) continue;
      if (rir(r, e) <= 1) hard += sets(r, e);
      String group = e.optString("group", e.optString("area"));
      if (!group.isEmpty()) groups.add(group);
    }
    if (total == 0) out.add(new Tip(true, "还没有动作，请至少选择一个动作再保存。"));
    if (total > (beginner ? 16 : 24))
      out.add(new Tip(true, "本次共" + total + "组，训练量较多；可减少组数或拆成两次。阈值是本工具的保守提醒，不是受伤界限。"));
    if (hard > 0)
      out.add(
          new Tip(
              beginner,
              "有"
                  + hard
                  + "组只预留0～1次余力，较接近力竭。"
                  + (beginner ? "新手优先预留2～3次，先稳定动作。" : "根据恢复安排，不必每组都力竭。")));
    if (minutes(p, r) > budget)
      out.add(
          new Tip(
              false, "按每组含休息约3分钟估计，约需" + minutes(p, r) + "分钟，超过你的" + budget + "分钟预算；可减少动作或组数。"));
    JSONArray days = p.optJSONArray("trainingDays");
    Set<Integer> d = new HashSet<>();
    if (days != null) for (int i = 0; i < days.length(); i++) d.add(days.optInt(i));
    boolean consecutive = false;
    for (int i = 1; i <= 7; i++) if (d.contains(i) && d.contains(i % 7 + 1)) consecutive = true;
    if (consecutive) out.add(new Tip(false, "存在连续训练日。同一肌群若仍明显酸痛、乏力，调整训练部位或插入恢复日。"));
    if (ids != null && ids.length() > 1 && groups.size() == 1)
      out.add(new Tip(false, "动作主要集中在同一部位。若这是全身计划，考虑补充推、拉和腿部动作。"));
    if (out.isEmpty()) out.add(new Tip(false, "当前安排较精简。按动作质量和恢复加重量，组数少也可以从入门开始。"));
    return out;
  }

  static Macro meal(JSONObject plan, JSONObject meal) {
    Macro m = new Macro();
    JSONArray items = meal.optJSONArray("items");
    JSONArray foods = plan.optJSONArray("foods");
    if (items != null)
      for (int i = 0; i < items.length(); i++) {
        JSONObject item = items.optJSONObject(i);
        JSONObject f = find(foods, item.optString("foodId"));
        if (f == null) continue;
        double n = item.optDouble("quantity", 0);
        m.protein += f.optDouble("protein", 0) * n;
        m.carbs += f.optDouble("carbs", 0) * n;
        m.fat += f.optDouble("fat", 0) * n;
      }
    return m;
  }

  static Macro day(JSONObject plan) {
    Macro out = new Macro();
    JSONArray meals = plan.optJSONArray("meals");
    for (int i = 0; i < meals.length(); i++) {
      Macro m = meal(plan, meals.optJSONObject(i));
      out.protein += m.protein;
      out.carbs += m.carbs;
      out.fat += m.fat;
    }
    return out;
  }

  static double proteinTarget(double kg) {
    return kg * 1.6;
  }

  static double carbTarget(double kg, int sets) {
    return kg * (sets >= 12 ? 4 : 3);
  }

  static List<Tip> nutrition(
      JSONObject plan, double kg, int sets, double customCarbs, double energy) {
    List<Tip> out = new ArrayList<>();
    Macro m = day(plan);
    if (m.kcal() == 0) {
      out.add(new Tip(true, "尚未选择食物，无法评估饮食；添加食物和份量后会自动计算。"));
      return out;
    }
    if (kg <= 0) {
      out.add(new Tip(false, "先填写参考体重，才能按体重给出蛋白质与碳水参考。当前只汇总计划，不判断是否足够。"));
      return out;
    }
    if (m.protein < 1.4 * kg)
      out.add(
          new Tip(
              true,
              String.format(
                  Locale.CHINA,
                  "全天蛋白质约%.0fg，低于运动人群参考下沿%.0fg；可增加一份瘦肉、豆制品、奶或高蛋白酸奶。",
                  m.protein,
                  1.4 * kg)));
    else if (m.protein > 2.2 * kg)
      out.add(new Tip(false, "蛋白质已高于2.2g/kg；对一般增肌未必有额外收益，可检查是否挤占主食和蔬果。不是中毒或疾病预警。"));
    double target = customCarbs > 0 ? customCarbs : carbTarget(kg, sets);
    if (m.carbs < target * 0.75)
      out.add(
          new Tip(
              sets >= 12,
              String.format(
                  Locale.CHINA,
                  "碳水约%.0fg，低于本计划试行目标%.0fg。训练时乏力或恢复差，可增加半碗饭或一份燕麦；低碳饮食者需结合实际调整。",
                  m.carbs,
                  target)));
    else if (m.carbs > target * 1.35)
      out.add(new Tip(false, "碳水高于本计划试行目标。先结合总热量、体重趋势和活动量判断，不因主食多就直接判定不健康。"));
    if (energy > 0 && m.kcal() < energy * .8)
      out.add(new Tip(true, "计划热量低于你自行设定目标的80%。检查是否漏记食物、烹调油或主食，避免把估算当作精确摄入。"));
    if (energy > 0 && m.kcal() > energy * 1.2)
      out.add(new Tip(false, "计划热量超过你设定目标的120%；确认份量，并结合2～3周体重和腰围趋势调整。"));
    if (out.isEmpty()) out.add(new Tip(false, "当前蛋白质与碳水接近计划参考。继续观察力量、体重和恢复，不必追求每天数字完全一致。"));
    return out;
  }

  static void validateExtensions(JSONObject p) throws Exception {
    JSONArray foods = p.optJSONArray("foods");
    Set<String> ids = new HashSet<>();
    if (p.has("foods") && foods == null) throw new Exception("食物库格式不正确");
    if (foods != null) {
      if (foods.length() > 200) throw new Exception("食物数量过多");
      for (int i = 0; i < foods.length(); i++) {
        JSONObject f = foods.getJSONObject(i);
        Data.validId(f, ids);
        if (f.getString("name").isEmpty() || f.getString("unit").isEmpty())
          throw new Exception("食物名称和份量不能为空");
        for (String key : new String[] {"protein", "carbs", "fat"}) {
          double v = f.getDouble(key);
          if (!Double.isFinite(v) || v < 0 || v > 500) throw new Exception("单份营养数值不正确");
        }
      }
    }
    JSONArray meals = p.getJSONArray("meals");
    for (int i = 0; i < meals.length(); i++) {
      JSONArray a = meals.getJSONObject(i).optJSONArray("items");
      if (a == null) continue;
      if (a.length() > 40) throw new Exception("单餐食物过多");
      Set<String> used = new HashSet<>();
      for (int j = 0; j < a.length(); j++) {
        JSONObject item = a.getJSONObject(j);
        double q = item.getDouble("quantity");
        String id = item.getString("foodId");
        if (!ids.contains(id) || !used.add(id) || !Double.isFinite(q) || q <= 0 || q > 20)
          throw new Exception("食物引用或份量不正确");
      }
    }
    JSONArray rs = p.getJSONArray("routines");
    for (int i = 0; i < rs.length(); i++) {
      JSONObject r = rs.getJSONObject(i), ps = r.optJSONObject("prescriptions");
      if (ps == null) continue;
      Set<String> used = new HashSet<>();
      JSONArray es = r.getJSONArray("exerciseIds");
      for (int j = 0; j < es.length(); j++) used.add(es.getString(j));
      Iterator<String> keys = ps.keys();
      while (keys.hasNext()) {
        String id = keys.next();
        JSONObject spec = ps.getJSONObject(id);
        int sets = spec.getInt("sets"), rir = spec.getInt("rir");
        String reps = spec.getString("reps");
        if (!used.contains(id)
            || sets < 1
            || sets > 6
            || rir < 0
            || rir > 5
            || !reps.matches("[0-9]{1,2}([–-][0-9]{1,2})?")) throw new Exception("训练组数、次数或余力不正确");
        String[] parts = reps.split("[–-]");
        int min = Integer.parseInt(parts[0]), max = Integer.parseInt(parts[parts.length - 1]);
        if (min < 1 || max > 50 || min > max) throw new Exception("次数应为1～50且下限不超过上限");
      }
    }
  }
}
