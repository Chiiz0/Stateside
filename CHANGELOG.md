# 更新日志（Changelog）

本项目遵循 [语义化版本（Semantic Versioning）](https://semver.org/lang/zh-CN/) 与 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/) 规范。

## [1.1.2] - 2026-11-20

### 修复与增强（Fixed & Improved — 一加 15T / ColorOS 17 真机深度根因修复）
- **彻底修复无线调试通知栏提交配对码后一直卡死在「正在完成中」的问题**：
  1. 移除盲扫未知双栈端口连接逻辑（此前会误连 ColorOS 非 ADB 系统监听端口如 `*:35711`，由于 `libadb` 的 `PairingConnectionCtx` 无读超时且持有 `AbsAdbConnectionManager.mLock` 全局锁，导致握手无限阻塞并连带锁死后续连接）；
  2. 改用独立 `PairingConnectionCtx` 实例 + `5.5s / 8.5s` 双重硬超时看门狗，任何异常或超时必立即中断并回显明确提示；
  3. 通知栏配对与取消按钮由 `PendingIntent.getBroadcast` 全面升级为 `PendingIntent.getForegroundService`（对齐 `Shizuku-Next`），彻底绕开 ColorOS `OplusHansManager` 超强冻结对后台广播的延迟队列（`p_BC`）；
  4. 修复 `NsdManager.unregisterServiceInfoCallback` 异步注销回调因局部线程池提前 `shutdownNow()` 触发 `ConnectivityThread` 抛出 `RejectedExecutionException` 导致后台进程静默崩溃、通知栏遗留「正在完成中」的隐蔽缺陷；
  5. 修复 `libadb` 的 `AdbStream` 在远端命令正常结束关闭流时对 `InputStreamReader` 抛出 `IOException("Stream closed.")` 误判为执行失败的问题；
  6. 移除内置旧版 `conscrypt-android:2.5.3` 原生库，全面切换至 Android 14~17 系统原生 `com.android.org.conscrypt.Conscrypt` TLS 1.3 + `exportKeyingMaterial`，Release 安装包体积缩减至约 **4.2 MB**。
- **默认保持状态栏与通知栏原运营商显示不变（对齐 `Simlet` 无痕行为）**：
  1. 新增并默认启用 **`US_KEEP_CARRIER`（保持原运营商显示）** 预设：仅覆盖 `sim_country_iso_override_string = "us"`，绝不修改 `KEY_CARRIER_NAME_OVERRIDE_BOOL` 或 `setOperatorBrandOverride`，状态栏与控制中心原样保留「中国电信 / 中国联通 / 中国移动」，同时海外与 AI 应用读取国家码仍为 `us`；
  2. 自动调用 `setOperatorBrandOverride(null)` 清理早期版本遗留的 `UiccProfile` 品牌名缓存；在已绑定无线调试或 Root 时点击运营商选项即可实时切换生效。
- **完善 Google Play 动态分包（Split APK）与后装应用的英语（`en-US`）同步**：
  1. 针对在中文系统下刚从 Google Play 安装的 TikTok 等动态分包应用（初装仅含 `split_config.zh.apk`），在写入 `en-US` 触发 Play 商店后台补齐 `split_config.en.apk` 时自动轮询等待英文分包落盘后再重载进程；
  2. 主界面快启坞与桌面守卫图标自动检测后装应用是否已完成 `en-US` 锁定，若为开启美境后新装的 TikTok 则先自动补写 `en-US` 再拉起应用。
- **适配 ColorOS 16 / 17 向导文案**：ColorOS 16 / 17（Android 16 / 17）已移除旧版「禁止权限监控」且原生支持无线调试特权委托，自动按系统 API 级别精准展示免额外开关指引。
- **重构全页面响应式自适应布局（兼容紧凑屏、常规直板机、大字号与折叠屏）**：
  1. 引入 `BoxWithConstraints` 自适应间距与 `600dp` 最大居中宽度，针对 `347dp` 等小尺寸高密度屏自动收窄外边距释放横向空间；
  2. 重构顶栏高度、2 列等宽对称「安全快启坞」、胶囊状态徽章（`StatusPillBadge`）与应用条目分层卡片（将「添加桌面防漏改图标」独立为底部胶囊按钮，彻底消除横向挤压折行）；
  3. 重构双卡区域卡片、权限横幅、首次激活向导与扩展应用抽屉，全面支持大字体缩放（`heightIn` + `FlowRow` 弹性换行）。

## [1.1.1] - 2026-11-20

### 修复与增强（Fixed & Improved — Android 17 / ColorOS 17 真机实测修复）
- **修复 Android 17 (API 37) `ACCESS_LOCAL_NETWORK` 本地网络权限拦截**：修复 `targetSdk = 36` 在 Android 17 真机（如一加 15T ColorOS 17）上因未弹出 `ACCESS_LOCAL_NETWORK` 授权框导致 `NsdManager` 被 `AppOps: ignore` 静默屏蔽、无法自动捕获无线调试配对弹窗端口的问题。
- **新增零权限内核双栈端口差分雷达与自动盲配引擎**：无需依赖 mDNS（不受本地网络权限、全局 VPN `tun` 或校园网 AP 隔离影响），自动通过回环双栈端口差分锁定配对弹窗端口；用户仅在通知栏填入 6 位数字配对码即可自动遍历候选端口完成绑定与重连。
- **修复「美境实际已生效但顶部卡片仍显示未激活」状态不同步问题**：当手机双卡已处于美区状态（如通过电脑 USB ADB 触发）而本机无线调试尚未配对时，顶部主控卡片准确展示「美境已生效（待绑定本机无线调试）」并直接开放安全快启坞。

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
