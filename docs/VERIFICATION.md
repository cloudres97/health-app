# v1.0.0 验证记录

- `clean testDebugUnitTest lintDebug assembleDebug`：通过。
- JUnit：9项测试，0失败、0错误。
- Android Lint：No issues found。
- 内置计划一致性脚本：通过。
- `apksigner verify`：通过，APK Signature Scheme v2。
- APK：`com.cloudres.health`，versionName 1.0.0，minSdk 26，targetSdk 35。
- 应用仅申请通知与开机广播权限，无联网权限。

本次APK为debug签名测试版。硬件手机安装、厂商后台管理、锁屏提醒和文件选择器仍需在实际手机验证。没有将未完成的模拟器测试计入以上通过项。
