# 更新日志（Changelog）

本项目遵循 [语义化版本（Semantic Versioning）](https://semver.org/lang/zh-CN/) 与 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/) 规范。

## [1.4.0] - 2026-11-21

### 新增能力（Added — 双模美区时区引擎：全局同步 + 单应用随开随切）
- **新增「美区时区引擎（`TimezoneOverrideEngine` + `CompanionTimezoneService`）」双模式自由选择**：
  1. **保持本机原时区（默认推荐）**：不改动系统状态栏与日程时间，适合日常使用 Claude、ChatGPT、X；
  2. **方案一：全局跟随美境同步切时区（`GLOBAL_US`）**：开启美境时自动关闭基站 NITZ 自动时区覆写并将系统切至所选美国时区，执行「还原默认」时自动恢复 `Asia/Shanghai` 及原自动时区开关；
  3. **方案二：随开随切 · 退出即还（`COMPANION_APP` 单应用动态伴随模式）**：针对主力机既需应对 TikTok 等严格时区风控、又不希望平时看错微信/闹钟时间的痛点——从美境快启坞、应用页或桌面守卫图标启动勾选应用前，毫秒级切入美区时区；通过特权通道自动静默授权 `GET_USAGE_STATS` 感知应用退出（含输入法与系统弹窗防误判缓冲），退回桌面约 4 秒后**自动归位原时区并销毁服务**（平时零驻留、零耗电，通知栏亦提供一键立即归位按钮）。
- **五大美国标准时区预设与四大自然功能入口**：
  - 内置美西洛杉矶（`America/Los_Angeles` 默认）、美东纽约、美中芝加哥、山地丹佛、夏威夷五大标准时区与实时双时钟对照；
  - 入口全打通：**工作台 → 核心能力矩阵「美区时区引擎」**、**应用页每款应用的「伴随时区」快捷胶囊**、**设置中心 → 美区时区工作策略** 以及 **环境透视自检抽屉**。

## [1.3.0] - 2026-11-20

### 视觉重塑与模块化产品架构升级（UI/UX & Brand Overhaul）
- **全新立体高精自适应桌面图标与品牌徽标（Adaptive Icon + Monochrome 主题图标）**：
  - 重绘 `ic_launcher_background`、`ic_launcher_foreground` 与 `ic_launcher_monochrome`：采用深邃黑曜蓝宝石基底、极光环境背光、阳极氧化钛金属倒角外环、八切面水晶罗盘主星与翡翠就绪信号星点，同时原生支持 Android 13~17 系统主题单色图标与全界面动态呼吸品牌徽标（`StatesideBrandEmblem`）。
- **由单页 Demo 升级为四大专项工作区模块化架构（自适应手机底部导航与折叠屏/大屏侧边导航栏）**：
  1. **工作台（`Dashboard`）**：深海蓝宝石主控仪表舱（实时展示号卡国家码、状态栏显标、英语应用就绪度与上次同步时间）+ 对称安全快启坞 + 可扩展 **「核心能力矩阵」Bento 面板**；
  2. **号卡工作坊（`SIM Studio`）**：顶部内置 **系统状态栏实时仿真预览窗**，双卡槽独立快捷胶囊（一键设为 `X` / 保持原卡名 / 跟随全局 / 自由定制），清晰呈现物理归属与穿透状态；
  3. **应用语言沙盒（`App Engine`）**：直观对比「手机全局中文 ↔ 受控应用 `en-US`」，全面升级为现代 `Switch` 开关，并内置直达「安全启动」与「桌面防漏改图标」快捷操作；
  4. **正式设置中心（`Settings`）**：彻底告别旧版点击设置只弹引导页的临时做法，重构为完整的特权通道管理（配对向导、直达无线调试、固定端口覆盖、重置配对记录）、系统权限健康度自检、厂商后台保活跳转、**深浅色模式与 Material You 壁纸取色开关**、环境维护与开源关于中心。
- **新增「环境透视与合规自检（Environment Inspector）」模块**：支持一键核验 SIM 国家码、基站注册 ISO、语言隔离与系统时区（附系统日期与时区快捷跳转），并支持一键复制脱敏诊断摘要。
- **应用图标 LRU 内存缓存**：消除切换页面或滚动列表时的应用图标闪烁重载。

## [1.2.0] - 2026-11-20

### 新增与突破（Added & Breakthrough — 一加 15 `PLK110` 全球通钻卡真机实测通过）
- **攻克中国移动「全球通钻卡 / 白金卡 / 动感地带」5G SA 核心网 NITZ 尊享显标锁死状态栏难题**：
  - 传统改卡工具（如 `Simlet`）仅覆盖 `carrier_name_string (EF_SPN)`，在开通了 5G 核心网尊享显标的号卡上会被基站实时下发的 `CellIdentityNr.mAlphaLong` 与 `rule=1 (showSpn=false, showPlmn=true)` 拦截，导致状态栏无法改名；
  - 美境 `SimRegionOverrideEngine` 新增 **3GPP `EF_PNN`（`pnn_override_string_array`）+ `EF_OPL` 清空 + 等效归属网络（`ehplmn` + `force_home_network_bool`）+ `spn_display_condition_override_int = 1` + `config_carrier_support_nitz = false` + OEM PLMN 映射清空** 组合穿透机制，在 `CarrierDisplayNameResolver (CDNR)` 与 `OplusQSCarrierTextController` 层实现毫秒级改名生效。
- **新增「分卡槽独立状态栏文案定制」与全局自定义名称（如卡 1 设为 `X`、卡 2 设为 `Zero` 或保留原名）**：
  - 支持全局「保持原运营商（推荐）」、「自定义（如 `X`）」及美国主流运营商预设；
  - 双卡机型每张电话卡卡片均支持点击 **「单独设卡 1 / 卡 2 显示名」**，互不干扰地独立指定任意自定义名称、保留原运营商或跟随全局模式；
  - 主界面电话卡状态直接读取 `SubscriptionInfo.carrierName`（系统状态栏真实裁决结果），所见即所得。

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
