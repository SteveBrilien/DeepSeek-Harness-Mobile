# DSH Mobile｜附件三态参考与微动反馈切片

日期：2026-09-21。用户提供三张手机参考截图，本文件为目标/实现/验收边界，不把参考截图冒称 DSH 当前运行效果。与 `2026-09-21-attachment-panel-owner-correction.md` 配套；本轮不重新开发用户已确认完成的其他 UI，也不恢复 SSH 救援作为门禁。

## 目标状态（同一 composer，同一附件草稿）

1. S0 收起：欢迎页/会话页保留当前输入框及官方唯一回形针；附件区无常驻大按钮、无 Android 来源弹窗。官方图标本身不被替换或隐藏。
2. S1 展开：点击官方回形针后，composer **下方**展开最近图片横向列表与拍照/相册/文件三块等宽入口；整个输入框被自然顶起，来源区占正常布局、底栏仍可见。再次点击、外部点击及可用的系统返回可关；触发原生系统 picker 后面板恢复收起。最近图库仅授权后限量查询；拒绝仍可启动系统相册。
3. S2 已选：用户真正选中 1/2/3/5 张图后，使用官方 draftIds 和附件 rail 在 composer 内展示缩略图、逐项移除和发送状态；下方最近图片有与真实官方草稿绑定的勾选态，点击已选项目可预览，不能以“只读预览”冒充正式选中。参考图中的快捷提问 chip 为可选展示，若实施只能插入文本而不能在用户未点击发送前自动提交。

## 微动反馈标准（不改变业务）

- 拍照/相册/文件三个按钮及近期缩略图：手指按下视觉层缩放到约 0.965，向下移动 1px、轻微变暗；按下渐变 85ms，松开用 170ms 缓出并略带弹性，边框/布局盒尺寸不变；鼠标/键盘焦点需有 `:focus-visible` 轮廓。
- 附件面板入场：约 210ms，向上位移 18px → -2px 轻微回弹 → 0、透明度渐显；收起的退出动画以后以真实 WebView 帧验证，当前组件立即卸载，不宣称已有完整退出动画。推荐优先 `transform`/`opacity`，不要动画化布局宽高或给 modal 的祖先套 transform。
- 选中态：必须以官方草稿/Session 事实为准，不能仅把 preview 状态变为打勾；重复点击、权限撤销、切 Session、发送后要与官方附件管理保持一致。
- `prefers-reduced-motion: reduce` 时取消位移缩放与过渡，仅保留瞬时亮度/焦点信息；手势由原布局承担，CSS 不截获 pointer/click，不使用合成 change/paste，不增加 Android 权限。
- 原生 Compose 来源按钮如果以后存在，建议 `MutableInteractionSource` + `collectIsPressedAsState` + `animateFloatAsState` + `graphicsLayer(scaleX/scaleY)` 实现视觉反馈；本切片以 Cordis Web 插件按钮为唯一目标，避免双重动画。

## 本轮实现与证据边界

- 在现有未提交的附件插件 `lib/client.js` 内做**最小 CSS 增量**：现有 source 与 recent-preview data 属性添加 `:active` 缩放/位移/亮度、焦点轮廓、reduced-motion；入场关键帧加 -2px 回弹。不更改官方 `input[type=file]`、回形针、Picker/MediaBridge 协议及本机真实图库。
- `scripts/test-mobile-attachment-sources.mjs` 增加样式契约断言。Node24 syntax/attachment/drawer/UI-policy/WebView-policy + `git diff --check` 已在本切片执行并通过。它们只证明静态语法、DOM 合同和合成行为；不能证明 OriginOS 120Hz/60Hz 动效观感。
- `2026-09-21-attachment-panel-owner-correction.md` 正在施工的最近图库桥接、双布局容器和版本升级由现有工作树负责；不另建平行附件入口，不提交/发布包含并行改动的 APK。

## 真机验收与发布阻断

- OriginOS 实测 360dp/深浅及东京夜色：展开时 composer 随正常流向上移动、收起不闪屏/不跳底栏；拍照/相册/文件三块每个至少 10 次按下/取消，滚动近期图 20 次不误选、不误开抽屉；键盘开/关与系统返回保持可用；reduced-motion 设置下无强制动效。
- 官方真实 1/2/3/5 张图片选择 → 草稿缩略图与勾选一致 → Host 回执 → Session 重载；权限拒绝/撤销和 ActivityResult 迟到都不能串会话。未有版本化正式 recent `File[]` intake 时，近期图片只提供预览及明确相册跳转，S2 的近期直接选中保持 BLOCKED。
- 必须补字节对应的 Alpine E2E、Android unit/lint/debug/signing，Chrome exact-current-profile 视觉及真机录屏；以上未获得结果前，本切片状态为 `MICRO-CSS IMPLEMENTED / DEVICE_NOT_RUN / NOT_RELEASED`。不改已发布 APK/OTA、不安装或读取用户真实相册。

补录：`runtime_alpine_e2e` job `task-runtime_alpine_e2e-fe63097d5f1a48b69209` succeeded/exit 0，输出 `runtime-alpine-e2e: PASS dsh=0.1.5-rc.2`，含 managed attachment plugin 检查。Node24 语法/附件/抽屉/UI/WebView policy 与 `git diff --check` 同轮 PASS。Alpine E2E 不等于 Chrome 视觉、Android 真机、近期图直接入列或最终发布；其余未完成门禁保持上述状态。
