# Mobile Gateway v1 协议

基础路径：`/dsh-mobile-v1/api`

## 设备配对

- `GET /health`：不含私密信息的版本与就绪状态。
- `POST /pair`：提交一次性配对码和设备名称，返回随机设备 token。
- token 只返回一次；服务端仅保存 SHA-256 摘要。
- 除 `health`、`pair` 外所有请求均要求 `Authorization: Bearer <token>`。

## DSH RPC 透传

- `POST /rpc/<method>` 映射到官方 `/api/<method>`。
- 请求体必须是官方 `ClientRequest`，`method` 必须同时与 URL 匹配。
- 返回体保持官方 `ServerResponse`，不改写业务错误。
- `POST /respond` 保持官方 `ClientResponse`，用于审批和用户问题。

首个纵向切片使用：

- `host.describe`
- `workspace.list`
- `session.list`
- `session.create`
- `session.history`
- `session.prompt`
- `session.cancel`
- `session.permissions`
- `session.setPermission`
- `session.models`

## 事件

- `GET /events/mux`：官方 mux downlink 的认证 SSE 转发。
- `GET /events/host`：官方 host downlink 的认证 SSE 转发。
- 客户端必须按 `rpcId` 关联可应答的 `approval/requested` 和 `question/requested`。
- Gateway 必须限制每设备并发事件连接数并在断开时取消上游流。

## 来源策略

允许来源默认为：

- `https://appassets.androidplatform.net`
- 显式配置的 HTTPS Web 预览 origin
- 开发环境下的 `http://127.0.0.1` / `http://localhost`

禁止 `Access-Control-Allow-Origin: *`。

