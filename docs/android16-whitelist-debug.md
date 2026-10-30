# Android 16 白名单专项审查与验证

本次基线：`feature/app-whitelist` / `2ed5bb56ca4e482582ba27e1fc3407a702aa8b5e`。
隔离诊断包：`io.github.hanghanglived.oconnect.whitelist.diagnostic`。
不覆盖原版 `io.pengyue.oconnect` 或已有白名单版 `io.github.hanghanglived.oconnect.whitelist`。
诊断版需手动建立自己的配置，后台、电池、通知设置也须单独核对。

## 已证实的源码问题

1. `AppWhitelistActivity.loadApps()` 只用 `queryIntentActivities(MAIN/LAUNCHER)`；
   Manifest 也只有启动器 intent queries。无启动器的 Google Play 服务不在枚举路径中。
   这能解释该服务不显示；不能单凭此断定 ChatGPT / Gemini 联网失败的全部原因。
2. 同方法使用 `selected.retainAll(available)`，把不可枚举的已保存包当作不可用项移除。
   打开页面本身未写入磁盘，但随后保存会持久化收窄后的名单。
3. `AndroidAppWhitelist.apply()` 将多类失败折叠为 `false`，管理线程仅提示跳过数量，
   `AppWhitelistConfig.apply()` 在部分成功时仍允许建立 TUN，无法追查具体漏掉哪个包。

## 核实后未发现的问题

- UI 保存真实 `ApplicationInfo.packageName`，没有把显示名称当成包名。
- 单一 `runVPN()` 建立路径：`getVpnServiceBuilder()` → `setIPInfo(b)` → 规则 → `b.establish()`。
  规则施加到同一个 Builder，在 establish 之前，没有中途更换 Builder。
- 每个管理线程构造时从该配置的 SharedPreferences 读取不可变快照。
  保存活动名单时，已有服务代码等待旧 TUN / 原生线程结束，再构造新线程读取新配置。
- 全局和白名单使用相同 `setIPInfo()`、IPv4 / IPv6 路由、DNS、搜索域和 MTU 逻辑；
  白名单分支没有自行删除默认路由、改 DNS、禁 IPv6 或关闭 DTLS。
- 外层原生 socket 走已有 `onProtectSocket()` → `VpnService.protect()`。
  原有内部 KeepAlive DNS 探针需要 VPN 应用自身获准访问隧道；本次保留这一行为。
- `DeviceStateReceiver` 使用原有 CONNECTIVITY_ACTION / active NetworkInfo；没有新增网络回调
  或 `bindProcessToNetwork`。本次仅隔离其广播标识，不改暂停、恢复或重连决策。
- target / compile SDK 为 36，min SDK 23；服务具有 BIND_VPN_SERVICE、systemExempted
  前台服务类型及相应权限。没有降低 target SDK。

上游 master 对照提交为 `8cc4fa6c0027e9021e81127b6980a6b352af1368`。
基线与当前上游并非简单包含关系，不能把“与上游不同”都视为白名单故障。

## 本次修改

- 仅为 7 个排查候选声明明确 package queries；查询结果决定是否展示，未假定安装。
  不引入 QUERY_ALL_PACKAGES。`getInstalledApplications(0)` 枚举当前用户可见应用，
  对已保存包另行查询；支持系统应用开关、名称/包名搜索、包名和 UID 展示。
- 不再在页面加载时删除选择。不可见、卸载或禁用项明确提示；只有用户明确移除才改草稿。
  共享 UID 可能共享路由，不自动增选依赖包；跨用户/工作资料需在对应 Android 用户中核实。
- 任一所选包无效或 addAllowedApplication 失败，终止本次接口建立；禁止部分成功后静默连接。
  用户应修正名单再连，不会回落为全局 VPN。稳定版与诊断版可随时切换。
- 本地内存中最多保留 6 代诊断：盐化配置标识、UI（本进程观察到时）/加载/候选/添加名单、
  UID、异常类型、连接状态、建立结果、DNS/地址/路由/MTU、重建原因、socket protect 结果。
  系统读取页补充 VPN capabilities、LinkProperties、Private DNS 和进程绑定情况；
  本应用未显式调用 setUnderlyingNetworks，显示系统管理，不通过隐藏 API 推断实际底层绑定。
  普通传输重连复用原有 TUN，不伪装成新接口代次；新接口记录 `connection_requested` 或 `whitelist_saved`。
- 新“VPN 诊断”页提供刷新、用户主动 SAF 导出；不复制旧认证日志，不自动上传。
  搜索域只保留存在性，配置 UUID 盐化。导出包含有诊断必要的包名、UID、地址和路由，分享前可检查。
- 诊断安装包的权限、广播、Provider 与已有 WL 隔离。KeepAlive / DTLS / DPD /
  网络切换和暂停恢复算法保持原样，没有更改服务器或协议。
- Actions 在诊断分支测试、lint、构建及验证包名、签名和四 ABI；复用仓库已存在的
  SHA256 校验导入脚本，从固定上游 v1.17.0 APK 导入未经修改的 16 个原生库。
  本次并未重新编译原生引擎。原生源码仍由固定子模块和 external/Makefile 提供。

## 2026-10-30 本机实际验证

- Gradle 8.13 / JDK 17.0.21，`testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest`：BUILD SUCCESSFUL。
- JUnit：20 项通过，0 失败、0 错误、0 跳过。
- Lint：0 错误，194 项非致命警告；本项目并非零警告代码库。
- 主 APK 与测试 runner APK 已生成；runner 未在设备执行。
- 主 APK 包名为独立 diagnostic 包，版本 1131 / 1.17.0-whitelist-diagnostic.2，min 23 / target 36。
- 主 APK 24,310,569 字节；SHA256：48a901d8d85833b42d4ac9eb7442fa85d2feb1514be605a6ef0f7bf097cffe4a。
- apksigner：v1 / v2 均有效；Debug 证书 SHA256：2760f37a9c876eb77f213e9d0cff73d4216914e87e3f74f7e1251003f58e63b7。
- 四 ABI 的 16 个原生库与经固定 SHA256 核对的上游发布 APK 逐字节相同，PT_LOAD 对齐至少 16 KB。
- 145 个 Manifest/资源 XML 可解析；git diff --check 通过。
- ADB 已实际运行，设备列表为空。Android 16 真机、VPN 登录、分流路径、锁屏、网络切换均未执行。
- 本地与 Actions 使用的 Debug 签名密钥可能不同，不能假定可相互覆盖安装；也不要卸载稳定版来解决签名问题。

## 真机矩阵（本次尚未执行，不是通过结果）

每次更改后保存、等待重新连接；需要 `establish=true`、`state=5` 和正确 added_packages。
新代次代表新 TUN；Wi-Fi/移动切换可能沿用原 TUN，不能要求每次都变更代次。
若 Android 开启“阻止未使用 VPN 的连接”，先记录该状态；它可能与未选应用直连的预期冲突。

| 场景 | 勾选 | 要验证 | 状态 |
| --- | --- | --- | --- |
| 1 全局 | 关闭白名单 | ChatGPT、Gemini、Chrome 访问 gemini.google.com | 未执行 |
| 2 ChatGPT 单包 | 实际安装的 ChatGPT | ChatGPT 联网，Chrome 直连，实际出口/网关观察 | 未执行 |
| 3 ChatGPT + GMS | 场景 2 + 实际存在的 GMS | 与场景 2 对比启动、登录、对话 | 未执行 |
| 4 Gemini 依赖 | 实际存在的 Gemini / Google / GMS / Play 商店 | 登录、加载、正常对话 | 未执行 |
| 5 全部相关 | 实际安装的相关包 | ChatGPT 与 Gemini 同时正常 | 未执行 |
| 6 生命周期 | 分别使用上述已确认名单 | 手动重连、锁屏 5/30/60 分钟、Wi-Fi/蜂窝切换、短暂断网、修改名单 | 未执行 |

分别导出场景 1、2、3、4 的诊断；Android 进程未退出时文件可包含最近 6 代用于对照。
不要只凭 VPN 图标或“已连接”判断路径；需已选应用的出口、受控网关流量证据或设备网络观测。
不要把另一个应用（例如 Chrome）的出口检查当作 ChatGPT 的路径证明。
Private DNS、IPv6、MTU、DTLS 只记录，若无对照证据不修改。

## 用户最少操作

1. 保留原稳定版，安装 OConnect WL Debug；手动填 VPN 配置，账号密码仅输入手机应用。
2. 先全局连接并测试 ChatGPT；再仅选 ChatGPT、重新连接复测。
3. 打开“显示系统应用”，搜索 `com.google.android.gms`，若实际存在则手动加入，重连再测。
4. 每次在“应用分流 → VPN 诊断”导出文件，注明该轮使用的名单和具体失败阶段。
   若场景 2 失败而场景 3 成功，这是依赖路径的实测线索；若两者均失败，继续比较基本规则和 DNS/路由。

可选只读核验：`powershell -File scripts/check-diagnostic-device.ps1 -Adb <adb.exe路径>`。
该脚本不安装 APK、不改设置、不读取 VPN 配置、不导出账号或认证日志。

## 官方接口依据

- [逐应用规则及 Builder 建立约束](https://developer.android.com/reference/android/net/VpnService.Builder)
- [Android 11+ 最小必要包可见性声明](https://developer.android.com/training/package-visibility/declaring)
- [Android 16 行为变化](https://developer.android.com/about/versions/16/behavior-changes-all)

本文仅报告源码证据及待测步骤。Galaxy S26 上 ChatGPT/Gemini 的最终联网与锁屏稳定性，
必须由真机矩阵验证后才能验收，不能用源码检查或编译成功代替。
