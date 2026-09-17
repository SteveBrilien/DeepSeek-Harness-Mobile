# 2026-09-18｜救援通道上线后的 DSH Mobile 诊断基线

范围：仅基于用户提供的手机终端成功输出、经固定主机密钥的橙派→手机独立 SSH、MCP 项目代码和既有测试报告；不访问个人文件/令牌，不重启隧道、不安装 APK、不清数据。此报告为诊断记录，不能当作修复完成或真机 UI 验收。

## G0：控制面与设备身份（2026-09-17 16:42–16:45 UTC）

- 用户手机 `ssh orange` 已输出 `ORANGEPI_OK` / `orangepiaipro-20t` / `orangepi`，旧 Azure 跳板主机公钥指纹已双向比对。
- 橙派项目 `scripts/rescue/pi-phone-ssh.sh u0_a232 --check` 实际输出 `RESCUE_SSH_OK` 与 UID `10232`；橙派 `ss` 显示仅 `127.0.0.1:22023` 监听。第二次独立登录再次通过。手机 Termux 的 `sshd` 进程可见，`dsh-rescue` tmux session 存在，反向 SSH 命令参数包括 `ExitOnForwardFailure=yes`。Termux 普通进程独立于 DSH App。尚**未**执行受控断线后的自动重连/重启、OriginOS 长时后台、APK 前后连续性测试；不能承诺无条件零中断。
- MCP `adb_devices` 返回设备空列表；手机 SDK30、V2115A，Termux `adb` 不存在，`getprop init.svc.adbd` 为 running，但 `settings get global adb_wifi_enabled` 被 Android 应用 UID 权限拒绝。不能凭 adbd 进程就断言 ADB TLS 已配对。下一步需要系统无线调试 UI 的用户授权和单独 loopback 端口验证；严禁公网开放 ADB、隐式提权。

## DSH Mobile 手机实际观测（仅普通 Termux 权限）

- `pm path com.stevebrilien.dshmobile` 找到 `base.apk`：证明当前已安装该包，但普通 Termux 无法读取私有 APK/确定当前 versionCode、versionName 或应用私有数据，须通过授权 ADB 或用户设置 UI 核对版本。
- `pidof com.stevebrilien.dshmobile` 未返回 PID，记为 `PID_NOT_VISIBLE`，因 Android 应用进程可见性约束，不是 App 已退出的充分证据。
- Termux 查询 `http://127.0.0.1:3080/healthz` 得到 curl 连接失败（HTTP 000）；仅说明该时刻该端口没有可达服务，可能是 runtime 未启动、服务端口不同或尚在初始化，不能独立判定 Crash / 26s 根因。不要尝试读私人 HTTP 数据。

## 当前源码/静态问题分层

- Git main HEAD 为救援文档提交 `65b9cbd`。当前工作树已有其他会话的未提交 Preview.6-dev/versionCode26 更改：AndroidManifest、ChatScreen、FilesScreen、NativeFileManager、build.gradle 以及新文件/测试。不要覆盖现有工作成果、推送、发布，旧 APK 为 Preview.5-dev/versionCode25，现有 `app/build/outputs/apk/debug/app-debug.apk` SHA256 `146f55405147c4b4980bfc373139973ce2c4fd62fbdcada2e5d71feee13aa1b5`，不能将其命名为最新 Preview.6。
- 当前 app 单测 XML `NativeTextEditorIntegrationTest` 共4例/4失败，均在 fixture 的 `NativeFileManager.browserRoot()`→`RecoveryVault.status()`→Android 11 `Environment.isExternalStorageManager()` 路径被 Robolectric 抛 `ArrayIndexOutOfBoundsException`，尚未触达文件编辑断言。`TextFileSafetyTest` 自身先前未报失败。考虑 SDK30 环境 API 异常时的 fail-closed 存储降级（不得放大读写权限）后复测；不得更换测试目标到 SDK28 来冒充 Android11 通过。
- 既有设备问题仍依据 `2026-09-16-device-ui-issue-inventory.md` 与 `2026-09-17-preview4-device-regressions-and-scope.md`：Settings 几何、右栏入口、抽屉闪影/手势、IME 弹跳、26s 启动、多选 1/2/3/5 件真正发送、文件批量与 Trash 恢复等均未通过真实 OriginOS 验收。旧 Chromium/Gradle PASS 不能替代真机。

## 下一步门禁

1. 非破坏性修正 API 查询异常兜底，重新跑 Android 11 Robolectric 四例与 Android 单测、Lint、差异检查；记录终态失败/通过，不自动发布。
2. 在已有救援 SSH 下，协助用户在 Android 11 系统 UI 开启无线调试及 TLS 配对，建立独立于救援 SSH 的仅回环 ADB 转发。严格核对 device serial/model/UID，确保不影响 `22022/22023`。
3. 只有 ADB 成功后才能读取包版本、当前 Activity、脱敏 logcat/IME/WindowInsets/WebView geometry/chooser 数量；先收集同一时间轴的真实证据再修改 UI。
4. APK 安装还需要签名/回退/用户数据备份、救援自动重连及更新前后救援复测；在此前不调用 install、force-stop 或清理。


## 2026-09-18 00:54 +08｜已实施的最小修复与复测（覆盖上文“考虑修复”的历史状态）

- 只对 Android 11+ 平台权限查询异常增加失败关闭（`getOrDefault(false)`）：`RecoveryVault.status()` 与 `NativeFileManager.browserRoot()` 遇到 `Environment.isExternalStorageManager()` 抛异常时，不授予广域存储权限，不将异常传播到编辑器；SDK30 测试配置未降级。保留原工作树其他所有未提交文件，无设备修改。未验证生产 Vivo 该 API 曾异常，不将 Robolectric 触发点冒充真机崩溃原因。
- Android 单元测试耐久任务 `task-android_unit_test-7fb0e510a0304290b912`：终态 `succeeded/exit 0`，Gradle `BUILD SUCCESSFUL`, 161 actionable tasks；Robolectric `NativeTextEditorIntegrationTest` 原 4/4 FAIL 变为 4/4 PASS。XML 综合：app 31/31、core/recovery 12/12、core/runtime-android 15/15，0 failures、0 errors（合计 58 例，统计仅上述三个拥有测试 XML 的模块）。
- Android Lint 耐久任务 `task-android_lint-c98103fa1ab04820b577`：终态 `succeeded/exit 0`，Gradle 289 actionable tasks、`BUILD SUCCESSFUL`；`app/build/reports/lint-results-debug.txt` 报 `0 errors, 23 warnings`。已有告警含 `WebViewCompat.addWebMessageListener` 缺 `isFeatureSupported` 显式守卫、`WebViewClient` 未实现 `onRenderProcessGone` 可能导致 renderer 退出时 app 崩溃等；本轮尚未对所有 23 条归因与修复。**Lint 任务成功不等于零告警或运行稳定。**
- 使用项目内 Node24 v24.18.1 运行 JS syntax / mobile-ui-policy / webview-compat-policy 和 `git diff --check` 均 PASS；默认 `/usr/bin/node` 仅 v12，直接执行 policy 曾因 top-level await 语法不支持失败，是工具链调用错误，已使用 Node24 成功复核。
- 00:53 左右再次通过独立、指纹固定的 Pi→phone SSH：`RESCUE_SSH_OK`/UID 10232；并未执行断线重连或 Android app force-stop/安装。
- 当前版本仍是磁盘上的 Preview.6/code26 **未发布工作树**，旧本地 APK Preview.5/code25；本轮没有重新构建或替换该 APK、没有提交/推送/OTA、没有访问用户 app 私有数据。真实 UI 26s/IME/抽屉/文件多选/相机系统选择器依旧等待无线 ADB 真机门禁，不能宣称已修复。

## 2026-09-18 01:33 +08｜无线调试配对与救援掉线（新增，覆盖上文早期在线状态）

- 用户截图显示 Android 11 Vivo 无线调试开关已开、连接端口 `38653`，弹窗配对端口 `43231`（一次性临时端口）；还出现「请连接到 WLAN 网络」与「某应用遮挡了权限请求界面」提示。**不保留、传播或写入截屏中出现的六位配对码。** 截图不能证明实际完成了 Pi 的 ADB 配对。
- 借原来已验证的 `127.0.0.1:22023` 救援入口、固定手机主机密钥及单独 Pi 私钥，SSH 确认 phone: `127.0.0.1:38653` 和 `10.33.221.78:38653` 开放，但配对端口 `43231` 此刻关闭（配对对话框离开/端口更新等原因未定）。独立在 phone Termux 新建 `dsh-adb-bridge` tmux，只将 phone loopback `38653` 反向转发到 Pi `127.0.0.1:25555`；初次确认 Pi 监听，TCP handshake 可连接，原 `dsh-rescue` session 仍存在。没有暴露公网端口或改动 22022/22023 配置。
- 通过受控临时 TaskProfile 执行 ADB connect 试验，Runner 明确报告 `network_policy.effective=false / forced_isolation=true / reason=adb-capability-private-network`，ADB 连接 `127.0.0.1:25555` 从隔离命名空间得到 `Connection refused`，`adb devices` 空。这是权限隔离阻断而非 Pi 端转发失败的证明；task 返回 `succeeded` 是测试命令容忍连接失败，**ADB pairing/connect 实际 FAIL/BLOCKED**。临时 TaskProfile 已移除，不通过复制 adb 二进制、取消沙箱、解锁全局 host channel 绕过安全限制。
- 提交受限 HostCapability 提案 `cap-proposal-db187807553c4f64bc04a374bb752e28`，审核状态 `reviewed/needs_admin_approval`，只允许管理员审批后固定 Pi loopback `25554` 配对、`25555` 连接等操作，不申请一般 host shell。尚未批准或启用。
- 01:31 +08 后独立只读复核发现 Pi `127.0.0.1:22023` 和 `:25555` **均不再监听**；SSH 连接 `22023` 报 `Connection refused`，四次相隔 3s 重试和 01:32 再查仍为 DOWN；原 `orangepi-azure-tunnel@22022` systemd 服务单独报告 active/running。当前无法访问手机 Termux 捕获断线日志，原因未定（需优先核对 WLAN、Termux/tmux、OriginOS 后台）。不能再宣称当前救援在线，也不得因此安装、强停、重启 App 或修改隧道。
- 下一步门禁：用户恢复/唤醒 Termux 和 `dsh-rescue` 后，重新核验 Pi `22023` + 指纹固定 SSH；若用户从 Windows 登录 Pi 可由用户本人使用 host ADB 在受限 loopback 通道配对，或等待 HostCapability 管理员审批。无论哪条路径，必须先恢复救援、确认最新端口/配对弹窗，核对 ADB device model/serial；然后才可执行只读包版本、进程、IME/Window/WebView 证据采集。不要复用过期配对码或把当前工作树 APK 当发布版。
