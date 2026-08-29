# DSH Mobile v1.0

DSH Mobile v1.0 是 DeepSeek Harness 的 Android 一等客户端，不是桌面网页的缩放版，也不在手机上复制 Agent 或另建一套会话真源。

当前版本：`1.0.0-alpha.1`，兼容基线：`@deepseek-ai/dsh@0.1.1-rc.2`。

## 已完成的纵向切片

- `packages/mobile-client`：移动优先 React/TypeScript 客户端，支持真实 session 列表、history、流式 mux/host 事件、提问、取消、审批和模型选择。
- `packages/mobile-gateway`：在 DSH 服务器进程内复用官方 `ctx.apiProxy`，提供一次性配对、设备 Bearer token、RPC 白名单和流式载体。
- `android`：Java WebView 容器、Android Keystore、Room 加密镜像缓存、返回键和安全导航策略。
- `artifacts/gateway`：可复制到服务器 DSH profile 的网关包。
- `artifacts/apk`：Android 安装包。
- `.cache`：项目专用 Gradle、Android SDK、Gradle user home 和下载缓存。

旧工程 `../dsh-mobile-app` 只作为视觉与行为参考，不参与 v1.0 构建。新 UI 只复用 DSH 的鱼形标志、黑白灰蓝令牌和必要的信息层级，不带旧工程的模拟对话、独立 `sessions.json` 或 DOM 注入补丁。

## 数据与启动体验

DSH 和 `DSH_HOME` 始终部署在服务器，同一服务器上的手机、浏览器和其他设备读取同一组会话与上下文。Android 本地保存一份可删除、可重建的加密镜像：启动先显示缓存，再与服务器校准，因此正常情况下不会表现成每次从零打开。

本地缓存不拥有 Agent 状态；离线时只能查看缓存和编辑草稿，不能排队执行 prompt、审批或设置修改。详见 [本地缓存](docs/LOCAL_CACHE.md)。

## 不可破坏的原则

1. DSH session event log 是唯一会话真源，禁止平行会话数据库。
2. 产品代码禁止模拟回复、模拟权限或静默伪造成功。
3. Android 只通过认证后的 Gateway 访问 DSH，不暴露原始 `/api`。
4. 每个可见控件必须接入真实能力；未实现能力应隐藏或明确标为不可用。
5. 新增源码、预编译文件、缓存和结果全部留在本目录。
6. UI 只使用集中定义的 DSH 黑、白、灰、蓝色号；红/绿只表示错误或成功状态。

## 构建

```powershell
pnpm check
pnpm test
pnpm build
pnpm package:gateway
pnpm bootstrap:android
pnpm build:android
```

`bootstrap:android` 会复用系统中 Java 17+，其余 Android 工具链写入 `.cache`。`build:android` 优先使用项目内 Gradle，不依赖 Android Studio。

当前已验证产物：

- `artifacts/apk/dsh-mobile-v1.0.0-alpha.1-debug.apk`
- `artifacts/gateway/dsh-mobile-v1-gateway-1.0.0-alpha.1.tgz`

调试 APK 使用 Android debug 证书，只用于安装验证；正式发布必须配置独立 release keystore。

## 服务器接入

网关必须安装到承载现有会话的同一个 DSH web profile，并保持原来的 `DSH_HOME` 不变。公网只开放 HTTPS，反向代理转发 `/dsh-mobile-v1/`，原始 DSH `/api` 不应直接暴露。具体步骤和环境变量见 [服务器部署](docs/DEPLOYMENT.md)。

详细资料：[架构](docs/ARCHITECTURE.md)、[协议](docs/PROTOCOL.md)、[设计规范](docs/DESIGN_SYSTEM.md)、[验收标准](docs/ACCEPTANCE.md) 和 [安全边界](docs/SECURITY.md)。
