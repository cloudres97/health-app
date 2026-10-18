# v1.0.0 验证记录

- `clean testDebugUnitTest lintDebug assembleDebug`：通过。
- JUnit：9项测试，0失败、0错误。
- Android Lint：No issues found。
- 内置计划一致性脚本：通过。
- `apksigner verify`：通过，APK Signature Scheme v2。
- APK：`com.cloudres.health`，versionName 1.0.0，minSdk 26，targetSdk 35。
- 应用仅申请通知与开机广播权限，无联网权限。

本次APK为debug签名测试版。硬件手机安装、厂商后台管理、锁屏提醒和文件选择器仍需在实际手机验证。没有将未完成的模拟器测试计入以上通过项。

## v1.1.0 自选计划与简洁界面

- `clean testDebugUnitTest lintDebug assembleDebug`：通过。
- JUnit：29项，0失败/0错误（计划基础9项、营养与训练规则13项、Robolectric页面与保存迁移7项）。
- 页面测试覆盖四个页面与两个编辑器、取消不写入、保存及重载、份量输入、旧计划迁移、无效导入和首周自定组数。
- Android Lint：No issues found；APK签名校验通过。
- versionCode 2 / versionName 1.1.0；最低Android 8.0；无新增运行时联网依赖。
- Robolectric页面测试使用API 28，不等同于真机测试。尚未在实际三星Android 16手机验证；通知与省电策略仍需真机确认。
- 营养与训练提醒是可解释的规划参考，不是健康诊断。规则边界和来源见planning-rules.md。
