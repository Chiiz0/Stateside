## 变更摘要 (Summary)

- 简述本次 PR 解决的问题或新增的能力：

## 关联 Issue

- Closes #

## 真机与构建自检清单 (Checklist)

- [ ] 遵守零外网通信原则（未引入任何外部服务器请求或统计 SDK）
- [ ] 本地已通过单元测试与 Release 构建：`./gradlew :app:testDebugUnitTest :app:assembleRelease`
- [ ] 已通过 Android 15/16/17 16 KB 页面对齐校验：`zipalign -c -P 16 4 app-release.apk`
- [ ] 若涉及 `UiAutomation` 或隐藏 API 反射，已保留 `FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES` 及对应 R8 `-keep` 规则
