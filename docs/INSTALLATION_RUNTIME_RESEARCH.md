# DSH Mobile 安装与本地 Runtime 启动路线调研

> 日期：2026-09-11  
> 适用项目：DeepSeek Harness Mobile（Android 11 / OriginOS）  
> 状态：实施中的技术依据；最终验证数据将在打包前补入本文

## 1. 结论摘要

当前“正在连接本地 Runtime…”长期不结束，并不只是一个加载动画问题，而是安装、配置迁移、Runtime 启动和 UI 状态传播耦合后产生的系统性故障：

1. 首页只轮询 Web 端口，后台服务即使已经失败，UI 仍会继续等待约 3 分钟；
2. 复用旧 Runtime 时，启动遥测可能沿用旧状态，不能可靠反映本次启动；
3. 启动路径会在版本迁移时进入 PRoot，调用 DSH/包管理器修改持久化 Web profile；这是耗时、可变且难诊断的操作；
4. 现有 ARM64 E2E 验证了 Linux/Node/DSH 内容，但没有覆盖 Android Service、遥测和 Compose 首页的失败传播；
5. 当前宿主虽为 ARM64，但执行沙箱未暴露 KVM、Binder 设备或 Docker daemon，不能诚实地声称已运行完整 Android 虚拟机。

本次采用的路线是：

- 保留当前 Termux PRoot 技术基线，不更换 Runtime 引擎；
- 把内置移动插件迁移改为宿主侧、离线、幂等的确定性文件协调，不在启动阶段运行包管理器；
- 为每个启动阶段提供结构化进度，把后台失败立即传到首页；
- 以 Robolectric 覆盖 Android 生命周期/状态传播，以 ARM64 + PRoot/Alpine E2E 覆盖 Runtime 内容和 Web 启动；
- 两层模拟全部通过后，才允许编译、签名和登记 APK；真机覆盖安装仍作为最终设备验收项。

这条路线借鉴了 Termux/PRoot Distro 的“安装/缓存与运行分离”思路，同时保持项目既有 A/B Runtime、Recovery Core 和官方 DSH Web UI 约束。

## 2. 问题范围与已观察现象

设备截图显示：

- 应用停在“正在启动 DSH / 正在连接本地 Runtime…”；
- 底部导航和主进程仍响应，说明不是 Activity 整体崩溃；
- 页面未显示失败原因，也没有可复制诊断信息；
- 当前界面对应 `ChatScreen` 的 `Checking` 状态。

代码追踪得到的当前链路：

```text
ChatScreen
  -> RuntimeForegroundService.ACTION_START
  -> RuntimeControlPlane.start()
  -> AndroidRuntimeManager.start()
  -> 前置检查
  -> 写入 mobile context
  -> 确保 mobile plugins
  -> PRoot 内启动 Node/DSH Web
  -> 等待 127.0.0.1 Web 端口
```

旧 UI 只检查最后一步是否成功，没有消费中间阶段和服务失败。因此任何前置检查、插件迁移、PRoot 或 Node 错误，都会表现成同一个长时间转圈。

## 3. 外部方案调查

### 3.1 Termux PRoot

Termux 维护的 PRoot 是 Android 无 root Linux 用户空间方案。它通过 `ptrace` 模拟类似 `chroot` 的文件系统视图，并包含 Android/Termux 兼容补丁。

与本项目直接相关的事实：

- Termux 当前构建配方版本为 `5.1.107.92`，与项目内置 PRoot 基线一致；
- 配方显式依赖 Android 共享内存兼容库和 `libtalloc`；
- Termux 对 loader 做独立处理，说明 loader、ABI、Android linker 是不可忽略的启动前置条件。

判断：项目无需为本次故障更换 PRoot。现有版本并非明显落后，应先修复状态传播和启动阶段的非确定性。

来源：

- [Termux PRoot 源码](https://github.com/termux/proot)
- [Termux PRoot 构建配方](https://github.com/termux/termux-packages/blob/master/packages/proot/build.sh)

### 3.2 PRoot Distro

PRoot Distro 的主要工程启发是生命周期边界：

- 安装/恢复 rootfs 是显式阶段；
- distribution 缓存与运行命令分开；
- 登录/运行时不承担不必要的包安装工作；
- rootfs 可重建，用户数据和配置需单独考虑。

判断：DSH Mobile 应把内置插件协调放在“安装/升级协调”层，以文件清单和版本标记完成；正常启动只做快速校验和进程拉起。

来源：

- [Termux PRoot Distro](https://github.com/termux/proot-distro)

### 3.3 UserLAnd

UserLAnd 同样证明了无 root Android 上运行 Linux 用户空间是成熟可行的产品路线。它支持继续采用 PRoot 类方案，但不能证明任意 rootfs、loader 或应用私有目录布局都天然兼容。

判断：UserLAnd 是路线可行性的旁证，不是可直接替换本项目 Runtime 管理层的组件。DSH Mobile 仍需维护自身 slot、manifest、Recovery 和 Web profile 合约。

来源：

- [UserLAnd](https://github.com/CypherpunkArmory/UserLAnd)

### 3.4 Android 可执行文件限制

Android 10 起，面向 API 29 及以上的应用不能从应用可写 home 目录执行二进制文件；官方将其归入 W^X 行为变更。项目目前固定 `targetSdk 28`，是为了允许 app-private Runtime 执行，而非遗漏升级。

判断：

- 本修复保持 `targetSdk 28`；
- 没有替代架构时不擅自提升 target SDK；
- 该选择带来分发和长期维护风险，后续需单独治理。

来源：

- [Android 10 行为变更：从应用 home 目录执行文件](https://developer.android.com/about/versions/10/behavior-changes-10)

### 3.5 Android 模拟路线

#### Cuttlefish

Android 官方 Cuttlefish 可运行 ARM64 虚拟设备，官方目标包含 `aosp_cf_arm64_only_phone-userdebug`，但宿主需要 KVM 等虚拟化能力。

- [Cuttlefish 入门](https://source.android.com/docs/devices/cuttlefish/get-started)

#### redroid

redroid 可在 Linux 容器中运行包括 Android 11 在内的 Android 系统，并支持 ARM64；它需要 Binder/ashmem 等内核能力和高权限容器。

- [redroid 文档](https://github.com/remote-android/redroid-doc)

#### Robolectric

Robolectric 在宿主 JVM 中提供 Android framework shadows，适合验证 Service、Intent、SharedPreferences/遥测和 UI 状态决策。它不是完整设备模拟器，不能验证 Android linker 或真实 PRoot 二进制执行。

- [Robolectric Getting Started](https://robolectric.org/getting-started/)
- [Robolectric Releases](https://github.com/robolectric/robolectric/releases)
- [Android 本地测试指南](https://developer.android.com/training/testing/local-tests)

## 4. 方案比较

| 路线 | 能覆盖的风险 | 当前环境可运行 | 代价/限制 | 本次决策 |
|---|---|---:|---|---|
| Robolectric JVM 模拟 | Service、Intent、遥测、状态机、失败即时呈现 | 是 | 不运行真实 Android linker/PRoot | 采用 |
| ARM64 Linux + PRoot/Alpine E2E | rootfs、Node、DSH Web、profile 升级、端口就绪 | 是 | 不是 Android framework | 采用 |
| ADB 真机 | OriginOS、linker、SELinux、前后台限制、覆盖安装 | 当前无设备 | 依赖在线设备 | 发布验收 |
| Cuttlefish ARM64 | 完整 Android framework/系统镜像 | 否 | 当前沙箱无 `/dev/kvm` | 环境具备后加入 |
| redroid Android 11 | 容器化完整 Android | 否 | 当前无 Binder 设备及 Docker daemon | 环境具备后加入 |
| 更换 UserLAnd/其他 Runtime | 可能绕开部分自研问题 | 不必要 | 破坏 A/B、Recovery、DSH 合约，迁移成本高 | 不采用 |
| 启动时继续运行 pnpm/插件安装 | 可复用现有 CLI | 是但不稳定 | 慢、难回滚、错误面大 | 移除 |

## 5. 推荐技术路线

### 5.1 启动状态改为单一可观测流水线

为 Runtime 启动定义稳定阶段：

1. `verify-start-prerequisites`
2. `write-mobile-context`
3. `ensure-mobile-plugins`
4. `spawn-dsh-web`
5. `wait-web-ready`
6. `web-ready`

每个阶段输出机器可读 stage、用户可读 message、脱敏诊断行和本次启动经过时间。后台服务持久化状态，首页只渲染状态，不再自行猜测故障。服务失败后，首页应在下一次短轮询内停止动画并显示原因。

### 5.2 内置插件采用离线确定性协调

内置 `@dsh-mobile/dsh-mobile-context` 与 `dsh-client-ui-mobile` 属于 APK 版本的一部分，不应在应用启动时交给包管理器重新解析。

协调器应：

1. 先解析并验证现有 `profiles/web/package.json`；
2. 原子复制 APK 内置、已校验的插件文件到持久化插件目录；
3. 原子复制同一组文件到 Web profile 的 `node_modules` 目标；
4. 只补齐这两个 dependency 和 bundle 声明，保留用户其他依赖、bundle 及字段；
5. 全部成功后才提交版本 marker；
6. 任一步失败都保持可重试的幂等状态，不清空 profile，不覆盖用户 workspace。

这可消除启动阶段 10 分钟包管理器超时、网络/registry 变化和 pnpm 链接布局差异。

### 5.3 Runtime 存在性以磁盘 inventory 为准

遥测是观察结果，不是事实来源。服务每次启动先检查 slot inventory：

- 可复用 slot 存在：创建新的启动遥测并开始阶段上报；
- slot 不存在：进入安装流水线；
- 旧失败或旧安装遥测不得改变磁盘事实。

### 5.4 保持 A/B 与 Recovery 边界

本次不改变：非活动 slot 更新、验证后切换、失败保留旧 slot、Recovery Core 独立、不清用户工作区、不记录 token 或完整私密 URL。

## 6. 模拟与发布验证矩阵

最终 APK 生成前按以下顺序执行：

| 门槛 | 验证目标 | 通过条件 |
|---|---|---|
| Kotlin 单元测试 | 协调器幂等、保留自定义项、旧版本升级 | 全部通过 |
| Robolectric（API 30） | Service、遥测重置、阶段推进、失败即时呈现 | 全部通过 |
| 合约测试 | mobile context、插件声明、资产版本一致 | 全部通过 |
| ARM64 Runtime E2E | rootfs、Node/DSH 命令、Web 端口就绪 | 全部通过 |
| 旧 profile 升级模拟 | 复用历史 slot，不清数据即可启动 | 全部通过 |
| Android lint | 静态与资源问题 | 无阻断问题 |
| Debug/Release 编译 | APK 可重复构建 | 成功 |
| APK 签名校验 | release artifact 签名与摘要 | 成功 |
| 真机覆盖安装 | Android 11 OriginOS 保留数据升级 | 待在线设备 |

若 Robolectric 通过而 ARM64 E2E 失败，应检查 Runtime 内容、profile 或 Node/DSH。若 ARM64 E2E 通过而真机失败，应优先检查 Android linker、SELinux、应用私有目录权限和前台服务限制。

## 7. 失败后的路线复盘

禁止仅靠延长超时或增加重试掩盖问题：

| 失败阶段 | 首要复盘对象 |
|---|---|
| `verify-start-prerequisites` | ABI、loader、symlink、可执行位、slot manifest |
| `write-mobile-context` | 原子写、目录权限、context schema |
| `ensure-mobile-plugins` | 资产清单、JSON 协调算法、幂等和保留规则 |
| `spawn-dsh-web` | PRoot 参数、Android linker、环境变量、Node 入口 |
| `wait-web-ready` 且进程仍在 | DSH Web 初始化、端口/健康检查、profile |
| 进程提前退出 | 退出码及 runtime log 最后有效行 |
| 仅真机失败 | Android/厂商限制，而非已通过的业务层 |

只有证据指向 PRoot 不兼容时才评估更换引擎；只有完整 Android 模拟能增加覆盖时才申请 KVM/Binder/Docker 能力。

## 8. 当前环境与诚实边界

MCP 宿主为 ARM64 Linux，具备 JDK 17、Android SDK、`aapt2` 和 `adb`。本次检查时无在线 ADB 设备；沙箱未暴露 `/dev/kvm` 或 Binder 设备；Docker CLI 存在但 daemon/socket 不可用。

因此本次能完成“Android framework JVM 模拟 + ARM64 Runtime E2E”，不能描述为完整 Android 11 虚拟机测试。最终结果会分别列出模拟通过项和仍需真机确认项。

## 9. 打包放行标准

只有同时满足以下条件才创建新 release APK：

- 启动路径不再调用在线或可变的插件安装；
- 首页显示当前阶段，并在后台失败后迅速退出加载态；
- 旧遥测不污染新启动；
- 复用旧 slot 的升级模拟通过；
- Robolectric、合约、ARM64 E2E、lint、编译全部通过；
- APK 签名校验通过并记录 SHA-256；
- 文档明确记录尚未由真机覆盖的风险，不把“未测”写成“通过”。

真机回归重点：保留 alpha.11 数据覆盖安装、复用 slot A、不重装 rootfs、观察六个阶段、进入官方 DSH Web UI、验证后台服务，以及确认诊断信息已脱敏。

## 10. 后续增强

宿主具备条件后，可优先加入 Cuttlefish ARM64 完整系统测试；若采用 redroid，则固定 Android 11 镜像 digest 并记录 kernel module。虚拟设备测试是现有两层测试的补充，不能替代 OriginOS 真机验收。

---

本文给出可证伪的边界：每类测试只声明实际覆盖的部分；任何新失败都先回到对应边界重新判断技术路线，再决定修代码、改 Runtime，还是补运行环境。

## 11. 实施结果（2026-09-12）

本轮已按上述路线完成实现，并在 Orange Pi ARM64 环境完成发布前两层模拟验证：

- 启动时不再通过 `dsh plugin add` / pnpm 修改已有 Web profile；`MobilePluginProfileCoordinator` 在 Android 宿主侧完成 APK 内置插件的离线、幂等协调，保留用户自定义 dependency、bundle 和其他 JSON 字段；受管插件目录采用 staging -> backup -> activate -> rollback 的切换方式，marker 仅在最终合同校验成功后提交。
- Runtime 启动现在通过 `RuntimeStartProgress` 上报 `verify-start-prerequisites`、`write-mobile-context`、`ensure-mobile-plugins`、`spawn-dsh-web`、`wait-web-ready`、`web-ready` 六阶段；Foreground Service 将阶段持久化到 telemetry。
- `ACTION_START` 每次先读取磁盘 inventory 重新建立本次启动状态，旧 install/start telemetry 不再决定 Runtime 是否存在；服务最外层异常也会写入本次失败状态。
- Chat 首页不再只盲轮询 127.0.0.1 约三分钟；它只消费本次 dispatch 之后的新 telemetry，后台失败会在下一次短轮询内结束 loading 并显示实际阶段消息。
- Robolectric 在 Orange Pi/Linux ARM64 上最初因旧 Conscrypt 缺少 `linux-aarch_64` JNI 失败；测试依赖固定到 `conscrypt-openjdk-uber 2.6.2` 后恢复真实 JVM framework 测试，而不是跳过该门槛。

最终模拟结果：

- `mobile_context_contract`: PASS。
- `android_unit_test`（Robolectric/API 30 + app/service telemetry）：PASS，包含旧 profile 保留用户字段、离线幂等升级、失败不提交 marker、启动遥测重置/阶段推进/失败终止与 Foreground Service ACTION_START 分发。
- `runtime_alpine_e2e`: PASS（DSH `0.1.2-rc.1`），包含 rootfs guest absolute symlink、embedded DSH/pnpm、真正无 registry 的 embedded Web profile、token-authenticated DSH Web、online fallback、bundled/source-built node-pty、真实 PTY，以及“历史 Web profile -> 宿主侧离线加入 mobile UI -> pnpm lockfile SHA 不变 -> DSH Web 正常启动”的升级闭环。
- `android_lint`: PASS（`app:lintDebug`，287 actionable tasks，0 blocking error）。
- 项目路径污染检查：未发现已知污染。
- ADB HostCapability 本身在 ARM64 上可用，但本轮查询为 0 台在线设备；因此 Android 11 / OriginOS 覆盖安装仍明确属于真机验收项，未将其写成已通过。

发布候选版本已提升为 `0.3.0-alpha.12` / `versionCode 13`。只有 lint、最终 APK 构建和稳定签名验证继续通过后才写入 `release/` 并更新公开 update manifest。
