package com.cloudres.health;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.json.*;

public class MainActivity extends Activity {
  static final int BG = 0xFFF6F8F7,
      WHITE = 0xFFFFFFFF,
      INK = 0xFF20372E,
      MUTED = 0xFF607166,
      GREEN = 0xFF237B5C,
      PALE = 0xFFE7F0E6,
      LINE = 0xFFE0E6DC;
  Data data;
  LinearLayout root, content, nav;
  ScrollView scroll;
  int tab = 0;
  String selected = "A";
  final Handler handler = new Handler(Looper.getMainLooper());
  TextView timerLabel, progressLabel;
  long timerEnd;
  Runnable timerTick;
  String pendingImport;

  @Override
  public void onCreate(Bundle saved) {
    super.onCreate(saved);
    data = new Data(this);
    if (saved != null) {
      tab = saved.getInt("tab");
      selected = saved.getString("routine", "A");
    } else {
      JSONObject r = data.routineFor(LocalDate.now());
      if (r != null) selected = r.optString("id");
    }
    timerEnd = data.state.optLong("restEnd", 0);
    render();
    ReminderReceiver.schedule(this);
  }

  @Override
  protected void onSaveInstanceState(Bundle out) {
    out.putInt("tab", tab);
    out.putString("routine", selected);
    super.onSaveInstanceState(out);
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (data != null) {
      render();
      ReminderReceiver.schedule(this);
    }
  }

  @Override
  protected void onDestroy() {
    handler.removeCallbacksAndMessages(null);
    super.onDestroy();
  }

  int dp(float n) {
    return Math.round(n * getResources().getDisplayMetrics().density);
  }

  GradientDrawable shape(int color, int radius) {
    GradientDrawable g = new GradientDrawable();
    g.setColor(color);
    g.setCornerRadius(dp(radius));
    return g;
  }

  TextView text(String s, int size, int color) {
    TextView t = new TextView(this);
    t.setText(s);
    t.setTextSize(size);
    t.setTextColor(color);
    t.setLineSpacing(dp(3), 1);
    return t;
  }

  TextView bold(String s, int size, int color) {
    TextView t = text(s, size, color);
    t.setTypeface(null, Typeface.BOLD);
    return t;
  }

  LinearLayout column() {
    LinearLayout l = new LinearLayout(this);
    l.setOrientation(LinearLayout.VERTICAL);
    return l;
  }

  LinearLayout row() {
    LinearLayout l = new LinearLayout(this);
    l.setOrientation(LinearLayout.HORIZONTAL);
    l.setGravity(Gravity.CENTER_VERTICAL);
    return l;
  }

  void gap(LinearLayout p, int h) {
    View v = new View(this);
    p.addView(v, new LinearLayout.LayoutParams(1, dp(h)));
  }

  void paragraph(LinearLayout p, String s) {
    TextView t = text(s, 14, MUTED);
    p.addView(t);
    gap(p, 8);
  }

  LinearLayout card(LinearLayout p) {
    LinearLayout l = column();
    l.setPadding(dp(14), dp(12), dp(14), dp(12));
    l.setBackground(shape(WHITE, 12));
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.bottomMargin = dp(8);
    p.addView(l, lp);
    return l;
  }

  void label(LinearLayout p, String title, String subtitle) {
    p.addView(bold(title, 21, INK));
    gap(p, 4);
    if (!subtitle.isEmpty()) p.addView(text(subtitle, 13, MUTED));
    gap(p, 14);
  }

  Button button(String title, boolean primary, Runnable run) {
    Button b = new Button(this);
    b.setText(title);
    b.setAllCaps(false);
    b.setTextSize(14);
    b.setTypeface(null, Typeface.BOLD);
    b.setTextColor(primary ? WHITE : GREEN);
    b.setBackground(shape(primary ? GREEN : PALE, 14));
    b.setMinHeight(dp(46));
    b.setMinimumHeight(dp(46));
    b.setPadding(dp(12), dp(8), dp(12), dp(8));
    b.setOnClickListener(v -> run.run());
    return b;
  }

  void action(LinearLayout p, String title, boolean primary, Runnable run) {
    p.addView(button(title, primary, run), new LinearLayout.LayoutParams(-1, -2));
    gap(p, 8);
  }

  void render() {
    handler.removeCallbacksAndMessages(null);
    timerLabel = null;
    progressLabel = null;
    root = column();
    root.setBackgroundColor(BG);
    if (Build.VERSION.SDK_INT >= 30) {
      getWindow().setDecorFitsSystemWindows(false);
      root.setOnApplyWindowInsetsListener(
          (v, insets) -> {
            Insets i =
                insets.getInsets(
                    WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout()
                        | WindowInsets.Type.ime());
            v.setPadding(i.left, i.top, i.right, i.bottom);
            return insets;
          });
    }
    setContentView(root);
    LinearLayout header = row();
    header.setPadding(dp(20), dp(14), dp(16), dp(10));
    TextView brand = bold("日日有练", 19, INK);
    header.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
    Button settings = button("设置", false, () -> settings());
    header.addView(settings, new LinearLayout.LayoutParams(dp(66), dp(40)));
    root.addView(header);
    scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    scroll.setClipToPadding(false);
    content = column();
    content.setPadding(dp(20), dp(8), dp(20), dp(20));
    scroll.addView(content);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    nav = row();
    nav.setPadding(dp(8), dp(8), dp(8), dp(8));
    nav.setBackgroundColor(WHITE);
    String[] tabs = {"今天", "训练", "饮食", "记录"};
    for (int i = 0; i < tabs.length; i++) {
      final int at = i;
      Button b =
          button(
              tabs[i],
              i == tab,
              () -> {
                tab = at;
                render();
              });
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(48), 1);
      lp.setMargins(dp(3), 0, dp(3), 0);
      nav.addView(b, lp);
    }
    root.addView(nav);
    if (tab == 0) today();
    else if (tab == 1) training();
    else if (tab == 2) food();
    else records();
    if (timerEnd > System.currentTimeMillis()) startTicker();
  }

  String dateLabel(LocalDate date) {
    return date.format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA));
  }

  void today() {
    LocalDate now = LocalDate.now();
    JSONObject routine = data.routineFor(now);
    label(content, dateLabel(now), "第" + Math.max(1, data.week(now)) + "周");
    LinearLayout focus = card(content);
    focus.addView(bold(routine == null ? "恢复日" : routine.optString("name"), 24, INK));
    paragraph(
        focus,
        routine == null
            ? "正常饮食，饭后走一走。"
            : routine.optJSONArray("exerciseIds").length()
                + "个动作 · 常规"
                + PlanCoach.totalSets(data.plan, routine)
                + "组");
    action(
        focus,
        routine == null ? "查看训练" : "开始训练",
        true,
        () -> {
          if (routine != null) selected = routine.optString("id");
          tab = 1;
          render();
        });
    LinearLayout overview = card(content);
    progressLabel = bold("", 16, INK);
    overview.addView(progressLabel);
    updateProgress();
    PlanCoach.Macro macro = PlanCoach.day(data.plan);
    paragraph(
        overview,
        String.format(Locale.CHINA, "全天计划 · 蛋白质 %.0fg / 碳水 %.0fg", macro.protein, macro.carbs));
    LinearLayout checklist = card(content);
    if (routine != null)
      paragraph(checklist, data.trained(data.today()) ? "✓ 今天训练已保存" : "训练完成后，在训练页结束并保存。");
    for (String category : new String[] {"meals", "dailyTasks"}) {
      JSONArray arr = data.plan.optJSONArray(category);
      for (int i = 0; i < arr.length(); i++) {
        JSONObject t = arr.optJSONObject(i);
        String id = category + "_" + t.optString("id");
        CheckBox box = new CheckBox(this);
        box.setText(
            category.equals("meals")
                ? t.optString("time", t.optString("title"))
                : t.optString("title"));
        box.setTextSize(15);
        box.setTextColor(INK);
        box.setMinHeight(dp(48));
        box.setChecked(data.checked(id));
        box.setButtonTintList(android.content.res.ColorStateList.valueOf(GREEN));
        box.setOnCheckedChangeListener(
            (v, b) -> {
              data.check(id, b);
              updateProgress();
            });
        checklist.addView(box);
      }
    }
    LinearLayout actions = row();
    actions.addView(
        button("体重 / 腰围", false, () -> bodyDialog()), new LinearLayout.LayoutParams(0, -2, 1));
    actions.addView(
        button("参考设置", false, () -> profileDialog()), new LinearLayout.LayoutParams(0, -2, 1));
    content.addView(actions);
  }

  void updateProgress() {
    if (progressLabel == null) return;
    int done = 0, total = 0;
    for (String k : new String[] {"meals", "dailyTasks"}) {
      JSONArray a = data.plan.optJSONArray(k);
      for (int i = 0; i < a.length(); i++) {
        total++;
        if (data.checked(k + "_" + a.optJSONObject(i).optString("id"))) done++;
      }
    }
    if (data.routineFor(LocalDate.now()) != null) {
      total++;
      if (data.trained(data.today())) done++;
    }
    progressLabel.setText(String.format(Locale.CHINA, "今日完成  %d / %d", done, total));
  }

  void checkCard(LinearLayout parent, String id, String title, String description) {
    LinearLayout c = card(parent);
    CheckBox box = new CheckBox(this);
    box.setText(title);
    box.setTextSize(16);
    box.setTextColor(INK);
    box.setButtonTintList(android.content.res.ColorStateList.valueOf(GREEN));
    box.setChecked(data.checked(id));
    box.setPadding(0, 0, 0, dp(4));
    c.addView(box);
    paragraph(c, description);
    box.setOnCheckedChangeListener(
        (v, checked) -> {
          data.check(id, checked);
          updateProgress();
        });
  }

  void training() {
    label(content, "训练", "动作与组数按你的计划执行");
    LinearLayout edit = row();
    edit.addView(
        button("编排训练", false, () -> new PlannerEditor(this, true)),
        new LinearLayout.LayoutParams(0, -2, 1));
    edit.addView(
        button("参考设置", false, () -> profileDialog()), new LinearLayout.LayoutParams(0, -2, 1));
    content.addView(edit);
    gap(content, 12);
    JSONArray routines = data.plan.optJSONArray("routines");
    if (data.routine(selected) == null) selected = routines.optJSONObject(0).optString("id");
    HorizontalScrollView hs = new HorizontalScrollView(this);
    hs.setHorizontalScrollBarEnabled(false);
    LinearLayout selectors = row();
    for (int i = 0; i < routines.length(); i++) {
      JSONObject r = routines.optJSONObject(i);
      String id = r.optString("id");
      Button b =
          button(
              r.optString("name"),
              id.equals(selected),
              () -> {
                selected = id;
                render();
              });
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(44));
      lp.setMargins(0, 0, dp(8), dp(8));
      selectors.addView(b, lp);
    }
    hs.addView(selectors);
    content.addView(hs);
    JSONObject routine = data.routine(selected);
    JSONObject pro = Data.obj(data.state, "profile");
    List<PlanCoach.Tip> advice =
        PlanCoach.training(
            data.plan, routine, pro.optBoolean("beginner", true), pro.optInt("minutes", 30));
    LinearLayout summary = card(content);
    paragraph(
        summary,
        PlanCoach.totalSets(data.plan, routine)
            + "组 · 常规约"
            + PlanCoach.minutes(data.plan, routine)
            + "分钟（粗估）");
    advicePreview(summary, advice, () -> showAdvice("训练建议", advice));
    action(
        summary,
        "热身与动作规则",
        false,
        () -> info("训练规则", data.plan.optString("guidance") + "\n\n自行设置的组数和余力优先执行，不会再自动减成首周1组。"));
    LinearLayout timer = card(content);
    timerLabel = bold("组间休息", 16, INK);
    timer.addView(timerLabel);
    gap(timer, 8);
    LinearLayout options = row();
    for (int secs : new int[] {60, 90})
      options.addView(
          button(secs + "秒", false, () -> beginRest(secs)),
          new LinearLayout.LayoutParams(0, -2, 1));
    options.addView(
        button(
            "停止",
            false,
            () -> {
              timerEnd = 0;
              Data.put(data.state, "restEnd", 0);
              data.save();
              if (timerTick != null) handler.removeCallbacks(timerTick);
              timerLabel.setText("组间休息");
            }),
        new LinearLayout.LayoutParams(0, -2, 1));
    timer.addView(options);
    JSONArray ids = routine.optJSONArray("exerciseIds");
    for (int i = 0; i < ids.length(); i++) {
      JSONObject ex = data.exercise(ids.optString(i));
      LinearLayout c = card(content);
      LinearLayout head = row();
      head.addView(bold(ex.optString("name"), 17, INK), new LinearLayout.LayoutParams(0, -2, 1));
      head.addView(
          button("动作", false, () -> exerciseDialog(ex)),
          new LinearLayout.LayoutParams(dp(62), dp(40)));
      c.addView(head);
      int sets = data.setCount(routine, ex);
      paragraph(
          c,
          sets
              + "组 × "
              + PlanCoach.reps(routine, ex)
              + "次 · 余力"
              + PlanCoach.rir(routine, ex)
              + "次");
      c.addView(text(data.previous(ex.optString("id")), 12, MUTED));
      gap(c, 8);
      for (int n = 0; n < sets; n++) setRow(c, selected, ex.optString("id"), n);
    }
    action(content, "结束并保存训练", true, () -> finishWorkout());
  }

  void setRow(LinearLayout parent, String rid, String eid, int index) {
    JSONObject set = data.set(rid, eid, index);
    LinearLayout r = row();
    TextView no = text("第" + (index + 1) + "组", 13, MUTED);
    r.addView(no, new LinearLayout.LayoutParams(dp(46), -2));
    EditText kg = number("kg", set.optString("kg", "")),
        reps = number("次数", set.optString("reps", ""));
    kg.setContentDescription("第" + (index + 1) + "组重量公斤");
    reps.setContentDescription("第" + (index + 1) + "组次数");
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(48), 1);
    lp.setMargins(dp(3), 0, dp(3), 0);
    r.addView(kg, lp);
    LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(0, dp(48), 1);
    lp2.setMargins(dp(3), 0, dp(3), 0);
    r.addView(reps, lp2);
    CheckBox done = new CheckBox(this);
    done.setContentDescription("第" + (index + 1) + "组完成");
    done.setChecked(set.optBoolean("done"));
    done.setButtonTintList(android.content.res.ColorStateList.valueOf(GREEN));
    r.addView(done, new LinearLayout.LayoutParams(dp(48), dp(48)));
    TextWatcher save =
        new TextWatcher() {
          public void beforeTextChanged(CharSequence s, int st, int c, int a) {}

          public void onTextChanged(CharSequence s, int st, int before, int count) {
            Data.put(set, "kg", kg.getText().toString());
            Data.put(set, "reps", reps.getText().toString());
            if (set.optBoolean("done")) {
              Data.put(set, "done", false);
              done.setChecked(false);
              Data.put(data.workout(data.today(), rid), "finished", false);
            }
            data.save();
          }

          public void afterTextChanged(Editable e) {}
        };
    kg.addTextChangedListener(save);
    reps.addTextChangedListener(save);
    done.setOnCheckedChangeListener(
        (v, checked) -> {
          if (checked && !validSet(kg.getText().toString(), reps.getText().toString())) {
            done.setChecked(false);
            toast("填写重量与次数：徒手可填0kg，次数为1～100");
            return;
          }
          Data.put(set, "done", checked);
          if (!checked) Data.put(data.workout(data.today(), rid), "finished", false);
          data.save();
          if (checked) beginRest(90);
        });
    parent.addView(r);
    gap(parent, 6);
  }

  static boolean validSet(String kg, String reps) {
    try {
      double k = Double.parseDouble(kg);
      int r = Integer.parseInt(reps);
      return Double.isFinite(k) && k >= 0 && k <= 1000 && r >= 1 && r <= 100;
    } catch (Exception e) {
      return false;
    }
  }

  EditText number(String hint, String value) {
    EditText e = new EditText(this);
    e.setSingleLine();
    e.setTextSize(15);
    e.setTextColor(INK);
    e.setHint(hint);
    e.setText(value);
    e.setInputType(
        android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
    e.setSelectAllOnFocus(true);
    return e;
  }

  void finishWorkout() {
    JSONObject r = data.routine(selected);
    JSONArray ids = r.optJSONArray("exerciseIds");
    int complete = 0, expected = 0;
    for (int i = 0; i < ids.length(); i++) {
      JSONObject e = data.exercise(ids.optString(i));
      int count = data.setCount(r, e);
      for (int j = 0; j < count; j++) {
        expected++;
        if (data.set(selected, e.optString("id"), j).optBoolean("done")) complete++;
      }
    }
    if (complete == 0) {
      toast("先记录并完成至少一组训练");
      return;
    }
    final int completed = complete, total = expected;
    new AlertDialog.Builder(this)
        .setTitle(complete == expected ? "训练完成，做得不错" : "保存这次部分训练？")
        .setMessage("已完成 " + completed + " / " + total + " 组。记录会保存在今天，未做的组保持未完成。")
        .setPositiveButton(
            "保存训练",
            (d, w) -> {
              data.finish(selected);
              timerEnd = 0;
              Data.put(data.state, "restEnd", 0);
              data.save();
              tab = 0;
              render();
              toast("今天的训练已保存");
            })
        .setNegativeButton("继续训练", null)
        .show();
  }

  void exerciseDialog(JSONObject ex) {
    StringBuilder s = new StringBuilder();
    JSONArray steps = ex.optJSONArray("steps");
    for (int i = 0; i < steps.length(); i++)
      s.append(i + 1).append(". ").append(steps.optString(i)).append("\n\n");
    s.append("注意\n")
        .append(ex.optString("caution"))
        .append("\n\n第一次使用器械，请现场工作人员调整座椅、挡垫和安全限位。规范动作比重量更重要。");
    info(ex.optString("name"), s.toString());
  }

  void beginRest(int sec) {
    timerEnd = System.currentTimeMillis() + sec * 1000L;
    Data.put(data.state, "restEnd", timerEnd);
    data.save();
    startTicker();
  }

  void startTicker() {
    if (timerTick != null) handler.removeCallbacks(timerTick);
    timerTick =
        new Runnable() {
          public void run() {
            long left = Math.max(0, (timerEnd - System.currentTimeMillis() + 999) / 1000);
            if (timerLabel != null)
              timerLabel.setText(
                  left > 0
                      ? String.format(Locale.CHINA, "组间休息  %02d:%02d", left / 60, left % 60)
                      : "休息结束 · 准备好再继续");
            if (left > 0) handler.postDelayed(this, 500);
            else {
              timerEnd = 0;
              Data.put(data.state, "restEnd", 0);
              data.save();
              try {
                ToneGenerator tone = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 45);
                tone.startTone(ToneGenerator.TONE_PROP_BEEP, 180);
                handler.postDelayed(tone::release, 250);
              } catch (Exception ignored) {
              }
            }
          }
        };
    handler.post(timerTick);
  }

  void food() {
    label(content, "饮食", "份量估算，按餐执行");
    LinearLayout actions = row();
    actions.addView(
        button("编排饮食", false, () -> new PlannerEditor(this, false)),
        new LinearLayout.LayoutParams(0, -2, 1));
    actions.addView(
        button("参考设置", false, () -> profileDialog()), new LinearLayout.LayoutParams(0, -2, 1));
    content.addView(actions);
    gap(content, 12);
    PlanCoach.Macro total = PlanCoach.day(data.plan);
    LinearLayout summary = card(content);
    summary.addView(
        bold(
            String.format(Locale.CHINA, "蛋白质 %.0fg   碳水 %.0fg", total.protein, total.carbs),
            20,
            INK));
    paragraph(
        summary, String.format(Locale.CHINA, "全天计划约 %.0f 千卡 · 含脂肪 %.0fg", total.kcal(), total.fat));
    double kg = data.referenceKg();
    JSONObject profile = Data.obj(data.state, "profile"), r = data.routineFor(LocalDate.now());
    int sets = r == null ? 0 : PlanCoach.totalSets(data.plan, r);
    double carb =
        profile.optDouble("carbTarget", 0) > 0
            ? profile.optDouble("carbTarget")
            : PlanCoach.carbTarget(kg, sets);
    if (kg > 0)
      paragraph(
          summary,
          String.format(
              Locale.CHINA, "参考 %.1fkg · 蛋白质约%.0f～%.0fg · 碳水试行%.0fg", kg, kg * 1.4, kg * 2, carb));
    else paragraph(summary, "设置参考体重后，显示按体重计算的建议。");
    List<PlanCoach.Tip> tips =
        PlanCoach.nutrition(
            data.plan,
            kg,
            sets,
            profile.optDouble("carbTarget", 0),
            profile.optDouble("energy", 0));
    advicePreview(summary, tips, () -> showAdvice("饮食建议", tips));
    JSONArray meals = data.plan.optJSONArray("meals");
    PlanCoach.Macro checked = new PlanCoach.Macro();
    for (int i = 0; i < meals.length(); i++) {
      JSONObject m = meals.optJSONObject(i);
      PlanCoach.Macro macro = PlanCoach.meal(data.plan, m);
      String id = "meals_" + m.optString("id");
      LinearLayout c = card(content);
      CheckBox box = new CheckBox(this);
      box.setText(m.optString("time", m.optString("title")));
      box.setTextSize(17);
      box.setTextColor(INK);
      box.setChecked(data.checked(id));
      box.setButtonTintList(android.content.res.ColorStateList.valueOf(GREEN));
      c.addView(box);
      StringBuilder items = new StringBuilder();
      JSONArray list = m.optJSONArray("items");
      if (list != null)
        for (int j = 0; j < list.length(); j++) {
          JSONObject item = list.optJSONObject(j),
              f = PlanCoach.find(data.plan.optJSONArray("foods"), item.optString("foodId"));
          if (f != null) {
            if (items.length() > 0) items.append(" · ");
            items
                .append(f.optString("name"))
                .append("×")
                .append(PlannerEditor.format(item.optDouble("quantity")));
          }
        }
      TextView brief = text(items.length() == 0 ? "尚未选择食物" : items.toString(), 14, MUTED);
      brief.setMaxLines(2);
      brief.setEllipsize(TextUtils.TruncateAt.END);
      c.addView(brief);
      gap(c, 6);
      paragraph(
          c,
          String.format(
              Locale.CHINA,
              "蛋白质%.0fg / 碳水%.0fg / 约%.0f千卡",
              macro.protein,
              macro.carbs,
              macro.kcal()));
      brief.setOnClickListener(v -> mealDetails(m));
      box.setOnCheckedChangeListener(
          (v, b) -> {
            data.check(id, b);
          });
      if (data.checked(id)) {
        checked.protein += macro.protein;
        checked.carbs += macro.carbs;
      }
      TextView details = text("份量详情 ›", 13, GREEN);
      details.setPadding(0, dp(8), 0, dp(8));
      details.setOnClickListener(v -> mealDetails(m));
      c.addView(details);
    }
    action(
        content,
        "份量与食物选择说明",
        false,
        () ->
            info(
                "份量与数据说明",
                "米饭按普通平口碗；肉鱼按不含手指的手掌估计；蔬菜约两拳。\n\n"
                    + "内置食物营养是常见份量的粗估，食堂用油和品牌有差异。可以按包装标签新增食物；热量由蛋白质、碳水和脂肪按4/4/9估算，不包括额外酒精等来源。\n\n"
                    + "这里汇总的是计划，按餐打卡也不代表实测吃下的数量。\n\n"
                    + data.plan.optString("nutrition")));
  }

  void mealDetails(JSONObject m) {
    StringBuilder s = new StringBuilder();
    JSONArray list = m.optJSONArray("items");
    if (list != null)
      for (int i = 0; i < list.length(); i++) {
        JSONObject item = list.optJSONObject(i),
            f = PlanCoach.find(data.plan.optJSONArray("foods"), item.optString("foodId"));
        if (f != null)
          s.append(f.optString("name"))
              .append(" × ")
              .append(PlannerEditor.format(item.optDouble("quantity")))
              .append("份\n1份 = ")
              .append(f.optString("unit"))
              .append("\n\n");
      }
    s.append(m.optString("description")).append("\n\n").append(m.optString("tip"));
    info(m.optString("time", m.optString("title")), s.toString());
  }

  void advicePreview(LinearLayout p, List<PlanCoach.Tip> tips, Runnable open) {
    PlanCoach.Tip first = tips.get(0);
    for (PlanCoach.Tip t : tips)
      if (t.caution) {
        first = t;
        break;
      }
    TextView hint =
        text(
            (first.caution ? "注意 · " : "建议 · ") + first.text,
            13,
            first.caution ? 0xFF995200 : MUTED);
    hint.setMaxLines(2);
    hint.setEllipsize(TextUtils.TruncateAt.END);
    hint.setOnClickListener(v -> open.run());
    p.addView(hint);
    TextView more = text("查看建议 ›", 13, GREEN);
    more.setPadding(0, dp(8), 0, dp(4));
    more.setOnClickListener(v -> open.run());
    p.addView(more);
  }

  void showAdvice(String title, List<PlanCoach.Tip> tips) {
    StringBuilder s = new StringBuilder();
    for (PlanCoach.Tip t : tips)
      s.append(t.caution ? "注意 · " : "建议 · ").append(t.text).append("\n\n");
    s.append(
        "这些是计划参考，不是疾病判断。训练组数与余力用于提示训练负担，不代表测得的生理强度。碳水默认按参考体重×3g试行；当天计划达到12组时按×4g试行，可自行修改。这是应用的可调策略，不是所有人都必须达到的标准。\n\n"
            + "蛋白质参考ISSN运动人群1.4～2.0g/kg。肾病、妊娠或需要治疗性饮食等情况，需个体化建议。\n\n"
            + "依据与范围：ISSN 2017 Protein and Exercise；ACSM 2026 Resistance Training。完整阈值和来源见仓库"
            + " docs/planning-rules.md。");
    info(title, s.toString());
  }

  void profileDialog() {
    LinearLayout p = column();
    p.setPadding(dp(20), dp(8), dp(20), dp(8));
    JSONObject pro = Data.obj(data.state, "profile");
    paragraph(p, "体重只用于本机计划参考，不会上传。留空则使用最近体重记录。碳水和热量目标留空代表自动参考/不判断热量。");
    p.addView(text("参考体重 kg（可留空）", 13, MUTED));
    EditText kg =
        number(
            "体重", pro.has("weight") && pro.optDouble("weight") > 0 ? pro.optString("weight") : "");
    p.addView(kg);
    p.addView(text("每次可用分钟", 13, MUTED));
    EditText mins = number("分钟", String.valueOf(pro.optInt("minutes", 30)));
    p.addView(mins);
    Switch beginner = new Switch(this);
    beginner.setText("我是力量训练新手");
    beginner.setChecked(pro.optBoolean("beginner", true));
    p.addView(beginner);
    p.addView(text("自定碳水目标 g/天（可留空）", 13, MUTED));
    EditText carbs =
        number("自动参考", pro.optDouble("carbTarget", 0) > 0 ? pro.optString("carbTarget") : "");
    p.addView(carbs);
    p.addView(text("自行设定热量目标 千卡/天（可留空）", 13, MUTED));
    EditText energy =
        number("不判断总热量", pro.optDouble("energy", 0) > 0 ? pro.optString("energy") : "");
    p.addView(energy);
    ScrollView scroll = new ScrollView(this);
    scroll.addView(p);
    AlertDialog d =
        new AlertDialog.Builder(this)
            .setTitle("计划参考设置")
            .setView(scroll)
            .setPositiveButton("保存", null)
            .setNegativeButton("取消", null)
            .create();
    d.setOnShowListener(
        v ->
            d.getButton(-1)
                .setOnClickListener(
                    b -> {
                      try {
                        JSONObject next = new JSONObject();
                        Double weight = parseOptional(kg, 20, 350),
                            c = parseOptional(carbs, 1, 1000),
                            k = parseOptional(energy, 500, 10000);
                        double m = PlannerEditor.validNumber(mins, 10, 180);
                        if (m != Math.rint(m)) throw new Exception("分钟填写整数");
                        Data.put(next, "weight", weight == null ? 0 : weight);
                        Data.put(next, "carbTarget", c == null ? 0 : c);
                        Data.put(next, "energy", k == null ? 0 : k);
                        Data.put(next, "minutes", (int) m);
                        Data.put(next, "beginner", beginner.isChecked());
                        Data.put(data.state, "profile", next);
                        data.save();
                        d.dismiss();
                        render();
                      } catch (Exception ex) {
                        toast(ex.getMessage());
                      }
                    }));
    d.show();
  }

  void records() {
    label(content, "记录", "体重、腰围与训练历史");
    JSONObject body = Data.obj(data.state, "body");
    ArrayList<String> dates = keys(body);
    ArrayList<Float> weights = new ArrayList<>();
    ArrayList<String> weightDates = new ArrayList<>();
    for (String date : dates) {
      JSONObject b = body.optJSONObject(date);
      double v = b.optDouble("kg", Double.NaN);
      if (Double.isFinite(v)) {
        weights.add((float) v);
        weightDates.add(date);
      }
    }
    LinearLayout chart = card(content);
    chart.addView(bold("体重趋势", 18, INK));
    gap(chart, 4);
    paragraph(
        chart,
        weights.isEmpty()
            ? "记录第一次体重，开始观察趋势。"
            : "最近记录 " + weights.get(weights.size() - 1) + " kg · 不用追逐单日波动");
    chart.addView(
        new WeightView(this, weights, weightDates), new LinearLayout.LayoutParams(-1, dp(180)));
    action(chart, "＋ 记录体重 / 腰围", true, () -> bodyDialog());
    int sessions = 0;
    JSONObject all = Data.obj(data.state, "workouts");
    Iterator<String> it = all.keys();
    while (it.hasNext()) if (data.trained(it.next())) sessions++;
    LinearLayout stats = card(content);
    stats.addView(bold("累计训练 " + sessions + " 天", 20, GREEN));
    gap(stats, 8);
    paragraph(stats, "体重每周选3个早晨测量，看周平均。腰围每两周测一次。力量提高、腰围稳定，即使体重不变也可能有进步。");
    if (!weights.isEmpty()) {
      double sum = 0;
      int n = 0;
      LocalDate cutoff = LocalDate.now().minusDays(6);
      for (int i = 0; i < weights.size(); i++)
        if (!LocalDate.parse(weightDates.get(i)).isBefore(cutoff)
            && !LocalDate.parse(weightDates.get(i)).isAfter(LocalDate.now())) {
          sum += weights.get(i);
          n++;
        }
      if (n > 0)
        paragraph(stats, String.format(Locale.CHINA, "近7天已记录 %d 次 · 均值 %.1f kg", n, sum / n));
    }
    LinearLayout guidance = card(content);
    action(guidance, "步数与恢复参考", false, () -> info("恢复与日常活动", data.plan.optString("recovery")));
    action(guidance, "导出全部记录与计划", false, () -> exportBackup());
    label(content, "身体记录", "点击记录可编辑或删除");
    Collections.reverse(dates);
    for (int i = 0; i < Math.min(30, dates.size()); i++) {
      String date = dates.get(i);
      JSONObject b = body.optJSONObject(date);
      LinearLayout c = card(content);
      c.addView(bold(date, 15, INK));
      paragraph(
          c,
          (b.has("kg") ? b.optString("kg") + " kg" : "体重未填")
              + "  /  "
              + (b.has("waist") ? b.optString("waist") + " cm" : "腰围未填"));
      c.setOnClickListener(v -> bodyDialog(date));
    }
    label(content, "训练历史", "最近30个训练日 · 点击查看每组记录");
    ArrayList<String> trainingDates = keys(all);
    Collections.reverse(trainingDates);
    int shown = 0;
    for (String date : trainingDates) {
      if (!data.trained(date)) continue;
      if (shown++ >= 30) break;
      LinearLayout c = card(content);
      c.addView(bold(date, 16, INK));
      paragraph(c, "已保存 · 点击查看重量和次数");
      c.setOnClickListener(v -> history(date));
    }
  }

  static ArrayList<String> keys(JSONObject o) {
    ArrayList<String> r = new ArrayList<>();
    Iterator<String> it = o.keys();
    while (it.hasNext()) r.add(it.next());
    Collections.sort(r);
    return r;
  }

  void history(String date) {
    JSONObject rs = Data.obj(data.state, "workouts").optJSONObject(date);
    StringBuilder s = new StringBuilder();
    Iterator<String> it = rs.keys();
    while (it.hasNext()) {
      String rid = it.next();
      JSONObject w = rs.optJSONObject(rid);
      if (w == null || !w.optBoolean("finished")) continue;
      s.append("训练 ").append(rid).append("\n");
      Iterator<String> es = w.keys();
      while (es.hasNext()) {
        String eid = es.next();
        if (eid.equals("finished")) continue;
        JSONArray sets = w.optJSONArray(eid);
        if (sets == null) continue;
        JSONObject ex = data.exercise(eid);
        s.append(ex == null ? eid : ex.optString("name")).append("\n");
        for (int i = 0; i < sets.length(); i++) {
          JSONObject set = sets.optJSONObject(i);
          s.append("  第")
              .append(i + 1)
              .append("组  ")
              .append(set.optString("kg", "—"))
              .append("kg × ")
              .append(set.optString("reps", "—"))
              .append(set.optBoolean("done") ? "  ✓" : "  未完成")
              .append("\n");
        }
      }
      s.append("\n");
    }
    info(date + " 训练记录", s.toString());
  }

  void bodyDialog() {
    bodyDialog(data.today());
  }

  void bodyDialog(String date) {
    JSONObject b = Data.obj(data.state, "body").optJSONObject(date);
    LinearLayout box = column();
    box.setPadding(dp(24), dp(6), dp(24), 0);
    paragraph(box, "日期：" + date + "\n同一天保存会更新该日记录；空白项保留原值。");
    EditText kg = number("体重（kg）", b == null ? "" : b.optString("kg", "")),
        waist = number("腰围（cm，可不填）", b == null ? "" : b.optString("waist", ""));
    box.addView(kg);
    box.addView(waist);
    AlertDialog dialog =
        new AlertDialog.Builder(this)
            .setTitle("记录身体变化")
            .setView(box)
            .setPositiveButton("保存", null)
            .setNegativeButton("取消", null)
            .setNeutralButton(
                b == null ? null : "删除这条",
                (d, w) ->
                    new AlertDialog.Builder(this)
                        .setTitle("删除 " + date + " 的记录？")
                        .setPositiveButton(
                            "删除",
                            (dd, ww) -> {
                              Data.obj(data.state, "body").remove(date);
                              data.save();
                              render();
                            })
                        .setNegativeButton("取消", null)
                        .show())
            .create();
    dialog.setOnShowListener(
        v ->
            dialog
                .getButton(-1)
                .setOnClickListener(
                    btn -> {
                      try {
                        Double k = parseOptional(kg, 20, 350), a = parseOptional(waist, 30, 250);
                        if (k == null && a == null) throw new Exception("至少填写一项");
                        data.body(date, k, a);
                        dialog.dismiss();
                        render();
                      } catch (Exception e) {
                        toast(e.getMessage());
                      }
                    }));
    dialog.show();
  }

  Double parseOptional(EditText e, double min, double max) throws Exception {
    String s = e.getText().toString().trim();
    if (s.isEmpty()) return null;
    double d;
    try {
      d = Double.parseDouble(s);
    } catch (Exception ex) {
      throw new Exception("请填写有效数字");
    }
    if (!Double.isFinite(d) || d < min || d > max) throw new Exception("数值超出范围：" + min + "～" + max);
    return d;
  }

  void settings() {
    LinearLayout p = column();
    p.setPadding(dp(20), dp(12), dp(20), dp(12));
    ScrollView sc = new ScrollView(this);
    sc.addView(p);
    p.addView(bold("计划与提醒", 20, INK));
    gap(p, 8);
    paragraph(p, "数据只保存在手机本地，无账号、无广告、不申请联网权限。请定期导出备份；卸载应用会清除本地数据。");
    action(
        p,
        "计划开始日期：" + data.start(),
        false,
        () -> {
          LocalDate d = data.start();
          new DatePickerDialog(
                  this,
                  (view, y, m, day) -> {
                    LocalDate chosen = LocalDate.of(y, m + 1, day);
                    new AlertDialog.Builder(this)
                        .setTitle("更新计划开始日期？")
                        .setMessage("将重新计算训练周数和A/B顺序，已有记录保留。")
                        .setPositiveButton(
                            "更新",
                            (dd, w) -> {
                              Data.put(data.state, "start", chosen.toString());
                              data.save();
                              ReminderReceiver.schedule(this);
                              render();
                              toast("计划日期已更新，重新打开设置可查看");
                            })
                        .setNegativeButton("取消", null)
                        .show();
                  },
                  d.getYear(),
                  d.getMonthValue() - 1,
                  d.getDayOfMonth())
              .show();
        });
    Switch desk = new Switch(this);
    desk.setText("工作日起身提醒");
    desk.setChecked(data.state.optBoolean("deskReminder"));
    p.addView(desk);
    gap(p, 8);
    LinearLayout hours = row();
    EditText start = number("开始时", String.valueOf(data.state.optInt("reminderStart", 9))),
        end = number("结束时", String.valueOf(data.state.optInt("reminderEnd", 18)));
    hours.addView(start, new LinearLayout.LayoutParams(0, -2, 1));
    hours.addView(end, new LinearLayout.LayoutParams(0, -2, 1));
    p.addView(hours);
    EditText interval = number("间隔分钟", String.valueOf(data.state.optInt("interval", 45)));
    p.addView(interval);
    paragraph(p, "周一至周五，整点小时范围内提醒，12:00～13:00暂停。系统省电可能延迟提醒，不保证精确到分钟。");
    Switch training = new Switch(this);
    training.setText("训练日提醒");
    training.setChecked(data.state.optBoolean("workoutReminder"));
    p.addView(training);
    EditText hour = number("训练提醒小时", String.valueOf(data.state.optInt("workoutHour", 18)));
    p.addView(hour);
    paragraph(p, "按计划训练日，在上述小时的30分提醒。例如18代表18:30。当天已完成训练则跳过。");
    action(
        p,
        "保存提醒设置",
        true,
        () -> {
          try {
            int a = Integer.parseInt(start.getText().toString()),
                b = Integer.parseInt(end.getText().toString()),
                i = Integer.parseInt(interval.getText().toString()),
                h = Integer.parseInt(hour.getText().toString());
            if (a < 0 || b > 23 || a >= b || i < 15 || i > 180 || h < 0 || h > 23)
              throw new Exception();
            Data.put(data.state, "reminderStart", a);
            Data.put(data.state, "reminderEnd", b);
            Data.put(data.state, "interval", i);
            Data.put(data.state, "workoutHour", h);
            Data.put(data.state, "deskReminder", desk.isChecked());
            Data.put(data.state, "workoutReminder", training.isChecked());
            data.save();
            if ((desk.isChecked() || training.isChecked()) && !ReminderReceiver.allowed(this)) {
              if (Build.VERSION.SDK_INT >= 33
                  && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                      != PackageManager.PERMISSION_GRANTED)
                requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, 80);
              else notificationSettings();
            }
            ReminderReceiver.schedule(this);
            toast("设置已保存；请允许通知以接收提醒");
          } catch (Exception e) {
            toast("开始/结束小时0～23且开始小于结束；间隔15～180分钟");
          }
        });
    action(p, "通知权限与系统设置", false, () -> notificationSettings());
    paragraph(p, ReminderReceiver.allowed(this) ? "当前通知权限：已允许" : "当前通知权限：未允许，提醒不会显示");
    action(p, "导出计划 + 全部记录", false, () -> exportBackup());
    action(
        p,
        "导入计划 / 恢复备份",
        false,
        () -> {
          Intent pick =
              new Intent(Intent.ACTION_OPEN_DOCUMENT)
                  .setType("*/*")
                  .addCategory(Intent.CATEGORY_OPENABLE);
          startActivityForResult(pick, 42);
        });
    paragraph(p, "导入计划JSON只更新计划；恢复备份会替换全部当前记录。格式见仓库README。备份包含个人记录，请自行保管。");
    action(
        p,
        "关于与使用说明",
        false,
        () ->
            info(
                "日日有练 1.1.0",
                "离线个人训练助手\n\n"
                    + "包含训练指导、逐组记录、组间计时、饮食参照、每日打卡、体重腰围趋势和可选提醒。\n\n"
                    + "本版本动作指导为文字与份量示意，不包含真人动作视频。首次使用器械请现场确认动作。\n\n"
                    + "提醒使用系统非精确闹钟，可能受省电策略影响；强行停止应用后需重新打开。\n\n"
                    + "这是训练记录工具，饮食数字为试行参考，不能替代医疗建议。"));
    new AlertDialog.Builder(this).setView(sc).setPositiveButton("关闭", (d, w) -> render()).show();
  }

  void notificationSettings() {
    startActivity(
        new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
  }

  @Override
  public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
    super.onRequestPermissionsResult(code, permissions, results);
    if (code == 80) {
      ReminderReceiver.schedule(this);
      toast(
          results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED
              ? "通知已允许"
              : "通知未允许，可在系统设置中开启");
    }
  }

  void exportBackup() {
    Intent intent =
        new Intent(Intent.ACTION_CREATE_DOCUMENT)
            .setType("application/json")
            .addCategory(Intent.CATEGORY_OPENABLE)
            .putExtra(Intent.EXTRA_TITLE, "health-backup-" + data.today() + ".json");
    startActivityForResult(intent, 41);
  }

  @Override
  public void onActivityResult(int request, int result, Intent intent) {
    super.onActivityResult(request, result, intent);
    if (result != RESULT_OK || intent == null || intent.getData() == null) return;
    Uri uri = intent.getData();
    try {
      if (request == 41) {
        try (OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
          if (out == null) throw new IOException("无法写入文件");
          out.write(data.export().getBytes(StandardCharsets.UTF_8));
        }
        toast("备份已导出");
      } else if (request == 42) {
        byte[] bytes;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
          if (in == null) throw new IOException("无法读取文件");
          bytes = Data.readLimited(in, 2_000_000);
        }
        pendingImport = new String(bytes, StandardCharsets.UTF_8);
        boolean backup = new JSONObject(pendingImport).has("format");
        new AlertDialog.Builder(this)
            .setTitle(backup ? "恢复备份并替换当前记录？" : "更新训练饮食计划？")
            .setMessage(backup ? "现有计划和记录会被备份内容替换。建议先导出当前备份。" : "个人记录保留，动作与训练日按新计划更新。")
            .setPositiveButton(
                "确认导入",
                (d, w) -> {
                  try {
                    data.importText(pendingImport);
                    pendingImport = null;
                    ReminderReceiver.schedule(this);
                    render();
                    toast("导入成功");
                  } catch (Exception e) {
                    toast("导入失败：" + e.getMessage());
                  }
                })
            .setNegativeButton("取消", (d, w) -> pendingImport = null)
            .show();
      }
    } catch (Exception e) {
      toast("操作失败：" + e.getMessage());
    }
  }

  void info(String title, String message) {
    TextView t = text(message, 15, INK);
    t.setTextIsSelectable(true);
    t.setPadding(dp(24), dp(12), dp(24), dp(20));
    ScrollView s = new ScrollView(this);
    s.addView(t);
    new AlertDialog.Builder(this).setTitle(title).setView(s).setPositiveButton("知道了", null).show();
  }

  void toast(String s) {
    Toast.makeText(this, s, Toast.LENGTH_LONG).show();
  }

  final class WeightView extends View {
    final ArrayList<Float> values;
    final ArrayList<String> dates;
    Paint paint = new Paint(3);
    final Path plotPath = new Path();

    WeightView(Context c, ArrayList<Float> v, ArrayList<String> d) {
      super(c);
      values = v;
      dates = d;
      setContentDescription("体重趋势图，共" + v.size() + "条记录");
    }

    void line(Canvas c, String s, float x, float y, int color, int size) {
      paint.setColor(color);
      paint.setTextSize(dp(size));
      c.drawText(s, x, y, paint);
    }

    @Override
    protected void onDraw(Canvas c) {
      super.onDraw(c);
      float w = getWidth(),
          h = getHeight(),
          left = dp(40),
          right = w - dp(12),
          top = dp(20),
          bottom = h - dp(36);
      if (values.isEmpty()) {
        line(c, "还没有体重记录", dp(12), h / 2, MUTED, 14);
        return;
      }
      int from = Math.max(0, values.size() - 30);
      float min = Collections.min(values.subList(from, values.size())) - 0.5f,
          max = Collections.max(values.subList(from, values.size())) + 0.5f;
      paint.setStrokeWidth(dp(1));
      for (int i = 0; i < 3; i++) {
        float y = top + (bottom - top) * i / 2;
        paint.setColor(LINE);
        c.drawLine(left, y, right, y, paint);
        line(
            c,
            String.format(Locale.CHINA, "%.1f", max - (max - min) * i / 2),
            0,
            y + dp(4),
            MUTED,
            10);
      }
      Path path = plotPath;
      path.reset();
      int n = values.size() - from;
      long first = LocalDate.parse(dates.get(from)).toEpochDay(),
          last = LocalDate.parse(dates.get(dates.size() - 1)).toEpochDay();
      for (int i = from; i < values.size(); i++) {
        float
            x =
                n == 1
                    ? (left + right) / 2
                    : left
                        + (right - left)
                            * (LocalDate.parse(dates.get(i)).toEpochDay() - first)
                            / Math.max(1, last - first),
            y = bottom - (values.get(i) - min) / (max - min) * (bottom - top);
        if (i == from) path.moveTo(x, y);
        else path.lineTo(x, y);
        paint.setColor(GREEN);
        c.drawCircle(x, y, dp(3), paint);
      }
      paint.setStyle(Paint.Style.STROKE);
      paint.setColor(GREEN);
      paint.setStrokeWidth(dp(2));
      c.drawPath(path, paint);
      paint.setStyle(Paint.Style.FILL);
      line(c, dates.get(from).substring(5), left, h - dp(10), MUTED, 11);
      if (n > 1)
        line(c, dates.get(dates.size() - 1).substring(5), right - dp(36), h - dp(10), MUTED, 11);
    }
  }

  final class PortionView extends View {
    Paint p = new Paint(3);

    PortionView(Context c) {
      super(c);
      setContentDescription("份量示意：肉鱼约一到一掌半，蔬菜约两拳");
    }

    @Override
    protected void onDraw(Canvas c) {
      super.onDraw(c);
      float unit = getWidth() / 3f;
      String[] names = {"肉 / 鱼", "米饭", "蔬菜"}, amounts = {"1～1.5掌", "普通碗1碗", "约2拳"};
      for (int i = 0; i < 3; i++) {
        float x = unit * i + unit / 2, y = dp(52);
        p.setColor(PALE);
        c.drawCircle(x, y, dp(36), p);
        p.setColor(GREEN);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(dp(2));
        if (i == 0) {
          c.drawRoundRect(x - dp(16), y - dp(12), x + dp(15), y + dp(20), dp(8), dp(8), p);
          for (int f = 0; f < 4; f++)
            c.drawLine(
                x - dp(11) + f * dp(7),
                y - dp(12),
                x - dp(11) + f * dp(7),
                y - dp(24) + Math.abs(f - 1) * dp(3),
                p);
        } else if (i == 1) {
          c.drawArc(x - dp(22), y - dp(17), x + dp(22), y + dp(19), 0, 180, false, p);
          c.drawLine(x - dp(22), y + dp(1), x + dp(22), y + dp(1), p);
          c.drawOval(x - dp(21), y - dp(5), x + dp(21), y + dp(6), p);
        } else {
          c.drawRoundRect(x - dp(25), y - dp(13), x - dp(2), y + dp(14), dp(7), dp(7), p);
          c.drawRoundRect(x + dp(2), y - dp(13), x + dp(25), y + dp(14), dp(7), dp(7), p);
        }
        p.setStyle(Paint.Style.FILL);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(dp(13));
        p.setColor(INK);
        c.drawText(names[i], x, dp(108), p);
        p.setTextSize(dp(11));
        p.setColor(MUTED);
        c.drawText(amounts[i], x, dp(130), p);
      }
      p.setTextAlign(Paint.Align.LEFT);
    }
  }
}
