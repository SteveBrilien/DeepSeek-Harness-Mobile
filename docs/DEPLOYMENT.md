# 服务器部署

## 部署位置

移动网关必须进入服务器上正在运行的 DSH `web` profile。不要复制或切换 `DSH_HOME`：它包含现有 session、workspace、设置和凭据，换成快照目录会让用户看到旧上下文。

网关包位于：

```text
artifacts/gateway/dsh-mobile-v1-gateway-1.0.0-alpha.1.tgz
```

把 tarball 上传到服务器后，在现有 `~/.dsh/profiles/web/package.json` 中：

1. 将 `dsh-mobile-v1-gateway` 加入 `dependencies`，值指向上传后的 tarball 或解压目录。
2. 将 `dsh-mobile-v1-gateway` 加入 `dsh.profile.bundles`。
3. 在该 profile 内完成依赖安装，然后重启原有 DSH web 服务。

网关需要 profile 已提供 `webServer` 与 `apiProxy`；标准 DSH web 基础 bundle 已包含这两个注入点。修改前应备份 profile 的 `package.json`，不要覆盖其他已安装 bundle。

## 环境变量

- `DSH_MOBILE_DATA_DIR`：设备登记文件目录；默认是 `$DSH_HOME/mobile-v1`。
- `DSH_MOBILE_PAIRING_CODE`：可选的首次 6–12 位数字配对码；未设置时会生成 6 位码并写到服务日志，10 分钟过期且成功后立即轮换。
- `DSH_MOBILE_PUBLIC_ORIGIN`：可选的网页预览 origin，例如 `https://dsh.example.com`；Android appassets origin 已内置允许。
- `DSH_MOBILE_TRUST_PROXY=1`：仅当反向代理会覆盖而不是透传客户端提供的 `X-Forwarded-For` 时启用，用于按真实地址限制配对尝试。

设备 token 只以 SHA-256 摘要形式保存在服务器 `devices.json`；Android 端原值由 Keystore 加密保存。配对接口按地址限制为每 10 分钟 8 次尝试。

## 反向代理

- 只使用 HTTPS，把 `/dsh-mobile-v1/` 原样转发到 DSH web 服务。
- 不要对该路径返回网页登录页或 302；原生 App 使用 Bearer token，不持有门户 cookie。
- `/dsh-mobile-v1/api/pair` 是短期一次性码入口，其余移动 RPC/事件接口要求设备 token。
- 不要将 DSH 原始 `/api` 暴露到公网，否则会绕过设备身份边界。

## 验证与配对

重启后先检查：

```text
GET https://<服务器>/dsh-mobile-v1/api/health
```

应返回 `ok: true`、`appVersion: 1.0.0-alpha.1` 和 `contractVersion: 1`。然后从服务日志取得当前配对码，在 App 中只填写服务器 HTTPS 地址和配对码。成功后该码失效，手机用加密保存的设备 token 恢复连接。

当前调试 APK 不绑定固定服务器地址，同一个 APK 可以配对任意兼容的 DSH 服务器。
