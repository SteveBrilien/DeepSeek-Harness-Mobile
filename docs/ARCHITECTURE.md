# 架构基线

## 目标形态

```text
Android Java container
        │ encrypted Room cache + Android Keystore
        │ https://appassets.androidplatform.net
        ▼
Mobile React client
        │ Bearer device token
        ▼
dsh-mobile-v1-gateway
        │ exact DSH RpcRequest / ServerRequest envelopes
        ▼
@deepseek-ai/dsh-host-apiproxy (ctx.apiProxy)
        ▼
Agent · Session · Approval · Permission · Settings · Workspace
```

Gateway 是一个认证载体，不是第二套 Agent 后端。它通过官方 `toFetchHandler(ctx.apiProxy)` 复用完整协议，因此 Session 创建、恢复、历史、流式事件、工具、审批、权限和设置仍由 DSH 拥有。

DSH 及其 `DSH_HOME` 部署在服务器。手机不运行 Agent，也不持有另一套可分叉的 session log；不同地点和多台已配对设备始终读取服务器上的同一上下文。

## 目录所有权

- `mobile-client` 只拥有移动导航、渲染状态和用户输入。
- `mobile-gateway` 只拥有设备身份、来源校验、限流和安全转发。
- `android` 只拥有 WebView 生命周期、加密镜像缓存、系统分享、更新安装、键盘/返回键和安全存储。
- DSH 核心继续拥有模型、Agent 循环、工具、会话、工作区、权限与持久化。

## 迁移策略

旧静态壳只复用以下资产：图标轮廓、品牌标识、部分色彩映射和移动信息层级。旧 `app.js`、`data/sessions.json`、裸 LLM `/api/chat` 与本地模拟回复不迁移。

旧 DOM 插件只作为行为清单：抽屉、全屏设置、输入器安全区和触控尺寸。其 MutationObserver、结构选择器和 `!important` 补丁不进入运行时。

## 连接与恢复

- unary：认证后的 HTTP POST，保留 DSH 原始 `ClientRequest`/`ServerResponse`。
- downlink：Gateway 把 `ApiProxy` 的进程内 SSE 载体流转发为带认证的 SSE。
- App 回到前台后重连两个事件流，并重新读取 session history；事件日志负责去重与恢复。
- 首屏先读取手机的加密镜像缓存，再用 `session.list`、history tail 与事件序号增量校准；服务端结果始终获胜。
- 断网不得生成本地答案。未送达的草稿留在设备；已提交 turn 以 DSH history 为准。

## 版本兼容

当前兼容基线是完整发行栈 `@deepseek-ai/dsh@0.1.1-rc.2`。客户端握手记录 `host.describe` 结果和 Gateway contract version；遇到未知必需字段时进入“服务版本不兼容”页面，而不是猜测 DOM 或继续发送。
