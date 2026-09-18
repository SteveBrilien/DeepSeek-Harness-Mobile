# DSH Mobile｜V1.2 可执行实施清单（按依赖排序）

> 日期：2026-09-18；依据：`2026-09-18-owner-ui-and-attachment-plugin-reconciliation.md`、`2026-09-18-mobile-ui-workspace-drawer-attachment-technical-implementation-spec.md` V1.2 与 `../research/2026-09-18-deepseek-official-apk-attachment-static-analysis.md`。当前为 **DOC_ONLY / NO_NEW_APK**。`[x]` 只表示已完成**此项调查/文档**，不是功能开发完成。`[ ]` 未完成；`BLOCKED` 只能记录阻断条件，不能偷换为 PASS。各项必须在对应 sourceCommit 上复验。

状态字典：`PASS` 有确切 commit+test/evidence；`FAIL` 有可复现错误；`BLOCKED` 缺安全权限/ABI/设备；`NOT_RUN` 未测。UI 的静态 screenshot fixture 可以 PASS，但不能当设备/Host 发送 PASS。若实测结果不支持本方案的 `P` 接口，先更新 ADR/此清单，禁止绕过。

## A. 已完成的研究与设计（不是实现）

- [x] A01 复核旧会话要求、Preview.6 四张截图、工作区/侧栏/附件/备份分歧；证据：`owner-ui-and-attachment-plugin-reconciliation.md`；结果：产品设计差距已定位。
- [x] A02 读当前 `ChatScreen.kt` ModalBottomSheet、`FilesScreen.kt` 单选和 `RecoveryScreen.kt` 操作，以及冻结 DSH `slots.d.ts`、旧移动 UI/WorkBench Cordis 插件；结果：HTML/Lexical 与旧 textarea/synthetic paste 不兼容。
- [x] A03 官方 DeepSeek 2.5.2 APK 可信来源/ZIP/签名/版本核验，有限静态反编译；证据：`../research/2026-09-18-deepseek-official-apk-attachment-static-analysis.md`；解码错误 304 项已披露。
- [x] A04 确认参考图的近期图库、原生三入口、Photo Picker/SAF 及文件上传状态是独立概念；**未证明** DSH 官方 intake 已公开。
- [x] A05 形成 V1.2 架构/协议候选/安全停止规则/验收矩阵与本清单；结果：DOC_ONLY，无功能/真机或构建验收。

## B. 开工门禁 G0/P0：先查真实版本与实际数据路径（阻断 ATT 的后续开发）

- [ ] B01 [G0/安全] 留存已安装 APK `versionCode/versionName/signing SHA`、现有 Preview.6 发布 APK SHA、源码 commit 和 git 状态，不使用旧构建哈希冒充新版本；证据：脱敏 manifest。
- [ ] B02 [G0/安全] 实时检查手机独立 Termux SSH `22023` 连通、断开再重连、`22022` 旧隧道维持与救援身份；未通过则只做橙派测试，禁止覆盖安装。
- [ ] B03 [G0/安全] 在用户允许的时机备份现有 App/共享 Vault/测试样本并执行只读恢复验证；保留唯一副本，不清数据/卸载；记录可恢复范围，未验证密钥不可称“已备份”。
- [ ] B04 [P0/ABI] 读取真实设备/隔离 Android11 已安装的 DSH web bundle、seed hash、lock、active profile、Slot Registry generation、四个 managed bundles 的真实版本与 SHA；和本地 npm 0.1.2-rc.1 类型逐项比对。
- [ ] B05 [P0/ABI] official+compat-only fixture：确定谁触发 `onShowFileChooser`，每处 `input[type=file]` 的 `accept/multiple/capture`、Lexical 与内置附件配置；用固定测试样本记录回调和当前 native 三入口关系，不写入真实用户消息。
- [ ] B06 [P0/ABI, BLOCKER ATT] 查证可跨插件稳定调用的 `File[] → owner admission → official draft ID` 接口；如不存在，写独立最小 upstream typed API ADR/扩展提案和 ABI 兼容/回退；**不能直接调 concrete `createDraftImages` 串 `addImages`**。
- [ ] B07 [P0/端到端, BLOCKER ATT] 不装新插件，通过官方输入链实测图片 1/2/3/5 的 MIME/数量/字节/模型约束、Host admission、Session durable echo 与 reload，逐个给出测试 ID 和失败路径；没有真实 Host 时记 BLOCKED。
- [ ] B08 [P0/媒体桥, BLOCKER 近照] 验证 Android11 targetSdk28 Manifest 授权，以及新的 MediaBridgeV1 main-frame/origin/capability、缩略字节通道的 CSP/CORS/超时/内存/撤权方案；只用安全样本。失败时近期栏不假渲染。
- [ ] B09 [P0/插件] 以 freeze package 的实际槽位做 `input.left/input.dock` 注册/卸载/重入/换会话以及 `input.attachments` single 冲突负例；记录官方插件禁用/恢复路径。
- [ ] B10 [P0/冻结] D0 ADR 选择/淘汰矩阵、目标界面与 ABI diff 获审，固定 baseline 截图（360dp）、rollback commit；此项未通过不得进入 ATT 真正发送代码。

## C. 附件 D1/D2：先让真实发送通，再实现目标 UI

- [ ] C01 [D1/接口] 正式输入 owner 公开 `requestFiles/admitFiles` 受控入口，重用现有 MIME/模型/数量/单项/总字节校验且返回官方 `draftId`；更新 type tests 和 resource pin。若 B06 已有接口，本项只编写 adapter，不 fork 官方实现。
- [ ] C02 [D1/Native] 设计并落实一次仅一条在途的 `ChooserRequest`（callbackOnce、document/session generation、accept/mode/capture/source）；同时验证重入拒新、取消、Activity 重建、WebView render death、导航和 Native file-provider 生命周期。
- [ ] C03 [D1/URI] `data + ClipData` 保序去重、content scheme/授权/第三方 provider/MIME+有限签名/未知大小/流式限额；单选不接受多项，SAF 回退超额再次校验；每项可定位 error。
- [ ] C04 [D1/摄像头] FileProvider 仅本次 camera output；`ACTION_IMAGE_CAPTURE` flags、返回 Intent null 实际文件检查、失败/取消清理；不默认要求整库权限。
- [ ] C05 [D1/发送] 1/2/3/5 个测试图片经 UI→owner→Host→Session reload 全通过，重复发送/超时 outcome unknown 按 ID 去重，文本与已就绪草稿不因单项错误丢失。
- [ ] C06 [D1/非图片] 列出当前模型/DSH 真正支持的 MIME；PDF/TXT 如缺少 durable attachment/Host consume/历史展示则单独里程碑，文件按钮说明“目前仅支持图片”，不把选中当发送。
- [ ] C07 [D2/插件壳] 独立可关闭 Browser Cordis package 的 `package.json`/host/client/patch/profile seed+existing/manifest hash，注入/卸载 effect 正确；Safe Mode 退回官方 UI。
- [ ] C08 [D2/展现] composer 内联面板：近期横向条 + 官方已选 rail（不可双注册）+ 拍照/相册/文件三个等权入口；不遮历史、不保留 Compose ModalBottomSheet；Android 系统 picker 仍可弹系统界面。
- [ ] C09 [D2/近期] `RecentMediaRepository` 以授权可见 MediaStore 20 项分页、稳排序/懒缩略/有界缓存和独立临时 ID；无 permission 不查询、不保留失效句柄，拒绝/撤销后显示简洁降级提示。
- [ ] C10 [D2/桥] MediaBridgeV1 严格 origin + main-frame + doc/session/permisson generation + one-shot ID；不暴露原始 URI/任意读文件，缩略交付与 full-image admission 分离；不重用纯遥测 Bridge parser。
- [ ] C11 [D2/选择] 最近图片点击、系统多选都进入同一个官方 owner admission；官方 draftId/缩略/移除/灯箱/发送状态同源，不让前端另造附件 registry。
- [ ] C12 [D2/边界] 浏览器 user activation/async pre-arm 与 Android `onShowFileChooser` 真实因果关系验收；失败回默认安全系统选择器，不用 synthetic paste 或自动模仿真实点击。
- [ ] C13 [D2/无障碍] 320/360/390dp + 字体缩放+深浅/Tokyo、横滑不触发抽屉、TalkBack 朗读/焦点恢复/屏幕旋转/权限解释文字、没有大面积遮盖与空白。

## D. 侧边栏、Header、IME 与性能 D3/D4

- [ ] D01 [D3] 读取当前 layout truth/slot；在有 Header 的会话把汉堡集成标题行，无 Header 的 Hero 先证明正式 outlet，禁止 fixed top-left 遮盖。
- [ ] D02 [D3] 80vw 上限/右侧真实遮罩可点、右栏按钮/Settings modal/z-index/focus trap/长标题可用；Settings 固定弹窗宽度不受 transform 祖先影响。
- [ ] D03 [D3] 单权威 drawer 状态，跟手拖拽锁轴、系统手势避让、图库横滑排除、pointercancel/多指/旋转取消、一次提交、reduced-motion 与按钮完全替代。
- [ ] D04 [D3] Android Back：文件选择器→正式 modal/灯箱→附件面板→右栏→左栏→Web history→Native，一次事件一次处理；不要 Escape+goBack 双触发。
- [ ] D05 [D4] 同时记录 Native IME/navigation insets、WebView/root/visualViewport/composer 与滚动锚点，重现收键盘弹跳后确定单一布局 owner，再决定哪处修复。
- [ ] D06 [D4] 26 秒用户观测转冷/温/热至少 3 次分段 TTID/TTFD 指标，记录 runtime/web/token/first interactive；不以打断安全启动或关闭缓存校验来缩短时间。
- [ ] D07 [D4] 真实 WebView 151 抽屉 20 次/IME 各 10 次，统计漏击、空白、残影、掉帧和输入焦点；系统栏临时露出按系统规范解释，不承诺永远不可见。

## E. 工作区 D5 与设置备份 D6（与附件/抽屉解耦的交付域）

- [ ] E01 [D5] 保留已入库的紧凑首屏改动，360dp 真正截图对照；统一目录树/项目快捷入口/面包屑、点击与长按，不加第二个常驻项目页。
- [ ] E02 [D5] 将 `selectedPath` 替换 `Set<EntryKey>`，只在选择模式显示上下文工具栏；操作需要按 grant/capability 动态启停，选中跨项目切换时作废。
- [ ] E03 [D5] 多授权根 `AuthorizedLocation` 独立 ADR；canonical/symlink 检查和 SAF document flags，Android11 restricted roots；不给 PRoot 传 `content://` 伪 cwd。
- [ ] E04 [D5] 批量复制/剪切/粘贴/重命名/删除制定 per-item journal + staging + verify + conflict policy，跨卷部分成功时保留原件与逐项反馈；故障注入 ENOSPC/撤权/断电。
- [ ] E05 [D5] Trash schema 保存原始路径与 grant/版本、旧数据不猜路径、同名/权限撤销明确处理；文本编辑 SHA 并发保护、预览类型与大小限制、终端 cwd 双向映射。
- [ ] E06 [D6] Settings 一级四组/App/Data/Runtime/Developer，合并重复入口、层级一致，副标题/日志/路径按需显示；主题三套 token 而非 hardcode。
- [ ] E07 [D6] Backup catalog 把 Vault found/manifest found/archive verified 分开；可创建/导出/校验，但目前不能激活“完整恢复”按钮。
- [ ] E08 [D6] 可选备份+只读恢复预检、按资产分类授权/冲突/空间/密钥可用性；再做 checkpoint→staging→verify→分域 commit/journal/rollback/重启恢复，并独立测试凭据的安全恢复。

## F. 质量、设备与发布门禁 G1–G5

- [ ] F01 [G1] ABI/slot/intake/Host 测试 PASS，pin/generation 一致；仅 Chrome 模拟不算真实 DSH 发送。
- [ ] F02 [G2] Native API30 chooser/MediaBridge 单元与 Robolectric，URI 及权限攻击负例、callback 恰一次、retained draft/Session 隔离通过。
- [ ] F03 [G3] 当前源码 `android_unit_test`、`android_lint`、`runtime_alpine_e2e`、Node24 syntax+slot-policy 全 PASS，按任务 ID 和 commit 记录；Lint 23 个已有警告分型、不出现新错误。
- [ ] F04 [G3] 创建并验证当前 freeze-profile 的 Chromium composition gate：official+compat-only / full managed plugin A-B，检查 drawer/附件/右栏与卸载、`git diff --check`、code review。
- [ ] F05 [G4] 橙派模拟/Espresso+OriginOS151 分开记录；浅/深/Tokyo、320/360/390、fontScale、右栏 Settings、IME、Drawer、媒体预选/多选发送、Provider 超时/断网。
- [ ] F06 [G4] APK 更新前再次完成独立 Termux 救援隧道断线重连、备份可读、同签名检查、旧数据清单；不因为已经在历史对话 PASS 就跳过。
- [ ] F07 [G5] `:app:clean :app:assembleDebug` 冻结重构哈希、签名 SHA 和 `versionCode` 增长、Runtime/插件 seed+profile 完整、旧版本升级与回退只测试非破坏方案。
- [ ] F08 [G5] 手动 APK 用 HTTPS 上传并重新下载 SHA/size/HTTP 校验后才给下载链接；**不擅改 OTA manifest，不强制更新/安装、不清用户数据**。
- [ ] F09 [发布验收] 所有阻断项关闭或明确缺省降级，逐项记录 PASS/FAIL/BLOCKED/NOT_RUN，提交 release note/UI 对比与已知限制；用户确认安装后再执行真机变更。

## 必须首先解除的阻断项

| 阻断编号 | 具体条件 | 解锁后可进入 | 失败时明确退路 |
|---|---|---|---|
| B-ATT-ABI | B04/B05/B06 找到真实冻结版本标准 intake 或正式扩展方案 | C01–C06 | 只保留官方文件选择器 |
| B-ATT-BYTE | B07/B08 原生安全字节输送、草稿与 Host 回执闭环 | C08–C12 | 近期预览可降级；不得宣称可上传 |
| B-REC-DATA | E07/E08 备份内容/秘钥/rollback 真实验收 | 完整 Restore UI | 创建/导出/校验真实入口，恢复不显示可用 |
| B-DEVICE | B02/B03/F06 救援 SSH/备份/授权已实测 | 覆盖安装和最终真机验收 | 仅橙派构建/模拟，不发“全部完成”APK |

## 每项验收记录模板（待实施时复制，绝不预填 PASS）

```text
caseId: C05-2-images
status: NOT_RUN
commit: <实际源码 SHA>
runtimePin: <SHA>
pluginSha: <SHA>
device: <型号/OS/WebView 或橙派 profile>
inputFixture: <非私人测试样本 ID>
actions: <可复现步骤>
expected: <数量/状态/边界>
observed: <脱敏实际结果>
evidence: <安全位置/作业 ID>
rollback: <已验证对应版本>
```

**完成定义**：研究完成≠功能完成；URI 返回≠文件入列；文件入列≠Host 已收到；Host 已接受≠模型响应完成；模拟 PASS≠OriginOS PASS；APK 编译≠可安全覆盖安装。每个 release 级承诺都要有与其同一源码提交、同一运行环境对应的证据。
