# DeepSeek Harness Mobile v1.0 交接报告

最后更新：2026-08-29（进行中快照）  
唯一主线目录：`B:\Codex\DeepSeek Harness Mobile\dsh-mobile-app-v1.0`

> 本文件优先记录事实、已验证结果和未完成项。`v1.1` 只能作为缺陷样本与会话记录参考，禁止将它整体合并回主线。

> 额度中断前的用户原始要求已从 Codex 主任务 JSONL 恢复并独立归档到 `docs/RECOVERED_USER_REQUIREMENTS.md`；当时的 13 张参考图已复制到 `artifacts/screenshots/user-references-2026-08-28/`。本文件只负责实现状态，不能替代原始需求记录。

> 用户已确认恢复文档中的 R-01～R-12 全部属于本轮统一验收范围。后续不得把其中任何一项自行降级成“以后再做”的可选建议；确有协议、凭据或设备阻塞时必须保留未完成状态并写清证据。

## 1. 产品目标与已经确定的技术路线

本项目不是把完整 DSH 服务塞进 Android 本地运行，而是：

1. DSH/Cordis、模型密钥、会话、工作区和 Agent 状态继续部署在服务器；
2. Android App 是面向移动交互重写的可信客户端，通过配对后的移动网关连接服务器；
3. 服务器是会话与上下文的唯一权威来源；手机 Room 数据库只保存加密/可失效的阅读缓存与连接信息；
4. App 重开时可以先显示本地快照，再与服务器增量同步，因此不应让用户感到每次都从空白重新加载；
5. UI 应尽量复用或忠实迁移 DSH Web 的信息架构、图标、术语、交互反馈和事件语义，不另造花哨的“AI App”视觉体系。

该路线保留了跨设备共享同一上下文、服务端集中配置/密钥管理和 DSH 原生能力，又避免直接缩放 Web 页面在手机上造成的抽屉、设置弹窗、键盘及触控问题。

## 2. 当前代码状态

当前源码版本已统一为 `1.0.0-alpha.2`。主要目录：

- `packages/mobile-gateway`：DSH Host 插件、配对和 MobileApiProxy 白名单网关；
- `packages/mobile-client`：React 移动端 UI；
- `android`：原生 WebView 容器、Room 缓存、Keystore、文件选择与相机桥接；
- `scripts/fixture-gateway.mjs`：无需真实服务器即可进行浏览器契约测试的夹具；
- `docs`：架构、安全、缓存、协议、验收与本交接文档；
- `artifacts`：网关包、APK、报告和截图的唯一输出位置。

### 已实现并做过验证的部分

- 配对页保持极简；手机不输入或保存模型 API Key；
- 配对令牌、Host Origin 校验、RPC 方法白名单、请求体大小和上传大小限制；
- 服务器会话/工作区读取、新会话、会话切换、流式事件订阅、历史补拉与重连；
- 本地 Room 快照缓存，服务器数据仍为权威；
- 会话模型目录来自真实 `session.models`，切换调用 `session.selectModel` 后再次读取服务器确认，已经修复“界面切换但始终实际使用同一模型”的问题；
- DSH 风格输入框：工作区、Agent 预设/模式、命令提示、权限、模型与推理等级、上下文指示、发送/运行中按钮；
- 图片、相机、相册、文本/代码文件入口与预览；图片按 DSH `PromptContentPart` 发送，服务端 DeepSeek adapter 负责 Files API；文本/代码文件在本地限量转为文本片段；
- Markdown、JavaScript 代码块、流式文字、工具调用卡、轨迹原始事件入口和 `Deep diving…` 状态样式；
- 浅色、Tokyo Night、深色三主题；
- 抽屉、会话标题投影同步、服务器归档会话过滤；
- 设置页已加入模型、插件公开能力、Agent 预设、API 用量、插件市场、归档对话等 section；
- 插件页只展示 rc.2 可真实读取的 `skill.list`，没有伪造 Cordis 插件清单；
- Agent 预设来自真实 `agentPreset.list`，只允许空白会话执行 `agentPreset.select`；
- 归档页来自真实 `workspace.list.archivedSessionIds` 与 `session.list`；由于 rc.2 没有远程取消归档 RPC，界面没有伪造该按钮；
- Android 原生文件选择、相机 FileProvider 和系统栏主题适配。

### 已执行过的验证

- 网关单元测试：3 项通过；
- 客户端单元测试：9 项通过；
- TypeScript/JavaScript 检查与 Web 构建通过（后续改动仍须重新执行）；
- 360、393、412 px 竖屏浏览器检查通过，设置、模型、插件、预设、归档 sheet 已人工点检；
- fixture 中实际验证模型从一个模型切到另一个模型，并由服务端回读确认；
- 检查时没有浏览器 console error。

注意：以上是本次快照之前的验证记录，不代表后续未提交功能自动通过。

## 3. DeepSeek 文件上传结论

DeepSeek 官方 Files API 的 `POST /files` 当前只接受 JPEG、PNG、GIF、WebP 图像，不是任意文件云盘 API。DSH `0.1.1-rc.2` 的 `dsh-llm-deepseek` 已经实现图像上传、索引、复用和失败回退，所以正确链路是：

`Android 选择图片 -> MobileApiProxy prompt content -> DSH attachment subsystem -> DeepSeek adapter -> DeepSeek Files API`

模型密钥始终留在服务器。普通文本/代码文件可以限量转成带文件名边界的文本内容；PDF、Office、压缩包等二进制文件在没有服务端解析器前必须明确拒绝，不能假装已上传成功。

## 4. 最新需求与当前未完成项

以下内容是用户最新明确要求，尚未完成或只完成了基础版本，接力者必须优先处理：

1. **原生轨迹迁移**：当前只有原始事件 bottom sheet。需要改为完整页面/页签，迁移 DSH Web 的 Duration / Turns / Calls、搜索、输入/模型/工具概览轨、轮次边界、工具行、展开检查器、usage/duration/timing 等真实事件语义。原组件依赖 DSH Web Store/slot/runtime，不能直接无脑 import；应复用其数据定义和视觉结构，在 MobileApiProxy 数据上适配实现。
2. **抽屉底部重排**：把设置入口从主页面顶栏移到左侧抽屉底部；其上显示当前 DeepSeek API 余额；底部并列加入横竖屏切换和设置按钮。
3. **真实 API 余额**：由服务器使用保存在服务器的凭据调用 DeepSeek `GET /user/balance`，客户端绝不能拿到 API Key。需设计可选服务依赖/安全失败状态；余额不能由 token 用量估算冒充。
4. **会话费用**：把 Web 截图里的 `Session log` 位置改成“本会话估算费用 / 上一次交互估算费用”。token 数应优先读取 DSH 权威 `tokenUsage` projection；费用按当前官方模型价格计算并明确标注“估算”。余额和费用是两个不同概念。
5. **横竖屏原生桥**：Android bridge 调用 `setRequestedOrientation`，浏览器 fixture 给出清晰提示；必须测试从竖屏到横屏再返回。
6. **API 用量设置页**：完成上述数据源后，将余额、总 token 桶、会话估算、上次估算放进现有 API 用量 section，替换目前的“协议暂不支持”占位说明。
7. **真实服务器回归**：fixture 通过后，还需连接一套 rc.2 服务器核验模型切换、历史续传、长流、工具事件、附件和费用投影。

## 5. 关于余额与费用的实现边界

- 余额：DeepSeek 官方接口返回的 `total_balance / granted_balance / topped_up_balance`，属于真实账户数据；
- token 用量：DSH `tokenUsage` projection，属于当前会话的权威累计值；
- 会话费用：根据模型、时间段和 token 桶计算的估算值，不等同于账单；
- 上次交互费用：优先从最后一次完整 usage 事件计算；若只能看到累计 projection，应在本地保存同一会话前后两个权威快照并计算差值，同时标明估算；首次加载没有可靠基线时显示 `—`，禁止编造；
- 模型价格会变，价格表必须集中、带来源/生效时间并可测试，不能散落在 React 组件里。

## 6. 运行中的本地测试环境

本次会话中开放过：

- Web UI：`http://127.0.0.1:4173/`
- fixture：`http://127.0.0.1:4318/dsh-mobile-v1`
- 测试配对码：`123456`

这些进程依赖当前 Codex 终端会话，接力时若端口失效，应分别执行客户端 dev server 与 `pnpm dev:fixture` 重新启动，不要把端口写死进生产配置。

## 7. 构建产物状态

`artifacts` 中现有 APK/网关包主要是 `alpha.1` 旧产物。源码虽已升为 `alpha.2`，但新增设置 section 及最新改动后的完整 `alpha.2` APK、网关包、签名校验和哈希报告尚未最终重建。因此旧 APK 不能作为当前源码验收依据。

最终交付至少应包含：

- `artifacts/apk/dsh-mobile-v1.0.0-alpha.2-debug.apk`
- `artifacts/gateway/dsh-mobile-v1-gateway-1.0.0-alpha.2.tgz`
- `artifacts/reports/` 下的测试、SHA-256、APK 签名/包信息记录
- 必要的竖屏、横屏、轨迹、抽屉余额和设置页截图

## 8. 推荐接力顺序

1. 先通读本文件、`AGENTS.md`、`docs/ARCHITECTURE.md`、`docs/PROTOCOL.md`、`docs/SECURITY.md`；
2. 检查当前 `git diff`/文件时间，保护已有改动；
3. 先写余额/费用的纯函数、类型和测试，再接 UI；
4. 完成抽屉 footer 与原生方向桥；
5. 把轨迹从 sheet 重构为完整视图，并以 fixture 构造真实 turn/tool/usage 数据；
6. 执行 `pnpm check`、`pnpm test`、`pnpm build`；
7. 浏览器四种 viewport 人工点检，确认 console/network 无异常；
8. 真实 DSH rc.2 契约回归；
9. 重建网关包和 APK，验证签名、包名、版本、哈希；
10. 更新本交接报告，把所有仍未完成内容逐项写明，绝不能用“基本完成”掩盖未验证项。
