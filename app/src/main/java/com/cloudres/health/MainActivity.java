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
  static final int BG = 0xFFF5F6F1,
      WHITE = 0xFFFFFFFF,
      INK = 0xFF20372E,
      MUTED = 0xFF6E7E75,
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
    l.setPadding(dp(18), dp(18), dp(18), dp(18));
    l.setBackground(shape(WHITE, 20));
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.bottomMargin = dp(12);
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
    TextView brand = bold("日日有练", 22, INK);
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
    int week = data.week(now);
    JSONObject routine = data.routineFor(now);
    label(
        content,
        dateLabel(now),
        week <= 0
            ? "计划尚未开始"
            : week <= 8 ? "第 " + week + " / 8 周 · 稳定练，慢慢变强" : "已完成8周周期 · 继续记录，评估下一阶段计划");
    LinearLayout hero = card(content);
    hero.setBackground(shape(GREEN, 22));
    hero.addView(text("TODAY’S FOCUS", 12, 0xFFCEE5D8));
    gap(hero, 10);
    hero.addView(
        bold(routine == null ? "今天，恢复也算进步" : routine.optString("name") + " · 30分钟", 26, WHITE));
    gap(hero, 8);
    hero.addView(
        text(routine == null ? "吃好、走一走，让身体为下一次训练做好准备。" : "热身5分钟 → 4个动作 → 记录与放松", 14, 0xFFE2F0E8));
    gap(hero, 16);
    Button go =
        button(
            routine == null ? "查看训练计划" : "开始今天的训练",
            false,
            () -> {
              if (routine != null) selected = routine.optString("id");
              tab = 1;
              render();
            });
    hero.addView(go, new LinearLayout.LayoutParams(-1, -2));
    LinearLayout overview = card(content);
    progressLabel = bold("", 18, INK);
    overview.addView(progressLabel);
    gap(overview, 4);
    overview.addView(text("完成一点，也是在向前", 13, MUTED));
    updateProgress();
    LinearLayout weekRow = row();
    for (int i = 0; i < 7; i++) {
      LocalDate day = Schedule.monday(now).plusDays(i);
      LinearLayout cell = column();
      cell.setGravity(Gravity.CENTER);
      cell.addView(text(new String[] {"一", "二", "三", "四", "五", "六", "日"}[i], 12, MUTED));
      TextView number =
          bold(
              data.trained(day.toString()) ? "✓" : String.valueOf(day.getDayOfMonth()),
              16,
              day.equals(now) ? WHITE : INK);
      number.setGravity(Gravity.CENTER);
      number.setBackground(
          shape(day.equals(now) ? GREEN : data.trained(day.toString()) ? PALE : BG, 12));
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(32), dp(36));
      lp.topMargin = dp(6);
      cell.addView(number, lp);
      weekRow.addView(cell, new LinearLayout.LayoutParams(0, -2, 1));
    }
    gap(overview, 14);
    overview.addView(weekRow);
    label(content, "今日清单", "饮食按餐打卡，不必逐项称量");
    if (routine != null) {
      LinearLayout c = card(content);
      c.addView(bold(data.trained(data.today()) ? "✓ 今天的训练已完成" : "○ 完成今天的力量训练", 16, INK));
      gap(c, 4);
      paragraph(c, "训练页完成各组后，点击“结束并保存训练”。");
    }
    for (String category : new String[] {"meals", "dailyTasks"}) {
      JSONArray a = data.plan.optJSONArray(category);
      for (int i = 0; i < a.length(); i++) {
        JSONObject t = a.optJSONObject(i);
        checkCard(
            content,
            category + "_" + t.optString("id"),
            t.optString("title"),
            t.optString("description"));
      }
    }
    if (now.getDayOfWeek().getValue() == 1
        || now.getDayOfWeek().getValue() == 3
        || now.getDayOfWeek().getValue() == 6) {
      LinearLayout c = card(content);
      paragraph(c, "晨起如厕后、早餐前称重，看周平均；每两周在肚脐水平测腰围。");
      action(c, "记录体重 / 腰围", false, () -> bodyDialog());
    }
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
    StringBuilder days = new StringBuilder();
    for (int day : data.days()) {
      if (days.length() > 0) days.append(" / ");
      days.append("周").append(new String[] {"一", "二", "三", "四", "五", "六", "日"}[day - 1]);
    }
    label(content, "把每一次练好", days + " · 模板依次轮换");
    LinearLayout selectors = row();
    JSONArray routines = data.plan.optJSONArray("routines");
    if (data.routine(selected) == null) selected = routines.optJSONObject(0).optString("id");
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
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
      lp.setMargins(dp(3), 0, dp(3), dp(12));
      selectors.addView(b, lp);
    }
    content.addView(selectors);
    LinearLayout guide = card(content);
    guide.addView(bold("先热身，再开始", 17, INK));
    gap(guide, 6);
    paragraph(guide, "快走或单车2～3分钟，活动肩和髋；第一个动作先做一组轻重量。第1周每动作1组，第2周起2组。动作要点可点开查看。");
    action(guide, "完整训练与加重规则", false, () -> info("训练规则", data.plan.optString("guidance")));
    LinearLayout timer = card(content);
    timerLabel = bold("组间休息 · 还没有计时", 18, INK);
    timer.addView(timerLabel);
    gap(timer, 10);
    LinearLayout options = row();
    for (int secs : new int[] {60, 90}) {
      Button b = button(secs + "秒", false, () -> beginRest(secs));
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
      lp.setMargins(0, 0, dp(6), 0);
      options.addView(b, lp);
    }
    options.addView(
        button(
            "停止",
            false,
            () -> {
              timerEnd = 0;
              Data.put(data.state, "restEnd", 0);
              data.save();
              if (timerTick != null) handler.removeCallbacks(timerTick);
              timerLabel.setText("组间休息 · 已停止");
            }),
        new LinearLayout.LayoutParams(0, -2, 1));
    timer.addView(options);
    paragraph(timer, "计时状态可恢复；提示音只在应用前台播放。");
    JSONObject routine = data.routine(selected);
    JSONArray ids = routine.optJSONArray("exerciseIds");
    for (int i = 0; i < ids.length(); i++) {
      JSONObject ex = data.exercise(ids.optString(i));
      LinearLayout c = card(content);
      LinearLayout head = row();
      TextView name = bold((i + 1) + "  " + ex.optString("name"), 18, INK);
      head.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
      head.addView(
          button("动作", false, () -> exerciseDialog(ex)),
          new LinearLayout.LayoutParams(dp(62), dp(40)));
      c.addView(head);
      paragraph(c, ex.optString("area") + "  ·  每组 " + ex.optString("reps") + " 次");
      TextView prev = text(data.previous(ex.optString("id")), 12, MUTED);
      c.addView(prev);
      gap(c, 10);
      int sets = data.week(LocalDate.now()) <= 1 ? 1 : ex.optInt("sets", 2);
      String rid = selected, eid = ex.optString("id");
      for (int s = 0; s < sets; s++) setRow(c, rid, eid, s);
    }
    LinearLayout finish = card(content);
    boolean completed = data.workout(data.today(), selected).optBoolean("finished");
    paragraph(finish, completed ? "今天这套训练已经保存。继续修改各组会更新记录。" : "完成各组后再结束。中途退出也会保留已填写内容。");
    action(finish, completed ? "查看今日训练记录" : "结束并保存训练", true, () -> finishWorkout());
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
      int count = data.week(LocalDate.now()) <= 1 ? 1 : e.optInt("sets", 2);
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
    label(content, "食堂也能吃得明白", "按份量搭配，少一些计算负担");
    LinearLayout goal = card(content);
    goal.setBackground(shape(PALE, 20));
    goal.addView(bold("蛋白质 110～130g / 天", 22, GREEN));
    gap(goal, 6);
    paragraph(goal, "约2000～2200千卡仅作试行参考。先稳定三餐，根据2～3周体重、腰围与力量变化调整；不是吃得越多越好。");
    JSONArray meals = data.plan.optJSONArray("meals");
    for (int i = 0; i < meals.length(); i++) {
      JSONObject m = meals.optJSONObject(i);
      LinearLayout c = card(content);
      c.addView(text(m.optString("time"), 12, GREEN));
      gap(c, 4);
      c.addView(bold(m.optString("title"), 18, INK));
      gap(c, 8);
      paragraph(c, m.optString("description"));
      paragraph(c, "小提示：" + m.optString("tip"));
      CheckBox box = new CheckBox(this);
      box.setText("这一餐已完成");
      box.setTextColor(GREEN);
      String id = "meals_" + m.optString("id");
      box.setChecked(data.checked(id));
      box.setOnCheckedChangeListener((v, b) -> data.check(id, b));
      c.addView(box);
    }
    LinearLayout portions = card(content);
    portions.addView(bold("用手掌估份量", 18, INK));
    gap(portions, 8);
    portions.addView(new PortionView(this), new LinearLayout.LayoutParams(-1, dp(150)));
    paragraph(portions, "肉 / 鱼：手掌不含手指，约1～1.5掌面积、接近手掌厚度。蔬菜：约两拳。手大小和食物厚度不同，这只是估算。");
    paragraph(portions, "炒肉丝只估肉；小鸡腿可能不够，骨头和皮不算。肉少时加豆腐。豆腐同体积蛋白质通常少于肉，不直接一比一替换。");
    LinearLayout picks = card(content);
    picks.addView(bold("酸奶与水果怎么选", 18, INK));
    gap(picks, 8);
    paragraph(picks, "酸奶：优先原味、无添加糖、低脂。高蛋白款可按每100g蛋白质≥6g、脂肪0～2g筛选。0蔗糖不代表无其他添加糖，乳酸菌饮料也不等于酸奶。");
    paragraph(picks, "水果：苹果、橙子、猕猴桃、香蕉轮换。每天约两份小到中等水果，按大小调整，约200～350g。直接吃水果，少喝果汁；无需专门买昂贵水果。");
    action(picks, "饮食调整规则", false, () -> info("饮食参考", data.plan.optString("nutrition")));
  }

  void records() {
    label(content, "变化，看长期", "只展示你的真实记录，不预填示例数据");
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
                "日日有练 1.0.0",
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
