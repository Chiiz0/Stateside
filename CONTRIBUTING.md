# 贡献指南（Contributing Guide）

感谢您关注并参与 **美境（Stateside）** 的开源共建！无论是反馈某款国产新机型的系统开关路径、补充海外/AI 应用兼容细节，还是提交代码优化，都非常欢迎。

---

## 1. 开发环境准备

- **JDK**：JDK 17 或更高版本（推荐 JDK 21）
- **Android SDK**：
  - `platforms;android-36`
  - `build-tools;36.0.0` 或 `36.1.0`
- **IDE**：Android Studio Meerkat / Narwhal 或更新版本（亦可使用纯命令行 `./gradlew` 构建）

本地克隆与快速验证：

```bash
git clone https://github.com/Chiiz0/Stateside.git
cd Stateside

# 运行单元测试
./gradlew :app:testDebugUnitTest

# 构建 Debug 与 R8 精简签名版 Release APK
./gradlew :app:assembleDebug :app:assembleRelease

# 验证 16 KB 页面对齐（Android 15/16/17 真机硬性要求）
$ANDROID_HOME/build-tools/36.1.0/zipalign -c -P 16 4 app/build/outputs/apk/release/app-release.apk
```

---

## 2. 模块职责速览

修改代码时请保持清晰的职责边界与零外网依赖原则：

- `app.stateside.env.adb`：本机 `127.0.0.1` 无线调试配对（SPAKE2）、TLS 1.3 连接、回环端口占用寻端（`AdbMdnsDiscovery`）与 Root/TCP 通道调度。
- `app.stateside.env.privileged`：运行在 `StatesideEnvInstrumentation` 内的借权逻辑、`SimRegionOverrideEngine` 双卡覆盖与两步还原、`AppEnglishLocaleEngine` 多用户空间增量语言设定。
- `app.stateside.env.oem`：各品牌手机（HyperOS / ColorOS / OriginOS / MagicOS / One UI 等）开关向导文案与系统设置深链跳转。
- `app.stateside.env.boot` / `shortcut`：开机 Wi-Fi 延迟自愈调度（`BootRestoreJobService`）与桌面带角标防漏改快捷方式。
- `app.stateside.env.ui`：Jetpack Compose Material 3 界面与 `MainViewModel` 单向数据流。

---

## 3. 提交规范与自检清单

在发起 Pull Request 前，请确认以下事项：

1. **零外网原则**：严禁引入任何联网统计、广告、崩溃上报 SDK 或非本机回环网络调用。
2. **R8 混淆与 16 KB 对齐**：若新增了包含 JNI `.so` 或反射调用的依赖，请同步在 `app/proguard-rules.pro` 补充 `-keep` 规则，并确保 `assembleRelease` 与 `zipalign -c -P 16 4` 通过。
3. **无障碍友好**：任何涉及 `UiAutomation` 的改动必须保留 `UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES`，避免中断用户手机上的现有无障碍服务。
4. **单元测试与零编译警告**：运行 `./gradlew testDebugUnitTest :app:assembleRelease` 确保测试全部通过且无新增 Kotlin 编译警告。

Commit Message 建议采用语义化前缀：
- `feat:` 新增特性或新增某品牌机型适配
- `fix:` 修复真机兼容性或逻辑缺陷
- `docs:` 完善文档或排查指南
- `refactor:` 重构优化内部实现
