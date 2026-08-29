# 安全边界

## 人类控制面

审批、权限、设置和凭据属于人类控制面。公网反向代理必须拒绝直接访问 DSH 原始 `/api`，只允许认证后的移动 Gateway；否则 Agent 子进程或旁路客户端可能绕过设备身份直接应答审批。

Gateway 的设备 token 只是单用户部署的传输身份，不把 DSH 改造成多租户系统。不同设备默认仍看到同一个宿主的会话；未来若需要多用户，必须在 session/workspace 层增加所有权模型。

配对码 10 分钟过期、成功即轮换，并按来源地址限制为每 10 分钟 8 次尝试。只有在可信反向代理会覆盖客户端 `X-Forwarded-For` 时才允许设置 `DSH_MOBILE_TRUST_PROXY=1`。

## Android

- 本地资源使用 `WebViewAssetLoader` 的 HTTPS origin。
- 只允许导航到 appassets origin；外链交给系统浏览器。
- JavaScript bridge 只在可信主 frame 注入。
- 设备 token 使用 Android Keystore 加密后保存。
- Room 中的会话镜像 payload 使用 Android Keystore AES-GCM 加密；数据库中不保存模型/API 凭据。
- 当前版本不在应用内静默下载或安装更新；未来加入更新能力时，必须固定 HTTPS origin，并验证包名与签名证书。
- 禁止明文流量、混合内容、WebView 调试版进入 release。

## 发布密钥

release keystore 和密码必须由 CI secret 或人工安全路径注入，不放在工程中。旧原型 keystore 已与固定密码同时出现，不能继续作为正式更新信任根。
