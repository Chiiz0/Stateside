# 安全架构与隐私白皮书（Security & Privacy Policy）

「美境（Stateside）」是一款运行于手机本地的系统环境配置工具。由于涉及本机无线调试与电话卡区域覆盖能力，我们以最高透明度公开全部权限用途、数据边界与底层安全机制。

---

## 1. 零外网通信与隐私边界

| Android 权限声明 | 唯一真实用途 | 是否联网 / 外传数据 |
| :--- | :--- | :--- |
| `INTERNET` | **仅用于连接本机回环地址 `127.0.0.1` 的无线调试端口** | **绝不连接任何外部 IP 或域名** |
| `ACCESS_WIFI_STATE` / `CHANGE_WIFI_MULTICAST_STATE` / `ACCESS_NETWORK_STATE` | 本机 mDNS（`_adb-tls-pairing._tcp` / `_adb-tls-connect._tcp`）端口发现与开机 Wi-Fi 就绪自愈触发 | 否 |
| `ACCESS_LOCAL_NETWORK` | Android 17 (API 37) 本地网络保护前向兼容声明 | 否 |
| `READ_PHONE_STATE` | 在本机界面展示卡 1 / 卡 2 物理归属地与当前对外上报的国家码（`US` / `CN`），以及桌面快捷方式启动前毫秒级防漏改核验 | 否（不读取手机号、通话记录或短信） |
| `QUERY_ALL_PACKAGES` | 识别手机内是否已安装 TikTok、X、Claude、ChatGPT 并在扩展应用抽屉展示用户安装的应用列表 | 否 |
| `POST_NOTIFICATIONS` / `FOREGROUND_SERVICE_SPECIAL_USE` | 首次激活时在系统设置页面提供下拉通知栏直接输入 6 位无线调试配对码的能力 | 否 |
| `RECEIVE_BOOT_COMPLETED` | 手机重启连上授权 Wi-Fi 后自动静默补写内存态美区运营商配置 | 否 |

您可以在构建产物或源码中随时通过以下命令自行审计（除标准 XML 命名空间与开源协议链接外，无任何远程服务器端点）：

```bash
grep -rn "https\?://" app/src/main/
```

---

## 2. 密钥隔离与组件导出防护

1. **本机 ADB 身份密钥隔离**：
   - 首次配对生成的 2048-bit RSA 私钥（`stateside_adb_rsa.pk8`）保存在 `DeviceProtectedStorage` 的 `noBackupFilesDir` 中，文件权限显式收敛为仅应用自身 UID 可读写（`0600`）。
   - `AndroidManifest.xml` 全局设置 `android:allowBackup="false"`，杜绝通过备份通道导出私钥。
2. **内部服务防外部伪造调用**：
   - `AdbPairingService`、`BootRestoreJobService` 与 `PairingActionReceiver` 均声明为 `android:exported="false"`（或受系统级 `BIND_JOB_SERVICE` 权限保护），第三方普通应用无法伪造广播或指令驱动本应用发起配对。

---

## 3. 非持久化改卡安全性（零基带风险）

- 本项目**绝不修改**手机基带（Modem / EFS / NVRAM）或系统分区，仅通过 Android 框架标准接口 `CarrierConfigManager.overrideConfig(subId, bundle, /* persistent = */ false)` 在内存会话层覆盖 `sim_country_iso_override_string` 与运营商显示名。
- 一旦点击应用内「一键恢复国内默认状态」，或关闭自愈开关后重启手机，所有参数即刻 100% 恢复出厂原卡状态，不影响日常国内通话、短信、5G 流量与 VoLTE。

---

## 4. 安全漏洞报告（Reporting a Vulnerability）

如果您在本项目中发现潜在权限越界、密钥存储隐患或本地提权风险：
- 请优先通过 GitHub 仓库的 **[Private Vulnerability Reporting（安全顾问私密报告）](https://github.com/Chiiz0/Stateside/security/advisories/new)** 提交详情与复现步骤，请勿在修复发布前于公开 Issue 披露可被恶意利用的细节。
- 维护团队将在 **72 小时内**响应确认并跟进修复补丁。
