# DSH Mobile｜V1.3 当前唯一实施清单（用户收敛版）

> 日期：2026-09-18；依据：用户最新范围确认 + `2026-09-18-mobile-ui-workspace-drawer-attachment-technical-implementation-spec.md` V1.3 + 官方 DeepSeek APK 静态研究。状态：**研究完成一部分，以下产品功能尚未据本轮源码/设备证据验收，不宣称已发布**。此前 V1.2 的 52 项完整旧清单保存在 Git 提交 `a5c7b82`，此文件替代其日常实施任务，不继续按旧列表扩展工作。

## 0. 用户最新范围、状态和版本（高于此前旧清单）

用户明确：**不需要独立 SSH 救援通道**，不再列为门禁、待办或要求用户开启；不得将本指示理解为授权关闭/卸载现有服务。其余 UI/交互（工作区、设置/备份、既有 Header/汉堡布局、IME 与系统栏等）由用户确认已实现：标 `OWNER_CONFIRMED / OUT_OF_SCOPE`，不追溯成全套源代码或真机验收 PASS；若出现明确回归，再以单独缺陷处理。保留基本数据安全/权限/证书核验，但不另立这些 UI 功能的开发任务。

**本轮只交付六个体验结果，分为五个交付域**：附件域三项「图片实际上传」「图片预览（近期+已选）」「composer 内联拍照/相册/文件三入口」；交互域「左抽屉左滑关、右滑开」；运动域「切页过渡」；性能域「启动加速」。附件以用户最后一张 DeepSeek App 参考图的信息结构为目标，**禁止复刻专有原厂代码/图标或回到 Android 原生来源 ModalBottomSheet**。

App 版本在 `app/build.gradle.kts` 调为 `0.5.0-preview.1-dev` / `versionCode=27`，原版本 `0.4.0-preview.6-dev` / 26。新版本系列**不等于六项目标完成、稳定 1.0 或新 APK 已发布**；`release/update.json` 与旧 APK/SHA 不改，managed plugin/Runtime 版本独立，只有真实包资源变化且验证通过才升 bundle marker。

状态定义：`RESEARCHED` 有源证据只证明调查；`OWNER_CONFIRMED` 是用户已完成反馈，不当作独立 PASS；`PASS` 必须有当前源码 commit+对应测试/样本；`NOT_RUN/BLOCKED/FAIL` 按事实写。`[x]` 有 5 项已完成研究、1 项仅完成版本构建验证；待实现或发布验收仍为 `[ ]`。

## 当前实施与验收实录（2026-09-18；以这段结果覆盖旧的“全部未实现”快照）

当前阶段：**IN_PROGRESS / NOT RELEASED**。已修改源码但尚未发布 APK；旧 Preview.6 / `release/update.json` 未动。已经通过独立 MCP TaskProfile Android API30 单元测试 `task-android_unit_test-20df6b6ed8b743088e42`（app 34、core/recovery 12、core/runtime-android 16，共 62/62，0 fail）；Lint `task-android_lint-504e935bcc464f12a142`（0 errors、23 warnings）；最终附件 SVG 资产 Runtime Alpine E2E `task-runtime_alpine_e2e-a1829744115846db97df`（succeeded，exit 0，PASS DSH 0.1.5-rc.2）；`mobile_context_contract` job `task-mobile_context_contract-29cc317795b541b3ac13` 也 succeeded。Node24 `scripts/test-mobile-attachment-sources.mjs` 与 `scripts/test-mobile-drawer-gesture.mjs` PASS；两者是合成 DOM/事件契约而非 OriginOS 帧证据。

| 项目 | 已取得的本轮证据 | 尚不可标 PASS 的边界 |
|---|---|---|
| ATT01 | 真正封装的 DSH 0.1.5-rc.2 的 `InputBar` 有 HTML `input[type=file][multiple]`，入口 `onPickFiles → intakeFiles`，实际浏览器 fixture 确认，无旧 0.1.2 npm 缓存依赖。 | 手机包上的运行时内容与 Android provider 仍须真机验证。 |
| ATT02 | 三入口复用官方真实 input，同步 `click()`，`change/cancel` 后恢复 accept/capture/multiple，标准 DSH count/size/Session 校验保留；官方单占附件槽没被双注册。 | 公开的 `ComposerAttachmentsOwnerProps.onAddFiles` 在官方单占 slot 内，外部插件没有独立 intake 服务；近期图的直接入列仍 BLOCKED，不能用模拟 paste/合成 change 硬绕。 |
| ATT03 | 390px exact-current-profile Chromium：固定 PNG 1/2/3/5 逐步进入官方草稿；五张消息发送后出现在 Session，reload 后五张图全部重载。测试 session 在 isolated `.mcp/tmp/runtime-alpine-e2e-clean`，截图位于 `.mcp/browser/sessions/browser-af38419355c14053/`，无私人数据。 | 仅五张通过 Web→Host 消息回显；模型推理因 `MISSING_CREDENTIAL` 没有成功回复；Android 原生 `content://` grant、逐组 Host 持久化和真实设备未执行。 |
| ATT04/07 | Native 移除 ModalBottomSheet 并按 accept/capture 分流、只回 content://；新 Cordis list slots 三入口、禁用恢复原生输入、同屏不出现双回形针，390px Chromium 截图已录。 | 端到端 Activity 重建/Provider 超时/最近图库还未通过。 |
| ATT08 | 官方已选图横向预览、单击全屏灯箱、单项删除、五张横滑与刷新会话图片通过 Chromium。 | 最近图片预选行、MediaStore 授权/拒绝/撤销、320/360/390 三宽三主题无证据。 |
| DR01/02 | pointer 手势实现 + Node24 open/close/vertical/system-edge/rail/modal/cancel/unload 合成事件测试 PASS。 | Chrome 手势连续 20 次 / OEM 回退、帧表现/屏幕录制仍未验收。 |
| MOT01/PERF | Native 页面进出过渡改为保留 WebView 的透明 Home 目标；删固定 520ms splash，Supervisor 与首帧并行；已有单元编译 PASS。 | 冷/温/热三轮、reduced-motion、Android 实际帧序/设备启动时间缺证据，不能宣称缩短了多少秒。 |

**剩余关键阻断**：最近图库/安全媒体桥接/正式 onAddFiles 接口；原生 URI 实际上传与 OEM 真机；定量启动/动画。不得将这段 Browser PASS 等同最终六项 PASS，未获用户许可不读取其真实图片或密钥。

当前开发 APK build `task-android_debug-2e0e92c6c33f4471b57e` / signing `task-android_signing_verify-622c961c5e2f4f9f9c67` 均 PASS，包内确认新版两插件，仍**不是**六项验收全部通过和公开 release；详见 `2026-09-18-v050-attachment-integration-qa.md`。

## A. 已完成的研究（非代码交付）

- [x] A01 对照用户参考截图及之前记录，定位 Preview.6 `ChatScreen.kt` 原生 `ModalBottomSheet` 与要求不符。
- [x] A02 阅读橙派旧 Cordis 移动插件、官方 DSH slot/单占附件 rail 和 Lexical 输入边界；旧 synthetic paste 不可复用。
- [x] A03 校验官方 DeepSeek 2.5.2 APK 来源、包签名与静态组件，记录 JADX 304 项解码错误和结论边界。
- [x] A04 确认「获授权最近图片 MediaStore」和「官方 DSH 已选草稿」是两个状态；独立 Photo Picker/SAF 多选。
- [x] A05 输出 V1.3 技术规范、实施顺序、风险/退路和本清单；无新附件实现或真机验收。

## B. ATT：先证明真实入列和服务端发送，再移除旧弹窗

- [ ] ATT01 [BLOCKER] 核对实际冻结 DSH Web bundle/lock/managed plugin versions/slot 类型与 `onShowFileChooser` 来源；不能以本地 npm 缓存当手机实际版本。证据：pin/实际 source SHA + DOM/slot 负例。
- [ ] ATT02 [BLOCKER] 找到公开且受限的 `File[]→模型/MIME/单项/总大小/数量校验→官方 draftId` 正式 intake；没有则设计版本化最小 owner 扩展，不能拼未公开 `createDraftImages+addImages` 或 synthetic paste。证据：类型/ABI/安全负例。
- [ ] ATT03 [BLOCKER] 在隔离 DSH Host 使用固定图片 1/2/3/5 完整跑通选择→URI grant→owner admission→已选草稿→发送→Host/Session 持久引用→reload，并核对失败不丢文本/已有效草稿。无 Host 则 `BLOCKED`，不能以预览代替。
- [ ] ATT04 编写 Native chooser request 的单次 callback/lifecycle 契约：mode/accept/multiple/camera、重入拒新、Activity 重建、导航/renderer death/取消；结果 `data+ClipData` 保序去重、拒异常 URI/越权限额，失败不误发另一会话。
- [ ] ATT05 受控 `RecentMediaRepository`：用户授权后 Android 11 `MediaStore` 稳排序分页、低清有界缩略、权限撤销/切 Session 清句柄；拒绝权限仍可点系统相册，不暗中读取图库。
- [ ] ATT06 设计并通过 `MediaBridgeV1` main-frame/固定 origin/doc+session+permission generation/请求幂等与字节面实验证据；禁止 Web 通用任意 URI 读取、无限制 Base64 和沿用几何遥测协议。
- [ ] ATT07 独立可禁用 Cordis Browser 插件：使用真实 list slot，正常文档流布局「近期横向预选 + 官方已选 rail + 拍照/相册/文件等权三入口」，不双注册 `conversation.input.attachments`；Native 只承接系统选择器，桥验证后删除 `ModalBottomSheet` 来源界面。
- [ ] ATT08 图片预览：近照/已选区分状态；横滑、单击全图、逐项移除、失败重试和权限拒绝的空态，320/360/390dp/3主题/大字/读屏可用，横滑不抢抽屉。
- [ ] ATT09 文件入口准确揭示冻结模型支持 MIME；若 PDF/TXT 不能进入官方 Host，不标为支持上传。相机输出 FileProvider 产物、PhotoPicker/SAF fallback 超额和无授权均测试。
- [ ] ATT10 插件启停、旧 Preview.6 数据、会话切换、取消/Provider超时、重复发送和失败回退验收；禁用插件后官方输入不损坏。

## C. DR：只完成左侧抽屉手势；不重做用户已确认的其余侧栏布局

- [ ] DR01 使用官方 layout/sidebar 状态作为唯一开关；`pointerdown/move/up/cancel` 锁轴、拖动距离/速度、单次提交，左滑关右滑开；保留汉堡点击。
- [ ] DR02 排除最近图片横滑、Web 垂直滚动、系统返回边缘、多指/旋转与 Settings modal；遮罩可点、官方右侧栏及 Header 现有行为不回退。
- [ ] DR03 OriginOS/WebView 或受控等价环境连续 20 次打开/关闭并录帧；任何 UI 空白/误触/残影回归应修在本切片，不重新开展其他 UI 改造。

## D. MOTION/PERF：只做切页与启动，不把 IME 等已确认项重新列待办

- [ ] MOT01 构建 Native tabs 与 Web 页内切换的过渡所有权/单状态源；保持长驻 WebView、Session/草稿/滚动锚点，避免重复动画和假页面遮罩。
- [ ] MOT02 量化页面切换的响应时间、帧时间及交互反馈；短切换不触发无意义 loading/restart，支持 reduced-motion、系统返回正确。
- [ ] PERF01 同条件冷/温/热至少各 3 次记录 Activity→Runtime→token→WebView/root→可交互 composer 的 TTID/TTFD 与 p50，定位瓶颈后做针对性优化；26 秒是用户基线不是虚构的统一指标。
- [ ] PERF02 改动后同设备同样本对照启动和内存/缓存效果，不能关闭鉴权或削弱完整性检查来换速度；回归登录/附件/Session 完整性。

## E. 版本、质量与发布（不强制 Termux SSH 救援）

- [x] REL01 [BUILD_VERIFIED，非功能验收] 当前源码与本地 APK metadata 均为 `versionName=0.5.0-preview.1-dev`、`versionCode=27>26`、包名不变；`android_debug` job `task-android_debug-adfaf64433d74eb58988` 成功（172 tasks、exit 0），`android_signing_verify` job `task-android_signing_verify-c6edef7411ee40d88c57` 验证旧稳定证书一致；本地 `app/build/outputs/apk/debug/app-debug.apk` 未发布、未安装，DSH runtime pin/插件 marker/旧 APK/OTA 未改。
- [ ] REL02 各功能 commit 分别进行 ABI/Node24、Android API30 单测、`android_lint`、`runtime_alpine_e2e`、exact-current-profile Chromium A/B、`git diff --check`/review；不可借上一轮 59 项或旧 APK 测试顶替。
- [ ] REL03 提供真实已授权设备时按功能验证 1/2/3/5 图片 Host 回执、横滑与抽屉、过渡及冷/温/热性能；如果无设备，照实标记 `DEVICE_NOT_RUN`，小主机通过不能冒充真机通过。
- [ ] REL04 APK 手动覆盖安装前核对历史 APK/证书、版本递增与已有数据/可靠非破坏备份或用户自主保留；**SSH 救援非条件**，不强制 ADB、绝不主动清数据/卸载或停旧 SSH 服务。
- [ ] REL05 六项目标完成后冻结源码并 clean assemble/signing verify，手动 APK HTTPS 实际重新下载核验大小/SHA 后再给链接；未经另行确认不更改 `release/update.json`，不宣称稳定发布。

## 已由用户确认并退出本轮的范围

| 范围 | 当前产品状态 | 本轮处理 |
|---|---|---|
| 工作区、项目/文件管理与预览、设置及备份、主题、已有导航/Header/遮罩、IME/系统栏等其余 UI | `OWNER_CONFIRMED`，非本轮独立测试结论；此前文档中的旧缺口历史保留 | 不再追开新的工作项、不做无授权修改；若有具体回归再单独开项 |
| Termux SSH 救援 | `NOT_REQUIRED_BY_OWNER` | 不恢复、不测试、不作发布阻断；亦不擅自关闭旧服务 |

**发布判断**：仅对当前六个体验结果逐项给 `PASS|FAIL|BLOCKED|NOT_RUN`；签名/版本码/旧数据仍必须独立核对。`REL` 项是安全/可交付性检查，不意味着继续重构其他已确认页面。历史 V1.2 52 项及长篇 workspace/restore 技术方案只作为研究档案，由 Git `a5c7b82` 可恢复。
