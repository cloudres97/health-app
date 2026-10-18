package com.cloudres.health;

import android.app.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import org.json.*;

/** Edits a detached draft; cancel never changes the active plan. */
final class PlannerEditor {
  final MainActivity a;
  final Data data;
  final boolean training;
  final JSONObject draft;
  final Dialog dialog;
  LinearLayout body;
  String routineId, mealId;

  PlannerEditor(MainActivity a, boolean training) {
    this.a = a;
    data = a.data;
    this.training = training;
    try {
      draft = new JSONObject(data.plan.toString());
    } catch (Exception e) {
      throw new IllegalArgumentException(e);
    }
    routineId = a.selected;
    if (PlanCoach.find(draft.optJSONArray("routines"), routineId) == null)
      routineId = draft.optJSONArray("routines").optJSONObject(0).optString("id");
    mealId =
        draft.optJSONArray("meals").length() > 0
            ? draft.optJSONArray("meals").optJSONObject(0).optString("id")
            : "";
    dialog = new Dialog(a);
    dialog.setOnCancelListener(d -> {});
    show();
  }

  JSONObject routine() {
    return PlanCoach.find(draft.optJSONArray("routines"), routineId);
  }

  JSONObject meal() {
    return PlanCoach.find(draft.optJSONArray("meals"), mealId);
  }

  void show() {
    LinearLayout root = a.column();
    root.setPadding(a.dp(18), a.dp(12), a.dp(18), a.dp(12));
    root.setBackgroundColor(MainActivity.BG);
    root.addView(a.bold(training ? "编排训练" : "编排饮食", 21, MainActivity.INK));
    a.gap(root, 4);
    a.paragraph(root, "修改后点击保存生效，取消保留原计划。");
    ScrollView scroll = new ScrollView(a);
    body = a.column();
    scroll.addView(body);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    if (training) renderTraining();
    else renderFood();
    LinearLayout buttons = a.row();
    buttons.addView(
        a.button("取消", false, () -> dialog.dismiss()), new LinearLayout.LayoutParams(0, -2, 1));
    View space = new View(a);
    buttons.addView(space, new LinearLayout.LayoutParams(a.dp(8), 1));
    buttons.addView(a.button("保存计划", true, () -> save()), new LinearLayout.LayoutParams(0, -2, 1));
    root.addView(buttons);
    dialog.setContentView(root);
    dialog.show();
    dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
    dialog
        .getWindow()
        .setLayout(-1, (int) (a.getResources().getDisplayMetrics().heightPixels * .9));
  }

  void save() {
    try {
      if (training) {
        JSONArray routines = draft.optJSONArray("routines");
        for (int i = 0; i < routines.length(); i++)
          Data.put(routines.optJSONObject(i), "customized", true);
      }
      Data.validatePlan(draft);
      data.plan = draft;
      data.save();
      ReminderReceiver.schedule(a);
      a.selected = routineId;
      dialog.dismiss();
      a.render();
      a.toast("计划已保存");
    } catch (Exception e) {
      a.toast(e.getMessage());
    }
  }

  void heading(String title, String description) {
    a.label(body, title, description);
  }

  void renderTraining() {
    heading("每周训练日", "点击选择，可按恢复调整");
    LinearLayout days = a.row();
    JSONArray selectedDays = draft.optJSONArray("trainingDays");
    Set<Integer> selected = new HashSet<>();
    for (int i = 0; i < selectedDays.length(); i++) selected.add(selectedDays.optInt(i));
    for (int day = 1; day <= 7; day++) {
      final int n = day;
      Button b =
          a.button(
              new String[] {"一", "二", "三", "四", "五", "六", "日"}[day - 1],
              selected.contains(day),
              () -> {
                if (selected.contains(n)) selected.remove(n);
                else selected.add(n);
                List<Integer> sorted = new ArrayList<>(selected);
                Collections.sort(sorted);
                Data.put(draft, "trainingDays", new JSONArray(sorted));
                show();
              });
      b.setTextSize(12);
      b.setPadding(0, a.dp(4), 0, a.dp(4));
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, a.dp(44), 1);
      lp.setMargins(a.dp(2), 0, a.dp(2), 0);
      days.addView(b, lp);
    }
    body.addView(days);
    a.gap(body, 16);
    routineTabs();
    JSONObject r = routine();
    LinearLayout tools = a.row();
    tools.addView(
        a.button("名称", false, () -> rename(r, "name")), new LinearLayout.LayoutParams(0, -2, 1));
    tools.addView(
        a.button(
            "新模板",
            false,
            () -> {
              if (draft.optJSONArray("routines").length() >= 8) {
                a.toast("最多8个模板");
                return;
              }
              JSONObject nr = new JSONObject();
              String id = "T" + System.currentTimeMillis();
              Data.put(nr, "id", id);
              Data.put(nr, "name", "新训练");
              Data.put(nr, "exerciseIds", new JSONArray());
              draft.optJSONArray("routines").put(nr);
              routineId = id;
              show();
            }),
        new LinearLayout.LayoutParams(0, -2, 1));
    if (draft.optJSONArray("routines").length() > 1)
      tools.addView(
          a.button(
              "删除模板",
              false,
              () ->
                  confirmDelete(
                      "删除这个训练模板？",
                      () -> {
                        removeById(draft.optJSONArray("routines"), routineId);
                        routineId = draft.optJSONArray("routines").optJSONObject(0).optString("id");
                        show();
                      })),
          new LinearLayout.LayoutParams(0, -2, 1));
    body.addView(tools);
    a.gap(body, 12);
    JSONArray ids = r.optJSONArray("exerciseIds");
    for (int i = 0; i < ids.length(); i++) {
      int position = i;
      String id = ids.optString(i);
      JSONObject e = PlanCoach.find(draft.optJSONArray("exercises"), id);
      LinearLayout c = a.card(body);
      c.addView(a.bold((i + 1) + ". " + e.optString("name"), 16, MainActivity.INK));
      a.paragraph(
          c,
          PlanCoach.sets(r, e)
              + "组 × "
              + PlanCoach.reps(r, e)
              + "次 · 余力"
              + PlanCoach.rir(r, e)
              + "次");
      LinearLayout controls = a.row();
      controls.addView(
          a.button("调整", false, () -> prescription(r, e)), new LinearLayout.LayoutParams(0, -2, 1));
      if (i > 0)
        controls.addView(
            a.button(
                "上移",
                false,
                () -> {
                  swap(ids, position, position - 1);
                  show();
                }),
            new LinearLayout.LayoutParams(0, -2, 1));
      controls.addView(
          a.button(
              "移除",
              false,
              () -> {
                ids.remove(position);
                JSONObject ps = r.optJSONObject("prescriptions");
                if (ps != null) ps.remove(id);
                show();
              }),
          new LinearLayout.LayoutParams(0, -2, 1));
      c.addView(controls);
    }
    a.action(body, "＋ 选择动作", false, () -> chooseExercise());
    tips(body, PlanCoach.training(draft, r, beginner(), budget()));
    a.paragraph(body, "余力=一组结束后还能规范完成的次数。这里评估计划组数，不根据公斤数跨动作比较强度。");
  }

  boolean beginner() {
    return Data.obj(data.state, "profile").optBoolean("beginner", true);
  }

  int budget() {
    return Data.obj(data.state, "profile").optInt("minutes", 30);
  }

  void routineTabs() {
    HorizontalScrollView hs = new HorizontalScrollView(a);
    hs.setHorizontalScrollBarEnabled(false);
    LinearLayout row = a.row();
    JSONArray rs = draft.optJSONArray("routines");
    for (int i = 0; i < rs.length(); i++) {
      JSONObject r = rs.optJSONObject(i);
      String id = r.optString("id");
      Button b =
          a.button(
              r.optString("name"),
              id.equals(routineId),
              () -> {
                routineId = id;
                show();
              });
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, a.dp(44));
      lp.setMargins(0, 0, a.dp(6), a.dp(8));
      row.addView(b, lp);
    }
    hs.addView(row);
    body.addView(hs);
  }

  void chooseExercise() {
    JSONObject r = routine();
    JSONArray catalog = draft.optJSONArray("exercises"), ids = r.optJSONArray("exerciseIds");
    Set<String> used = new HashSet<>();
    for (int i = 0; i < ids.length(); i++) used.add(ids.optString(i));
    List<JSONObject> choices = new ArrayList<>();
    List<String> names = new ArrayList<>();
    for (int i = 0; i < catalog.length(); i++) {
      JSONObject e = catalog.optJSONObject(i);
      if (!used.contains(e.optString("id"))) {
        choices.add(e);
        names.add(e.optString("name") + " · " + e.optString("group", e.optString("area")));
      }
    }
    if (ids.length() >= 12) {
      a.toast("单个模板最多12个动作");
      return;
    }
    if (choices.isEmpty()) {
      a.toast("动作已全部加入");
      return;
    }
    new AlertDialog.Builder(a)
        .setTitle("选择动作")
        .setItems(
            names.toArray(new String[0]),
            (d, w) -> {
              JSONObject e = choices.get(w);
              ids.put(e.optString("id"));
              show();
              prescription(r, e);
            })
        .setNegativeButton("取消", null)
        .show();
  }

  void prescription(JSONObject r, JSONObject e) {
    LinearLayout p = fields();
    a.paragraph(p, "组数1～6；次数或范围1～50；余力0表示接近力竭。");
    EditText sets = field(p, "组数", String.valueOf(PlanCoach.sets(r, e)), true),
        reps = field(p, "次数，例如8–12", PlanCoach.reps(r, e), false),
        rir = field(p, "余力次数（0～5）", String.valueOf(PlanCoach.rir(r, e)), true);
    LinearLayout preview = a.column();
    p.addView(preview);
    Runnable change =
        () -> {
          preview.removeAllViews();
          try {
            JSONObject candidate = new JSONObject(draft.toString());
            JSONObject cr = PlanCoach.find(candidate.optJSONArray("routines"), r.optString("id"));
            JSONObject spec = spec(sets, reps, rir);
            Data.put(Data.obj(cr, "prescriptions"), e.optString("id"), spec);
            for (PlanCoach.Tip t : PlanCoach.training(candidate, cr, beginner(), budget()))
              a.paragraph(preview, (t.caution ? "注意 · " : "建议 · ") + t.text);
          } catch (Exception ex) {
            a.paragraph(preview, "请填写有效组数、次数与余力。");
          }
        };
    watch(change, sets, reps, rir);
    change.run();
    AlertDialog dialog =
        new AlertDialog.Builder(a)
            .setTitle(e.optString("name"))
            .setView(wrap(p))
            .setPositiveButton("应用", null)
            .setNegativeButton("取消", null)
            .create();
    dialog.setOnShowListener(
        v ->
            dialog
                .getButton(-1)
                .setOnClickListener(
                    b -> {
                      try {
                        JSONObject candidate = new JSONObject(draft.toString());
                        JSONObject cr =
                            PlanCoach.find(candidate.optJSONArray("routines"), r.optString("id"));
                        JSONObject s = spec(sets, reps, rir);
                        Data.put(Data.obj(cr, "prescriptions"), e.optString("id"), s);
                        Data.validatePlan(candidate);
                        Data.put(Data.obj(r, "prescriptions"), e.optString("id"), s);
                        dialog.dismiss();
                        show();
                      } catch (Exception ex) {
                        a.toast("组数1～6，次数1～50（如8–12），余力0～5");
                      }
                    }));
    dialog.show();
  }

  JSONObject spec(EditText sets, EditText reps, EditText rir) throws Exception {
    JSONObject s = new JSONObject();
    s.put("sets", Integer.parseInt(sets.getText().toString()));
    s.put("reps", reps.getText().toString().trim());
    s.put("rir", Integer.parseInt(rir.getText().toString()));
    return s;
  }

  void renderFood() {
    PlanCoach.Macro total = PlanCoach.day(draft);
    heading(
        "全天计划",
        String.format(
            Locale.CHINA,
            "蛋白质 %.0fg  ·  碳水 %.0fg  ·  约%.0f千卡",
            total.protein,
            total.carbs,
            total.kcal()));
    HorizontalScrollView hs = new HorizontalScrollView(a);
    LinearLayout row = a.row();
    JSONArray meals = draft.optJSONArray("meals");
    for (int i = 0; i < meals.length(); i++) {
      JSONObject m = meals.optJSONObject(i);
      String id = m.optString("id");
      Button b =
          a.button(
              m.optString("time", m.optString("title")),
              id.equals(mealId),
              () -> {
                mealId = id;
                show();
              });
      row.addView(b);
    }
    hs.addView(row);
    body.addView(hs);
    a.gap(body, 10);
    JSONObject m = meal();
    if (m == null) {
      a.action(body, "＋ 添加一餐", false, () -> newMeal());
      return;
    }
    LinearLayout controls = a.row();
    controls.addView(
        a.button("餐名", false, () -> renameMeal(m)), new LinearLayout.LayoutParams(0, -2, 1));
    controls.addView(
        a.button("加一餐", false, () -> newMeal()), new LinearLayout.LayoutParams(0, -2, 1));
    if (meals.length() > 1)
      controls.addView(
          a.button(
              "删除这餐",
              false,
              () ->
                  confirmDelete(
                      "删除这餐及其食物？",
                      () -> {
                        removeById(meals, mealId);
                        mealId = meals.optJSONObject(0).optString("id");
                        show();
                      })),
          new LinearLayout.LayoutParams(0, -2, 1));
    body.addView(controls);
    a.gap(body, 12);
    JSONArray items = Data.arr(m, "items");
    for (int i = 0; i < items.length(); i++) {
      int index = i;
      JSONObject item = items.optJSONObject(i),
          f = PlanCoach.find(draft.optJSONArray("foods"), item.optString("foodId"));
      LinearLayout c = a.card(body);
      c.addView(
          a.bold(
              f.optString("name") + " × " + format(item.optDouble("quantity")),
              16,
              MainActivity.INK));
      a.paragraph(c, f.optString("unit"));
      LinearLayout actions = a.row();
      actions.addView(
          a.button("改份量", false, () -> quantity(m, f)), new LinearLayout.LayoutParams(0, -2, 1));
      actions.addView(
          a.button(
              "移除",
              false,
              () -> {
                items.remove(index);
                show();
              }),
          new LinearLayout.LayoutParams(0, -2, 1));
      c.addView(actions);
    }
    a.action(body, "＋ 选择食物", false, () -> chooseFood(m));
    a.action(body, "＋ 按标签自定义食物", false, () -> customFood(m));
    JSONObject profile = Data.obj(data.state, "profile");
    JSONObject r = todayRoutine(draft);
    tips(
        body,
        PlanCoach.nutrition(
            draft,
            data.referenceKg(),
            PlanCoach.totalSets(draft, r),
            profile.optDouble("carbTarget", 0),
            profile.optDouble("energy", 0)));
    a.paragraph(body, "这里计算的是计划，不是实测摄入。食堂用油、品牌和份量会影响结果；可按包装标签自定义单份营养。");
  }

  static String format(double n) {
    return String.format(Locale.CHINA, "%.2f", n).replaceAll("0+$", "").replaceAll("\\.$", "");
  }

  void chooseFood(JSONObject meal) {
    JSONArray fs = draft.optJSONArray("foods");
    String[] names = new String[fs.length()];
    for (int i = 0; i < fs.length(); i++) {
      JSONObject f = fs.optJSONObject(i);
      names[i] = f.optString("name") + " · " + f.optString("unit");
    }
    new AlertDialog.Builder(a)
        .setTitle("选择食物")
        .setItems(names, (d, w) -> quantity(meal, fs.optJSONObject(w)))
        .setNegativeButton("取消", null)
        .show();
  }

  void quantity(JSONObject meal, JSONObject food) {
    JSONObject old = null;
    JSONArray items = Data.arr(meal, "items");
    for (int i = 0; i < items.length(); i++)
      if (items.optJSONObject(i).optString("foodId").equals(food.optString("id")))
        old = items.optJSONObject(i);
    final JSONObject existing = old;
    LinearLayout p = fields();
    a.paragraph(p, "1份 = " + food.optString("unit") + "\n可填0.5、1、1.5等；直接替换该食物份数，不会重复添加。");
    EditText q = field(p, "份数（0.1～20）", old == null ? "1" : old.optString("quantity"), true);
    LinearLayout preview = a.column();
    p.addView(preview);
    Runnable change =
        () -> {
          preview.removeAllViews();
          try {
            double count = validNumber(q, .1, 20);
            JSONObject candidate = new JSONObject(draft.toString());
            JSONObject cm = PlanCoach.find(candidate.optJSONArray("meals"), meal.optString("id"));
            setQuantity(cm, food.optString("id"), count);
            PlanCoach.Macro macro = PlanCoach.day(candidate);
            a.paragraph(
                preview,
                String.format(
                    Locale.CHINA,
                    "全天计划：蛋白质%.0fg / 碳水%.0fg / 约%.0f千卡",
                    macro.protein,
                    macro.carbs,
                    macro.kcal()));
            JSONObject pro = Data.obj(data.state, "profile"), r = todayRoutine(candidate);
            tips(
                preview,
                PlanCoach.nutrition(
                    candidate,
                    data.referenceKg(),
                    PlanCoach.totalSets(candidate, r),
                    pro.optDouble("carbTarget", 0),
                    pro.optDouble("energy", 0)));
          } catch (Exception ex) {
            a.paragraph(preview, "请输入0.1～20份。");
          }
        };
    watch(change, q);
    change.run();
    AlertDialog d =
        new AlertDialog.Builder(a)
            .setTitle(food.optString("name"))
            .setView(wrap(p))
            .setPositiveButton("应用", null)
            .setNegativeButton("取消", null)
            .create();
    d.setOnShowListener(
        v ->
            d.getButton(-1)
                .setOnClickListener(
                    b -> {
                      try {
                        double n = validNumber(q, .1, 20);
                        setQuantity(meal, food.optString("id"), n);
                        d.dismiss();
                        show();
                      } catch (Exception ex) {
                        a.toast("份数应在0.1～20之间");
                      }
                    }));
    d.show();
  }

  static void setQuantity(JSONObject meal, String foodId, double n) {
    JSONArray items = Data.arr(meal, "items");
    for (int i = 0; i < items.length(); i++) {
      JSONObject item = items.optJSONObject(i);
      if (item.optString("foodId").equals(foodId)) {
        Data.put(item, "quantity", n);
        return;
      }
    }
    JSONObject item = new JSONObject();
    Data.put(item, "foodId", foodId);
    Data.put(item, "quantity", n);
    items.put(item);
  }

  JSONObject todayRoutine(JSONObject plan) {
    JSONObject today = data.routineFor(java.time.LocalDate.now());
    return today == null
        ? null
        : PlanCoach.find(plan.optJSONArray("routines"), today.optString("id"));
  }

  void customFood(JSONObject meal) {
    LinearLayout p = fields();
    EditText name = field(p, "食物名称", "", false),
        unit = field(p, "1份大小，例如一盒200g", "", false),
        protein = field(p, "每份蛋白质 g", "", true),
        carbs = field(p, "每份碳水 g", "", true),
        fat = field(p, "每份脂肪 g", "", true);
    a.paragraph(p, "营养表若按100g标示，整盒200g的数值需乘2。请填每一份的营养。");
    AlertDialog d =
        new AlertDialog.Builder(a)
            .setTitle("自定义食物")
            .setView(wrap(p))
            .setPositiveButton("添加并使用", null)
            .setNegativeButton("取消", null)
            .create();
    d.setOnShowListener(
        v ->
            d.getButton(-1)
                .setOnClickListener(
                    b -> {
                      try {
                        if (draft.optJSONArray("foods").length() >= 200)
                          throw new Exception("最多200种食物");
                        String n = name.getText().toString().trim(),
                            u = unit.getText().toString().trim();
                        if (n.isEmpty() || u.isEmpty()) throw new Exception("请填写名称和单份大小");
                        JSONObject f = new JSONObject();
                        String id = "F" + System.currentTimeMillis();
                        f.put("id", id);
                        f.put("name", n);
                        f.put("unit", u);
                        f.put("protein", validNumber(protein, 0, 500));
                        f.put("carbs", validNumber(carbs, 0, 500));
                        f.put("fat", validNumber(fat, 0, 500));
                        draft.optJSONArray("foods").put(f);
                        setQuantity(meal, id, 1);
                        d.dismiss();
                        show();
                      } catch (Exception ex) {
                        a.toast(ex.getMessage());
                      }
                    }));
    d.show();
  }

  void newMeal() {
    if (draft.optJSONArray("meals").length() >= 8) {
      a.toast("最多8餐");
      return;
    }
    JSONObject m = new JSONObject();
    String id = "M" + System.currentTimeMillis();
    Data.put(m, "id", id);
    Data.put(m, "title", "自选餐");
    Data.put(m, "time", "自选餐");
    Data.put(m, "description", "自选食物与份量");
    Data.put(m, "items", new JSONArray());
    draft.optJSONArray("meals").put(m);
    mealId = id;
    show();
    renameMeal(m);
  }

  void renameMeal(JSONObject meal) {
    LinearLayout p = fields();
    EditText name = field(p, "餐名", meal.optString("time", meal.optString("title")), false);
    new AlertDialog.Builder(a)
        .setTitle("修改餐名")
        .setView(p)
        .setPositiveButton(
            "应用",
            (d, w) -> {
              String n = name.getText().toString().trim();
              if (n.isEmpty()) {
                a.toast("名称不能为空");
                return;
              }
              Data.put(meal, "time", n);
              Data.put(meal, "title", n);
              show();
            })
        .setNegativeButton("取消", null)
        .show();
  }

  void rename(JSONObject o, String key) {
    LinearLayout p = fields();
    EditText name = field(p, "模板名称", o.optString(key), false);
    new AlertDialog.Builder(a)
        .setTitle("修改名称")
        .setView(p)
        .setPositiveButton(
            "应用",
            (d, w) -> {
              String n = name.getText().toString().trim();
              if (!n.isEmpty()) {
                Data.put(o, key, n);
                show();
              }
            })
        .setNegativeButton("取消", null)
        .show();
  }

  void tips(LinearLayout parent, List<PlanCoach.Tip> tips) {
    for (PlanCoach.Tip t : tips) {
      TextView text =
          a.text(
              (t.caution ? "注意 · " : "建议 · ") + t.text,
              13,
              t.caution ? 0xFF995200 : MainActivity.MUTED);
      text.setPadding(a.dp(12), a.dp(10), a.dp(12), a.dp(10));
      text.setBackground(a.shape(t.caution ? 0xFFFFF2DD : MainActivity.PALE, 10));
      parent.addView(text);
      a.gap(parent, 6);
    }
  }

  LinearLayout fields() {
    LinearLayout p = a.column();
    p.setPadding(a.dp(20), a.dp(8), a.dp(20), a.dp(8));
    return p;
  }

  EditText field(LinearLayout p, String label, String value, boolean numeric) {
    p.addView(a.text(label, 13, MainActivity.MUTED));
    EditText e = numeric ? a.number(label, value) : new EditText(a);
    if (!numeric) {
      e.setText(value);
      e.setSingleLine();
    }
    p.addView(e);
    a.gap(p, 8);
    return e;
  }

  ScrollView wrap(LinearLayout p) {
    ScrollView s = new ScrollView(a);
    s.addView(p);
    return s;
  }

  static double validNumber(EditText e, double min, double max) throws Exception {
    double n;
    try {
      n = Double.parseDouble(e.getText().toString());
    } catch (Exception ex) {
      throw new Exception("请填写有效数字");
    }
    if (!Double.isFinite(n) || n < min || n > max)
      throw new Exception("数值应在" + min + "～" + max + "之间");
    return n;
  }

  void watch(Runnable change, EditText... fields) {
    for (EditText e : fields)
      e.addTextChangedListener(
          new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int count, int after) {}

            public void onTextChanged(CharSequence s, int st, int before, int count) {
              change.run();
            }

            public void afterTextChanged(Editable e) {}
          });
  }

  void confirmDelete(String title, Runnable run) {
    new AlertDialog.Builder(a)
        .setTitle(title)
        .setPositiveButton("删除", (d, w) -> run.run())
        .setNegativeButton("取消", null)
        .show();
  }

  static void removeById(JSONArray array, String id) {
    for (int i = 0; i < array.length(); i++)
      if (array.optJSONObject(i).optString("id").equals(id)) {
        array.remove(i);
        return;
      }
  }

  static void swap(JSONArray array, int a, int b) {
    try {
      Object x = array.get(a);
      array.put(a, array.get(b));
      array.put(b, x);
    } catch (Exception e) {
      throw new IllegalArgumentException(e);
    }
  }
}
