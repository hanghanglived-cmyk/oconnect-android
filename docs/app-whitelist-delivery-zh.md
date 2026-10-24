# OConnect 应用分流增强版：交付说明

## 下载与源码

- [用户 Fork](https://github.com/hanghanglived-cmyk/oconnect-android)
- [开发分支 feature/app-whitelist](https://github.com/hanghanglived-cmyk/oconnect-android/tree/feature/app-whitelist)
- [成功的 Actions 构建与日志](https://github.com/hanghanglived-cmyk/oconnect-android/actions/runs/40052487704)
- [Actions APK 下载（ZIP，需解压）](https://github.com/hanghanglived-cmyk/oconnect-android/actions/runs/40052487704/artifacts/12544248491)
- [详细验证记录](app-whitelist-validation.md)及[源码、许可和复现方法](app-whitelist.md)

本地主交付包：`OConnect-WL-v1.17.0-whitelist.1-debug.apk`，24,202,187 字节。
SHA256：`fd0357b596aed035ac2c4e9ce543e57b5e0c9906aceea3bea3777d7ba61cc935`。

**本地包和 Actions 包均为 Debug APK，签名密钥不同。请选择一种来源安装；
不能假定二者能相互覆盖升级。未制作正式签名的 Release APK。**

## 已实现的功能和修改清单

在每个 VPN 配置的编辑页新增“应用分流”：默认关闭，按名称/包名搜索，展示实际应用的
名称、图标和包名，多选、取消、计数、保存、确认清空；提供中文和英文资源，沿用 Material 主题。
选择与启用状态保存在各配置的私有 SharedPreferences，不会在不同配置之间串用。

规则在 `establish()` 前通过官方 `addAllowedApplication()` 设置。无有效选择则阻止建立接口；
卸载、禁用和无效包名会被跳过并提示数量，不记录应用列表。修改正在使用的规则后，
等待旧线程和接口退出，再走原有连接流程。关闭模式时只调整预选列表不会进行多余重连。

主要文件：

- `AppWhitelistActivity`、两个布局及中英文资源：应用选择页面。
- `AppWhitelistConfig`、`AndroidAppWhitelist`：快照、规则校验和存储。
- `OpenConnectManagementThread`：接口建立前接入规则。
- `OpenVpnService`：保存后等待退出再连接的窄路径，未重写普通网络重连。
- `ConnectionEditorFragment` 与配置 XML：逐配置入口。
- Gradle、Manifest、相关内部广播/权限：独立安装和命名空间隔离。
- `Application` 与更新检查：关闭旧自动崩溃上传和官方包升级提示。
- 测试、Actions、原生库导入脚本、外层 Makefile 的安装路径修复与文档。

VPN 服务自身保留内部隧道访问以维持原版 DNS 心跳，不算作用户选择的有效应用。
外层连接仍使用原来的 `protect()`。第一版只有允许名单，没有混用禁止名单；不要求 Root。

基线为 `v1.17.0` 的 `cd1d747cc3a321eeeee5b0e54d73a8062e77a1d4`。
本地主交付包的 16 个原生库与原版发布包逐字节一致，云端另外通过完整原生源码构建。
未修改加密、认证、DTLS、DPD、DNS、网段路由、网络变化处理和锁屏策略。

## 实际验证结果

| 检查 | 结果 |
| --- | --- |
| Windows Gradle 构建 | 成功，80 个任务执行 |
| 单元测试 | 18 项通过，其中白名单测试 10 项 |
| Lint | 通过，0 错误，189 项非致命警告 |
| 主 Debug APK | 已生成，CRC、签名、包名、SDK 和原生库已验证 |
| 测试 APK | 编译成功，未在设备执行 |
| GitHub Actions | 全流程成功，含 NDK 27.2.12479018 四架构原生构建 |
| Android 16 真机 | 未测试，ADB 没有连接设备 |
| VPS001、锁屏、网络切换 | 未测试，未获取或使用用户 VPN 凭据 |

已验证 targetSdk 36、四种 ABI 和原生库 16 KB ELF 对齐。
**编译成功不等于已验证 Samsung Galaxy S26 的实际连接稳定性。**

## 安装和使用

1. 保留稳定运行的官方 OConnect，不卸载、不清数据。
2. 把主测试 APK 复制到手机安装，按系统提示允许所选来源安装。
   Actions Artifact 要先解压找到 `app-debug.apk`，不要安装测试 runner APK。
3. 打开 **OConnect WL**，手动创建 VPN 配置；凭据只输入手机应用，不发送到聊天或 GitHub。
4. 先关闭白名单测试普通连接，再在该配置编辑页进入“应用分流”，勾选应用、启用并保存。
5. 修改活动规则会短暂断开，可能需要再次认证。对齐官方版的电池、后台与通知设置；
   系统按应用保存这些设置，新包名不会自动继承。

包名为 `io.github.hanghanglived.oconnect.whitelist`。FileProvider、权限、内部广播等已隔离，
测试版不会自动读取官方版的私有配置。Android 每个用户只能有一个活跃 VPN；连接 WL
会切换当前 VPN，但不会覆盖官方应用或其数据。恢复时断开 WL，在官方版重新连接。

## Samsung Galaxy S26 / Android 16 验收清单

使用可信的出口 IP 检查地址或网关观测验证流量，不能只看状态栏 VPN 图标。

- [ ] 两款应用同时安装，数据互不影响。
- [ ] 关闭名单时，连接、DNS、DTLS 和路由与原版对比正常。
- [ ] 开启名单后，已选应用经 VPN、未选应用直连，分别检查 IPv4/IPv6 与 DNS。
- [ ] 断开再连、重启 WL、切换两个 VPN 配置，状态和选择保持正确。
- [ ] Wi-Fi 切移动数据再切回，规则保持并自动重连。
- [ ] 锁屏 5、30、60 分钟恢复后核对连接和出口。
- [ ] 卸载一个已选应用可跳过；全部失效时阻止创建接口。
- [ ] 连接中修改名单并保存，新接口使用新规则。
- [ ] 重建中停止连接或撤销 VPN 授权，不意外自动重启。
- [ ] 关闭名单并保存，恢复原版路由行为。
- [ ] 搜索、多选、清空确认、旋转、中文/英文和深浅主题正常。

## 已知限制和维护

- 仅列出当前 Android 用户有启动器入口的应用，不包含所有无界面服务及其他工作资料用户；
  没有为列全应用申请 `QUERY_ALL_PACKAGES`。
- 允许名单仍受网关/网段路由限制，不强制改成全隧道。系统“阻止未使用 VPN 的连接”或
  企业策略可能阻止未选应用直连。
- 共享 UID 的应用可能共享路由；应用委托其他包的浏览器等操作也需单独考虑。
- 新包名不继承官方版电池设置，锁屏和网络切换必须真机验证。
- CI Debug 密钥可能随运行变化。长期升级应使用固定、妥善保管的签名密钥；
  不要为解决签名冲突直接卸载已有 WL，否则会丢失其私有配置。
- 关闭了旧自动崩溃上传、官方包更新检查和云备份，未上传应用列表、分流配置或 VPN 凭据。
- 未修改原作者仓库、未向原作者创建 PR、未强制推送或删除已有分支。
- 保留 GPLv2+ 和依赖许可证，分发时保留对应源码、锁定的子模块和相关许可。
