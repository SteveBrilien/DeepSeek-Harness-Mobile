# 2026-09-21 附件面板交互纠偏（用户截图确认）

## 用户确认的验收目标

- 保留 DSH 官方原有回形针的 DOM、SVG、样式及可禁用状态；不能额外注入第二个回形针，更不能用 CSS 隐藏原按钮。当前此前版本把 `SourceTrigger` 注入 `conversation.input.left` 并隐藏官方按钮，是错误实现，按本文件废止。
- 点击官方回形针，从屏幕底部在输入框**下方**展开面板，输入框被自然向上顶起；面板展示横向近期图片缩略图及底部拍照、相册、文件三个等权入口，不常驻输入框上方，不弹 Android 原生来源 ModalBottomSheet；再次点击、点击外部、Esc/系统返回按真实运行平台能力收起。主页（hero）和会话页（active）都须验收。
- 用户允许在内嵌近期照片功能需要时申请手机相应权限。仅点开附件面板可查询**已有授权**；若缺权限，必须再由用户明确点击“允许访问最近照片（仅供预览）”后调用系统权限对话框。拒绝后仍可使用 Photo Picker/相册；不能直接读取未授权图库。
- 真正图片上传必须继续走 DSH 官方 `input[type=file] -> onPickFiles -> intakeFiles -> Host`；不得注入合成 input/change/paste 或另起未鉴权的上传队列。近期图片直接加入草稿须依靠版本化的公开 `File[]` 正式 intake 接口，未解决时显示“预览 → 前往相册勾选”，禁止冒称直接选中成功。

## 本次修复切片

1. 附件插件移除第二个图标和原按钮隐藏规则，改为仅拦截官方回形针真实点击（入口三个按钮仍同步点击官方隐藏 input）；会话态注册公开 `conversation.composer.dock`，主页使用 `conversation.input.dock` 的后置排版，显式 reduced-motion。插件卸载恢复官方点击及原始 input 属性。
2. 新增 Native `RecentMediaWebBridge`：WebView listener 限定准确 loopback origin、main-frame、schema、请求大小和编号；Android 11 使用已有 `READ_EXTERNAL_STORAGE` 清单声明，只有明确点授权入口才触发 ActivityResult 权限申请。查询上限 12，返回随机短期 handle；按 handle 只读 96px、至多 32KiB JPEG 缩略图；导航、销毁、权限撤销清句柄，禁止 Web 任意 content URI/文件路径与原图读取。
3. 插件横向展示有限近期图、点缩略图只读放大预览，提供跳转系统相册明确勾选入口；权限拒绝/设备不支持/Bridge 缺失都有降级，不制造占位假照片。
4. App 升 `0.5.0-preview.2-dev`/code 28；managed 附件插件升 `0.1.2-dshm.1`，使已安装 Preview.1 旧 profile 可识别插件字节更新；不更改旧发布 APK、下载哈希和 OTA `release/update.json`。

## 分级验收及已知缺口

- Node24 插件契约：官方图标不隐藏/不重复、面板 Hero+Active 双槽但仅可见者请求图库、三来源继续使用真 input、显式权限、模拟缩略图只以 data URL 显示、Esc、卸载复原。单元层不是 OriginOS 视觉实测。
- Android API30 JVM：已确认修正最初 `WebMessageReplyProxy` 拼写错误为确实存在的 `JavaScriptReplyProxy`，后续任务回执单独记入；无真机授权就绝不读取私人照片。
- 待完成（不可伪称交付）：最近图片**直接点击入官方草稿**的公开附件 intake/Host 回执、真实 V2115A 权限 UI + 缩略图/相册选择 1/2/3/5 真实上传、WebView 页面具体定位和动画录屏、冷温热指标及公开 APK 覆盖安装。零设备可用时保持 DEVICE_NOT_RUN；代码和打包成功不等于用户满意或发布验收。

本文件优先于旧 V1.3 清单中“常驻 composer 上方三块/替换回形针”等与用户四张截图冲突的设计描述；其他用户已确认 UI 不重新打开需求。更改、测试/签名及局限必须有 commit/job ID 追踪。