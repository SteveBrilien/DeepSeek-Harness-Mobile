# DSH Mobile 0.5.0-preview.2-dev｜手动 APK 交付记录

日期：2026-09-21（北京时间）。用户明确本轮验收方式为 **提供 APK，由用户自行安装反馈**；ADB、SSH 救援或助手代替真机验收均不作为交付前提。此版本是用户体验候选，不等于六项目标全部验收通过，也不自动修改 OTA。

## 本轮内容与边界

- versionCode=28、versionName=0.5.0-preview.2-dev；包名 com.stevebrilien.dshmobile，历史调试签名证书保持一致。
- 附件界面修正为保留 DSH 官方唯一回形针，点击后在 composer 下方展开图片预览和拍照／相册／文件入口；支持明确触发的近期照片授权与只读小缩略图。微动效含 reduced-motion；附件选择仍使用官方真实 input 与安全限制。
- **已知未实现**：近期照片直接点击加入官方草稿（当前为预览后跳转系统相册选择）。未取得 Vivo OriginOS 真机上传 1/2/3/5、权限回退、动画帧、冷温热启动的设备证据。用户反馈决定后续迭代，不声称上述场景 PASS。

## 与当前源码字节对应的工程门禁

- Node24 `--check`、`test-mobile-attachment-sources.mjs`、`test-mobile-drawer-gesture.mjs`、`test-mobile-ui-policy.mjs`、`test-webview-compat-policy.mjs`、`git diff --check`：PASS，2026-09-21 02:03 CST。
- Alpine E2E `task-runtime_alpine_e2e-fe63097d5f1a48b69209`：succeeded/exit0，DSH=0.1.5-rc.2，2026-09-21 01:57 CST。
- Android Lint `task-android_lint-bc3402d020dd4bb8a42a`：succeeded/exit0；0 errors、24 warnings。相较旧版多 1 条 `RecentMediaWebBridge.kt:80` WEB_MESSAGE_LISTENER 特性检查告警；安装路径已有 `isFeatureSupported` 前置条件，保留告警供后续审查，不伪称 0 warnings。
- Android debug build `task-android_debug-92903e75da974817bbd0`：succeeded/exit0，172 tasks；APK output metadata 核对包名、versionCode28、versionName，ZIP integrity PASS，包内附件插件 `0.1.2-dshm.1`。
- 历史签名 `task-android_signing_verify-096e1e7368354202b356`：succeeded/exit0；SHA256 证书 `085c7b7dea582ff9295b250f88d0e90e947bd293ac727a8240a747c8c9b24907`。
- Android unit `task-android_unit_test-4cc1215b09814bb1a9fc`：succeeded/exit0；app49、core/recovery12、core/runtime-android16，合计 77/77，0 failures/errors。模拟测试不等于真实用户设备验证。

本轮工程候选 `app/build/outputs/apk/debug/app-debug.apk` SHA256=`9960115df8aef842486f5eea9919bd4b243d4153e3fff5cb90bd51e51b6d27dd`、88372273 bytes；最终公开文件须复制后再次核对哈希，并实测 HTTPS 下载完整内容才向用户提供链接。`release/update.json` 保持原值；不做 ADB 安装、清数据或主动接触真实图库。