# DeepSeek 官方 Android APK：附件面板与最近图库静态分析（2026-09-18）

> 目的：为 DSH Mobile 用户提供的第四张参考图建立可复核的技术证据，修订附件技术规范；仅研究通用界面与公开 Android API 的可借鉴架构。**本轮不安装官方 APK、不登录、不采集用户数据、不复制/反编译成果移植原厂实现、不改 DSH Mobile 功能代码、不构建 APK。** 以下为静态证据；不等于运行时逐帧测量或上传端到端验收。

## 1. 样本来源与身份

- 官方下载页 `https://download.deepseek.com/` 的网页脚本 `main.da36db67e2.js` 中明确出现 APK 链接 `https://download.deepseek.com/apk/deepseek.apk`；直接从该官方域名下载，不使用重打包第三方镜像。2026-09-18 获取时 HTTP 200，Content-Type `application/vnd.android.package-archive`，Last-Modified `2026-09-15 03:25:36 GMT`。官方网页日后更新，故 URL 相同不代表 APK 哈希不变。
- 本次冻结样本仅保存于橙派项目 gitignored 临时位置 `.mcp/tmp/deepseek-reference/deepseek-official-20260915.apk`，**不加入仓库/发布包**。字节数 `14211169`；SHA-256 `a6d025c14c98118a5a67849e04aaef6f3c9c13aaf0fa62caaee0e47091374ea3`；ZIP 完整性通过。
- AAPT `dump badging`：包名 `com.deepseek.chat`，versionName `2.5.2`，versionCode `273`，minSdk `23`、targetSdk `36`。不同商店/分发渠道的 versionCode 或签名可能不同；本结论只绑定上述官方直链的当前 APK。
- APK 签名验证工具 `apksigner verify --print-certs` 返回成功；签名者 `CN=DeepSeek, OU=DeepSeek, O=DeepSeek, L=hangzhou, ST=zhejiang, C=cn`，证书 SHA-256 `c6893c5368f7b5122fecdafc6c4ecf49e0b3dc4e42bd166cdb646907788c7ffa`，SHA-1 `ca2378d73740d0010d2c4bce7eef948b32fef14b`。JAR META-INF 未受签名保护的标准告警不等于 APK 验签失败；证书名称也不替代官方直链的来源核验。
- 结构：APK ZIP 698 项，3 个 DEX，含 Android Jetpack Compose/RecyclerView。分析工具 JADX `1.5.6` 来自公开项目发行资产（ZIP 完整性通过），全包反编译到 gitignored `.mcp/tmp/deepseek-reference/decompiled-full`：输出 12,319 个 Java 文件，但**JADX 退出码 3、304 项解码错误**，有 R8 混淆与丢失方法；因此只能对实际可读的组件/调用作局部断言，不可宣称取得完整源码或确定每一帧布局实现。

## 2. 已确认的真实 UI 组件（按证据强弱）

| 证据锚点（临时反编译文件，非可复制源码） | 观察到的行为 | 可信边界 |
|---|---|---|
| `defpackage/e65.java` 约 901–1004：原始 Compose 调试来源 `UploadPanel.kt:33` | 独立 `UploadPanel` 可组合界面；读取图片授权态、调用图片条 `we5.d(...)`、按钮组 `uk8.t(...)`、按业务模式展示提示 | 确认它是 Android 原生 Compose 组件，不是 DSH/Cordis/WebView 插件；静态代码不能证明某台设备的具体动画或是否挂在 Android 的某一种 sheet 容器下 |
| `defpackage/uk8.java` 约 19087–19129：`UploadPanelActionButtonGroup.kt:18` | 明确建立 `[jr9.a, jr9.c, jr9.b]` 三个操作项，以相同权重构建布局 | 结合 `ge5.java` 的资源映射确认顺序「拍照／相册／文件」、三入口等重，不是系统 DocumentsUI 自带按钮 |
| `defpackage/ge5.java` 约 1381–1463：`UploadPanelActionButton.kt:41` | camera/album/file 各自映射独立字符串资源和 camera/photo/paperclip 图标 | 仅借鉴信息层级与动作划分；不搬运专有图标、资源或原代码 |
| `defpackage/we5.java` 约 2886–3043：`UploadPanelImageScroller.kt:32` | 不同授权状态决定图片区可用性；经 DI 获取 `x17` 媒体数据源，以水平列表构建图片条，宽高布局代码出现约 `80.0f`；尾部可附加更多照片入口 | 指示独立“最近媒体预选区”，不等于已上传的草稿附件；80dp 为该版本实现观察值，不构成 DSH 必须使用的尺寸 |
| `defpackage/kr9.java` 约 126–220：`UploadPanelImage.kt:140`；`defpackage/b17.java` `UploadPanelImageScroller.kt:122` | 图片 URI 异步加载为缩略图，绘制选中视觉；水平栏底部/尾部存在添加更多图片操作 | 可以确定图片条有选择语义；静态反编译不能保证所有错误、全图预览和上传状态细节 |
| APK `resources.arsc` 经 AAPT | `upload_panel_add_more_photos`, `_camera`, `_album`, `_file` 与 `upload_file_status_uploading`, `_failed`, `_sucess`（原厂资源拼写），`image_preview_*` | 区分“本地媒体/选择面板”与“上传中/失败/成功”概念；资源存在不代表所有服务端流程已验证 |

### 2.1 本轮关键纠正：最近相册不是官方附件草稿栏

`defpackage/x17.java` 是近期图片数据源，构造时选 `MediaStore.Images.Media.getContentUri("external")`（SDK>=29），更低版本使用 `EXTERNAL_CONTENT_URI`；投影 `_id`, `_display_name`, `date_added`, `_size`。`c(...)` 以 `ContentResolver.query` 查询，SDK>=26 在 Bundle 中按 `date_added DESC, _id DESC` 排序、**每页 20 项**及 offset，之后使用 `ContentUris.withAppendedId` 生成 `content://` 图片 URI；旧版本走等效 SQL 排序/分页。`e(Cursor)` 把 metadata 映射为列表条目。故横向缩略图是**显式取得媒体读取权限后的最近图片**，不是仅绘制用户已经选择的图片。查询只限 Android 已授权可见图库，不能推断它能够绕开 Android 相册权限或读取私有目录。

DSH 官方 `@deepseek-ai/dsh-client-ui-attachment` 单占 `conversation.input.attachments` 已有的横向 rail，则是**已选择进入 DSH 草稿的图片**，有预览/移除等逻辑，和这个近期图库不是同一份状态。用户第四张图要求的是「授权可见的近期图片预选」+「三入口」，**近期图片列现在列为明确目标功能**，而不是原技术规范中描述不清的可选增强；只有拒绝授权时降级为已选 DSH rail 与系统相册入口，且必须明确提示不显示近照的原因。

### 2.2 权限：在 Android 11 上不是无条件可查 MediaStore

`defpackage/z65.java` `UploadPanelPermission.kt:21/122` 按 SDK 显式分流：API>=34 使用 `READ_MEDIA_IMAGES`（全图）或 `READ_MEDIA_VISUAL_USER_SELECTED`（选定部分）；API=33 使用 `READ_MEDIA_IMAGES`；API<=32 使用 `READ_EXTERNAL_STORAGE`。APK Manifest 还声明 `CAMERA`、`READ_EXTERNAL_STORAGE` (`maxSdkVersion=32`)、`READ_MEDIA_IMAGES`、`READ_MEDIA_VISUAL_USER_SELECTED`、`WRITE_EXTERNAL_STORAGE` (`maxSdkVersion=29`)。**这是 DeepSeek 应用的权限决策，不应复制其 Manifest 或 targetSdk 到 DSH Mobile**；后者当前 targetSdk 28 与本机 Linux 执行策略有关（项目 ADR 0005），须独立评估 Android 11 / OriginOS 的 grant、设置页权限跳转、部分访问与撤销行为。拒绝许可/永久拒绝、返回后撤销应显示受限状态，系统 picker 仍可用时不应强迫整库授权。

## 3. 三入口实际打开的系统路径

- **相册/更多图片**：`defpackage/x6.java` 是照片多选的 ActivityResult contract。若平台选择器可用，构建 `android.provider.action.PICK_IMAGES`，设置受限图像 MIME、最多 `min(20, 实际业务上限)`，与 `MediaStore.getPickImagesMaxLimit()` 校验；否则尝试 AndroidX 兼容 photo picker，最后回退 `ACTION_OPEN_DOCUMENT` + `EXTRA_ALLOW_MULTIPLE=true`。系统/OEM fallback 可能不执行严格数量上限，结果方必须再检查 DSH 实际上限。
- **文件**：`defpackage/w6.java` 的 ActivityResult contract 在文件动作中构建 `ACTION_OPEN_DOCUMENT`、`EXTRA_MIME_TYPES`、`EXTRA_ALLOW_MULTIPLE=true`、`type=*/*`；只是系统文件授权入口，不能据此证明 DSH Web Host 支持把任意 PDF/TXT 发送给模型。
- **拍照**：同一合同中创建 `ACTION_IMAGE_CAPTURE`，传 `output` URI，并附加读/写授权 flags；独立 FileProvider/临时文件、产出检查和撤销必须由 DSH Mobile 自己设计。
- **系统 picker 返回**：`x6.U(...)` 和 `w6.U(...)` 都合并 `Intent.data`、`Intent.clipData`，用 `LinkedHashSet<Uri>` 保序去重；取消返回空。DSH Mobile 已有 `AndroidFileChooserResult.resolve()` 进行类似处理，但这一层成功只是返回 URI，并不证明 DSH 草稿入列、文件 MIME 支持或服务端确实收全附件。
- **业务接入**：`defpackage/gh6.java` 中 `ChatInputAttachmentMenu.kt:39` 的 `rememberUploadActions` 通过依赖注入取得上传/文件相关状态并连接 picker launcher；`wp1.java` 和 `nb5.java` 分别含 `calculateFileUploadStatus`、`hasUploadedFile`，`ut9.java` 映射成功/失败提示。可观察到“选择 → 上传业务 → 状态显示”分层，但由于 R8 混淆与 304 处反编译错误，不能声称掌握服务端协议，亦未用 1/2/3/5 个测试文件实测。

## 4. 对 DSH Mobile 的实际设计变更（规范性）

**决策 A：两条独立数据源。** 原生 Android `RecentMediaRepository` 只读取用户授权可见的 MediaStore 项：分页大小拟取 20，排序 date_added DESC + `_id` 稳序、后台线程查询、仅投影必要列、带版本/权限 generation 缓存和有界缩略图解码；SDK30 `READ_EXTERNAL_STORAGE` 必须按真实设备权限状态判定，若拒绝则不调用查询。URI 是 opaque `content://`，不可转全局文件路径，不记录原 URI/文件名/照片内容，不导出到普通日志。跨 Android 13/14 要独立做媒体受限权限矩阵。图片源接口仅回匿名 ID/展示安全元数据和可信的授权句柄；WebView 不能获得浏览任意 MediaStore 的通用权限。

**决策 B：三入口的外观与 Native 系统弹窗分离。** 浏览器 `@dsh-mobile/attachment-sources` Cordis plugin 注册 session/list 插槽显示三等宽来源入口与可访问权限说明，随 composer 正常布局展开、支持 IME/关闭、不盖住消息内容，不在 Compose 复制另一份底部 source sheet。Native 层承担由用户动作触发的 picker/相机/URI grants；现有 `ChatScreen.kt` `ModalBottomSheet` 应在桥接方案及回退通过后才被删除。平台 picker 可以是系统弹窗，禁止的是**三入口来源面板**再用无关的原生模态 UI。若插槽只能放 dock 上/下、不能插卡，必须在冻结实际 bundle 验证交互布局；不靠任意 CSS hash 猜 composer 内部节点。

**决策 C：近照预选只是待提交选择，不能直接充当 DSH 文件草稿。** 点击近照要在 Web 主动选择动作上创建单次授权范围内的明确 selection request，Native 回传最小的选定媒体句柄；必须先验证/补充正式 DSH `admitFiles(File[])` 公共契约及 `accept`, `multiple`, MIME, 模型、单项/总大小/数量限额。所有图像仍进官方单占 `conversation.input.attachments` 草稿栏，使用官方 remove/lightbox/upload/session event。不得复活旧版通过合成 ClipboardEvent/DOM `FileList` 接入的私有路径，也不得把 `IConversation` 接口外方法当稳定入口。若本版冻结 DSH 没有合规 intake API，阻断实际上传集成，优先给官方接口提最小 typed extension，再做附件入口插件。

**决策 D：细粒度桥接，先认证再赋权。** `RecentMediaRepository` 与 `NativeSourcePicker` 属 Android 用户身份；只与受信任、真实 main-frame 的本地 DSH origin 通信，校验 WebMessage feature/origin/request id/session generation/gesture freshness/来源 enum/数量 limit，拒绝任意网页、子框架、外部 URL、旧请求重放。不要以现有只读 geometry handshake 冒充新授权桥；专门提出版本化 `MediaBridgeV1` 协议和 lifecycle。建议限定 `requestSources / listRecent / pickSelected`，返回无任意 `content://` 指定读取的能力句柄，真正 URI/内容仅能经用户点击与 Host 指定受限入口投递，不开放“read arbitrary URI”；页面重新导航或插件卸载撤销 pending 请求，取消一次完成，无权限时不兜底搜全库。

**决策 E：能力与反馈不可伪装。** 在出现近期图片时允许显示允许范围/授权范围状态；相册 picker 与三入口在无整库读取权限时仍需可用（实际 Intent 支持为准），不可常驻空缩略图。文件入口不对 DSH 当前不支持的 MIME 宣称「发送成功」。上传成功、服务端接收、最终会话事件回显分开判定，错误按项显示且保留草稿。是否允许 20 张由 DSH 模型/后端上限决定，不能照搬 DeepSeek Cloud App。

## 5. 可执行的下一轮实验与验收矩阵（此轮未执行）

1. Android 11 OriginOS：分别授予/拒绝/撤销 `READ_EXTERNAL_STORAGE`；近照栏只在允许时查询 MediaStore（20 条分页、排序稳定、无越权），拒绝时近期栏消失但三入口照常启动系统 picker；切后台/权限更新立即失效缓存。
2. Android 13/14 模拟器：全量/选定媒体/拒绝，`READ_MEDIA_IMAGES` 和 `READ_MEDIA_VISUAL_USER_SELECTED` 分级；拒绝后 fallback、不读取未选照片。
3. 320/360/390dp、浅/深/东京夜色：近期 80dp 参考尺寸由 DSH 的布局实际适配（先 64–80dp），三入口不换行、不遮输入和 IME；横向近照滚动不得触发左抽屉手势。
4. Pick 1/2/3/5 图片：URI 去重、grant 可读、逐项 `accepted/ready/error`、用户可撤销、Session 切换隔离；发送后 Host 真实附件数量/测试样本 SHA（仅测试样本）、历史 reload/服务端回执均通过。系统 picker fallback 返回超过上限也应在 Host 再次拦截。
5. 文件/相机：`ACTION_OPEN_DOCUMENT` 支持的 MIME/数量；相机权限拒绝、无相机可处理应用、`result.data == null` 但 output 存在、生成失败清理、后续进入官方 DSH 文件能力边界。
6. 边界：Activity 被杀、WebView renderer 消失、页面导航、Bridge 被禁用、插件 Safe Mode、低内存/大图/坏 EXIF/Uri provider 超时；失败保留草稿、一次终结回调、无全局 URI 能力泄露。
7. 必须先恢复安全的独立手机 SSH 并取得设备验收证据；橙派 Chromium/Compose 模拟通过不能冒充真实服务端发送或 OriginOS 可用。

## 6. 静态证据局限和法律/安全界限

- 官方 APK 与 DSH Harness 属**不同应用/不同前后端**：前者的 Compose 图片面板和私有上传状态不能作为后者 Cordis 插件 API 已支持的证明；推论部分已在表格和实施阻断中明确标识。
- JADX 部分反编译失败 304 项，缩略图缓存细节、所有按钮动效、具体父容器、完整授权策略、云端发送协议未确认；未经运行实验不得以「原厂正在用 X 的全部算法」「服务端肯定支持全部文件」下结论。
- 本文只陈述功能机制和通用 Android 公共 API 名称/源码位置，**不复制原厂反编译代码、图标、字体或私有资源**。APK、证书原文件、反编译 Java、图库样本留在 `.mcp/tmp` 忽略目录，不提交、不发给用户、不制作重打包 APK，也不利用它的私有接口或认证凭据。
- 下一轮若需视觉行为动态证据，应在授权测试设备上安装官方正版/不带个人数据账号以人工观察、用公开系统 API 分析自身 App 的行为，不绕过加固/签名或截获原厂用户私有数据。此轮未进行此类动态测试。

## 7. 与主技术规范的同步关系

对应主文档：`docs/handoff/2026-09-18-mobile-ui-workspace-drawer-attachment-technical-implementation-spec.md` 的 `ATT-2`、§3.4、§3.5、D1/D2 及未决事项。它应将“近期图库可选增强/未证实与参考图关系”替换成“官方样本证明近照面板单独通过已授权 MediaStore 数据源生成、明确的 UX 目标；仍需受控媒体权限和新的桥接/DSH 入列契约”。主文档其余 Drawer/Workspace/Backup 安全不变量不受官方 APK 影响。
