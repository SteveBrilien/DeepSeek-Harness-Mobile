# 2026-09-16 G0 实施前冻结与证据边界

状态：G0 主机基线记录完成；以下并非真机回归测试通过声明。关联问题 `2026-09-16-device-ui-issue-inventory.md`、方案 `2026-09-16-ui-remediation-and-acceptance-plan.md`。

- 进入时 main HEAD=`eaa26e4`（Preview.3 APK 手动发布提交）；`app/build.gradle.kts` 为 `0.4.0-preview.3` / versionCode 23；UI 包 `0.4.1-dshm.1`；`release/update.json` 仍是 Preview.2 / versionCode 22，不能把二者混同。
- Git 原有未提交的用户/上一轮文档：`docs/HANDOFF.md`、`docs/NEXT_ACTIONS.md`、`docs/UI_BASELINE.md`、两份新交接文档；原有未跟踪 `analysis/debug/2026-09-14-current-compat/`、`analysis/device-install-screen.png`、`analysis/ui-regression/`。禁止清理、覆盖或无差别提交这些文件。
- 开发 MCP doctor 通过，Registry ready；项目 Bubblewrap 内执行；ADB 真机是否在线未证明，Chromium 测试不替代 Vivo Android 11 WebView 的 IME/chooser/系统栏/动画证明。
- 既有 Preview.3 回归观测：全宽抽屉、会话标题遮挡、右栏入口丢失、键盘退回弹跳、导航栏干扰、一次多文件上传失败、Native workspace/settings/terminal 信息架构与安全缺口。截屏只是现象，因果须单独验证。
- 不删卸 app 数据、Runtime slots、Vault、Session、Projects、SSH、插件与未跟踪证据；不改变 targetSdk、签名证书、官方 DSH、WebView root compat 所有权。任何持久数据 schema/授权根/恢复迁移先新 ADR 与单独评审。
- 本轮实施可进入分切片代码修改、测试与本地候选编译；未经完整门禁和用户批准不更新公开 OTA、不宣称正式发布、不对真实文件执行破坏性测试。
- 当前阶段目标先处理证据明确且可以回滚的窄范围问题，无法确定根因的 IME、多文件上传与右栏入口要保留阻断/待真机状态，不得宣称修复。
