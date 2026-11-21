# 更新日志（Changelog）

本项目遵循 [语义化版本（Semantic Versioning）](https://semver.org/lang/zh-CN/) 与 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/) 规范。

## [1.1.0] - 2026-11-20

### 新增（Added）
- **Android 14 ~ 17 全真机覆盖**：下调 `minSdk` 至 API 34（Android 14），全面兼容 Android 14 / 15 / 16 / 17 真机并通过 16 KB 内存页对齐（`zipalign -c -P 16 4`）核验。
- **Wi-Fi 延迟开机自愈服务（`BootRestoreJobService`）**：基于系统 `JobScheduler` 绑定 `TRANSPORT_WIFI` 网络约束，解决真机刚开机时尚未连接 Wi-Fi 导致 `BroadcastReceiver` 10 秒超时的问题。
- **校园网 / 全局 VPN 手动端口配对与重连**：通知栏 `RemoteInput` 与首次激活向导同步支持输入 `端口 6位配对码`（如 `38941 123456`）及手动连接端口，攻克组播被拦截场景。
- **Root 与 `adb tcpip 5555` 免配对快车道**：自动识别 Magisk / KernelSU / APatch Root 权限及本机 5555 端口，免除无线调试配对步骤。
- **真实图标 + `US` 盾牌角标桌面快捷方式**：动态合成目标应用图标与防漏改角标；扩展英语应用同步接入主界面安全快启坞与快捷方式创建。
- **开发者选项自动滚动高亮**：跳转系统开发者选项时注入 `toggle_adb_wireless` 锚点参数，自动滚动高亮至「无线调试」。

### 修复（Fixed）
- **修复 `targetSdk 36` 下 mDNS 漏检本机端口**：改用 `ServerSocket` 回环 `EADDRINUSE` 端口占用探测 + `ConnectivityManager` 原始字节比对，彻底规避 `NETLINK_ROUTE` SELinux 限制与 IPv6 `%wlan0` 后缀不匹配。
- **修复通知栏提交错误配对码触发的 5 秒前台服务崩溃**：所有 `startForegroundService` 分支均第一时间调用 `startForeground`。
- **修复无障碍服务被中断及 `UiAutomation` 半连接态崩溃**：启用 `FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES` 并安全清理异常连接态。
- **修复国产系统应用双开（`user 999`）每次误强停问题**：逐个用户空间校验安装状态与语言差异后再决定是否重载进程。
- **修复未授予电话权限时误判「美境生效中」**：引入系统 `BOOT_COUNT` 与 `isStatesideDesired` 状态机严格核验当前开机周期。
- **修复空白 `EF_SPN` 电话卡恢复默认后运营商名称残留问题**：内置中国四大运营商及港澳台 MCC/MNC 字典并在两步还原中增加异步落盘轮询。
- **修复构建与 R8 混淆兼容性**：解决 `hiddenapibypass` API 37 元数据冲突、BouncyCastle 重复类冲突及 `spake2` JNI 混淆规则缺失。

## [1.0.0] - 2026-11-20

### 新增（Added）
- 初始架构设计：整合本机无线 ADB 客户端、同进程 `StatesideEnvInstrumentation` 特权借权引擎、美国三大运营商预设（T-Mobile / AT&T / Verizon）与四大默认英语应用组（TikTok / X / Claude / ChatGPT）。
