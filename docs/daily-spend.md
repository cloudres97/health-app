# 日账 · Daily Spend

独立安卓模块：`spend/`，不替换现有“日日有练”应用。

## 功能
- 今日支出、本月累计、预算进度、微信/支付宝渠道汇总。
- 手动记录支出、按来源筛选账单、长按删除记录。
- 从系统文件选择器导入 CSV，数据保存在本机 SharedPreferences。
- 简洁原生 Android UI，无第三方运行时依赖。

## 账单接入说明
微信支付和支付宝的个人账单不能由普通第三方 App 直接凭公开接口读取。请先在官方应用中导出账单，再选择 CSV 文件导入。首版是通用 CSV 导入原型，官方导出文件的字段/编码/收支标记可能不同，需要拿真实脱敏样例验证后再定制解析器。导入前务必核对金额方向，避免把退款或收入计为支出。不要上传密码、支付凭据或未经脱敏的真实账单。

## 构建
`./gradlew :spend:assembleDebug`

APK 输出：`spend/build/outputs/apk/debug/spend-debug.apk`。GitHub Actions 成功后提供 `daily-spend-debug-apk` Artifact，并在主分支发布 `downloads/daily-spend.apk`。
