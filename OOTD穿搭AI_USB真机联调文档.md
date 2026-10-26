# OOTD 穿搭 AI — USB 真机端到端联调文档与测试清单

> 模块：`OotdAnalyzeScreen`（分享页「AI 穿搭」副页面）
> 后端接口：`POST /api/ootd/upload`（图片上传 → TOS 预签名 URL）、`POST /api/ootd/chat/sse`（SSE 流式分析）
> 网络客户端：`OotdSseClient.kt`（OkHttp，readTimeout=120s）
> 图片工具：`OotdImageUtils.kt`（最长边 1280px / JPEG 质量 82 压缩）
> 最近更新：2026-10-19

---

## 一、前置准备

### 1.1 电脑端（后端）

| 项 | 操作 | 说明 |
| --- | --- | --- |
| 启动 SpringBoot 后端 | 运行后端主类，确认监听 `0.0.0.0:8080` | 必须监听 `0.0.0.0` 而非 `127.0.0.1`，否则真机 WiFi 访问不到 |
| 查看局域网 IPv4 | 命令行执行 `ipconfig`，取「无线局域网适配器 WLAN」的 IPv4 地址 | 例：`192.168.1.100`；若用电脑热点，取热点网卡 IP |
| 防火墙放行 8080 | Windows 防火墙 → 入站规则 → 新建规则 → 端口 → TCP 8080 → 允许 | 或临时关闭域/专用网络防火墙测试；公司网络可能需额外放行 |
| 验证后端可达 | 电脑浏览器访问 `http://<局域网IP>:8080/` 或后端健康检查接口 | 能返回数据即说明监听正常 |

### 1.2 手机端（真机）

| 项 | 操作 | 说明 |
| --- | --- | --- |
| 连接 USB | 数据线连接电脑，手机弹窗选择「文件传输（MTP）」 | 仅充电模式可能无法 adb 识别 |
| 开启开发者模式 | 设置 → 关于手机 → 连续点击「版本号」7 次 | 已开启则跳过 |
| 开启 USB 调试 | 设置 → 开发者选项 → 打开「USB 调试」 | 首次连接电脑需在手机弹窗点「允许 USB 调试」 |
| 同一 WiFi | 手机与电脑连接同一个 WiFi（或电脑开热点给手机连） | 这是真机访问电脑后端的前提，不可用流量 |
| 安装 App | Android Studio Run 或 `adb install -r app-debug.apk` | 覆盖安装即可 |

### 1.3 代码端配置（必须改）

打开 `app/src/main/java/com/example/citygo/skeleton/data/remote/ootd/OotdSseClient.kt`，把 `companion object` 中的 `HOST_IP` 改成上一步查到的电脑局域网 IPv4：

```kotlin
private const val HOST_IP = "192.168.1.100"  // ← 改成你电脑的局域网 IPv4
private const val PORT = 8080
private const val BASE_URL = "http://$HOST_IP:$PORT/"
```

改完重新编译安装。页面底部会显示当前联调地址，方便核对。

> 说明：主 App 其余接口走 `adb reverse tcp:8080 tcp:8080` + `127.0.0.1:8080`；OOTD 模块独立走局域网 IP（同一 WiFi），两套互不干扰。

---

## 二、完整测试流程

### 2.1 入口与页面栈

1. 启动 App → 登录 → 底部 Tab 切到【分享】
2. 顶部栏右侧点【✨ AI 穿搭】按钮
3. 进入「OOTD 穿搭 AI 分析」副页面（无底部导航栏）
4. 点左上角返回箭头 → 回到【分享】主页（不跳首页、不丢分享页状态）

### 2.2 选图 → 上传 → 流式分析

1. **选图**：点【拍照】唤起相机（首次弹相机权限），或点【相册】选图（Android 13+ 走系统 Photo Picker，免存储权限）
2. **原图预览**：选好后页面展示穿搭原图，可点【重新拍照】/【相册重选】
3. **提问（可选）**：在输入框输入想让 AI 分析的问题，默认「帮我分析这套穿搭」
4. **开始分析**：点【开始 AI 分析】按钮
5. **上传阶段**：按钮显示「图片上传中…」+ 转圈；本地压缩（最长边 1280px / JPEG 82）后 multipart 上传
6. **流式阶段**：上传成功后按钮变「AI 正在分析…」，结果卡片逐字打字机输出分析文本
7. **知识库引用**：若 AI 引用了知识库规则，页面下方展示「知识库引用（N 条）」标签列表
8. **完成**：流结束后按钮变【重新分析】，结果卡片显示「分析完成」

---

## 三、测试用例

| # | 用例 | 操作步骤 | 预期结果 | 结果 |
| --- | --- | --- | --- | --- |
| 1 | JPG 正常图 | 相册选一张 `.jpg` 穿搭图 → 开始分析 | 上传成功，SSE 流式返回分析文本，原图正常显示，分析完成后可重新分析 | ☐ |
| 2 | PNG 正常图 | 相册选一张 `.png` 穿搭图 → 开始分析 | 上传成功，压缩后转 JPEG 字节上传，SSE 正常返回 | ☐ |
| 3 | 大图（>5MB） | 选一张 4000×3000 以上的大图 → 开始分析 | 本地压缩到最长边 1280px，上传体积约 200-400KB，不超时，SSE 正常返回 | ☐ |
| 4 | 空图/无效图 | 选一张损坏的图片文件（或 0 字节文件） | 页面提示「图片处理失败：…」或「无法读取图片，请更换图片重试」，不崩溃 | ☐ |
| 5 | 断网上传 | `svc wifi disable`（或关 WiFi）→ 选图 → 开始分析 | 上传阶段失败，显示红色「网络异常」卡，文案包含「无法连接后端 http://…」，不崩溃 | ☐ |
| 6 | 断网 SSE | 上传成功后立即断网（或后端在 SSE 阶段关闭） | 已收到的文本保留展示，额外显示错误状态卡；不弹出「服务响应超时」（仅 onFailure 才弹） | ☐ |
| 7 | 相机拍照 | 点【拍照】→ 授予相机权限 → 拍一张穿搭照 | 拍照成功，原图预览显示，压缩上传正常 | ☐ |
| 8 | 相机权限拒绝 | 首次点【拍照】→ 拒绝相机权限 | Toast「相机权限被拒绝，无法拍照」，不崩溃 | ☐ |
| 9 | 返回栈 | 分享页 → AI 穿搭 → 返回 | 回到分享页，瀑布流状态保留（位置、数据不丢） | ☐ |
| 10 | 分析中退出 | 流式分析中点返回箭头 | 页面关闭，SSE 连接自动断开（`DisposableEffect` + `viewModel.close()`），无内存泄漏 | ☐ |

---

## 四、故障排查清单

### 4.1 图片上传失败

| 现象 | 可能原因 | 排查步骤 |
| --- | --- | --- |
| Toast / 状态卡「图片处理失败」 | 图片损坏、格式不支持、EXIF 读取异常 | 1. Logcat 过滤 `OotdImage` 看 `compress failed` 日志；2. 换一张正常 JPG 重试；3. 确认图片 Uri 是 `content://` 而非无效路径 |
| 「图片上传失败（HTTP xxx）」 | 后端上传接口报错、参数不对 | 1. Logcat 过滤 `OotdSse` 看 `upload http xxx` + 响应体；2. 确认后端 `/api/ootd/upload` 接收 `multipart/form-data` 的 `file` 字段；3. 用 Postman 直连后端上传接口验证 |
| 「上传成功但未获取到图片地址」 | 后端返回的 JSON 结构与解析逻辑不匹配 | 1. Logcat 看 `upload ok but no presigned url in body: <raw>`；2. 检查 `OotdSseClient.parsePresignedUrl` 兼容的字段（`presignedUrl`/`url`/`imageUrl`/`tosUrl`/`data`）；3. 若后端字段名不同，在 `URL_FIELDS` 列表中补充 |

### 4.2 SSE 连不上

| 现象 | 可能原因 | 排查步骤 |
| --- | --- | --- |
| 红色「无法连接后端 http://<IP>:8080/」 | IP 错误、WiFi 不同、后端没起、防火墙 | 1. 核对 `OotdSseClient.HOST_IP` 是否为电脑当前局域网 IPv4；2. 确认手机与电脑同一 WiFi；3. 手机浏览器访问 `http://<IP>:8080/` 看能否打开后端页面；4. 检查电脑防火墙是否放行 8080；5. 确认后端监听 `0.0.0.0:8080` 而非 `127.0.0.1` |
| 「服务异常（HTTP xxx）」 | 后端 SSE 接口路径或参数不对 | 1. Logcat 看 `sse connect: <url>` 与 HTTP 状态码；2. 确认后端接口为 `POST /api/ootd/chat/sse`，请求体 `{"imageUrl":"...","prompt":"..."}`；3. 用 curl 或 Postman 直连验证 |
| 「服务响应超时（120 秒无数据返回）」 | 后端处理过慢、SSE 未及时下发分片 | 1. Logcat 过滤 `OotdSse` 看是否有分片到达；2. 检查后端是否在 120s 内下发了至少一个 `data:` 帧；3. 确认后端 `complete()` 后正常关流 |

### 4.3 流式中途断开

| 现象 | 可能原因 | 排查步骤 |
| --- | --- | --- |
| 文本输出一半后停止，显示「分析完成」 | 后端 `complete()` 或收到 `[DONE]` 后正常关流 | 这是**正常现象**，不是错误。客户端按 EOF / `[DONE]` 判定成功，保留已渲染文本，不弹错误 |
| 文本输出一半后停止，显示错误卡 | 网络波动、后端异常断连 | 1. Logcat 过滤 `OotdSse` 看 `sse failed` 日志与异常类型；2. 若异常为 `unexpected end of stream` 且之前已收到文本，客户端会按成功处理（`isEndOfStreamAfterContent`）；3. 否则检查后端日志是否抛异常 |
| 文字卡在「AI 正在思考搭配建议…」无输出 | 后端首帧延迟过长、或请求体字段名不匹配 | 1. 确认后端 SSE 在收到请求后尽快下发首个 `data:` 帧；2. 检查请求体字段：`imageUrl`（TOS URL）、`prompt`（提问文本）；3. Logcat 看 `upload ok, presigned url = ...` 是否拿到了有效 URL |

### 4.4 真机访问不到本地后端

| 现象 | 可能原因 | 排查步骤 |
| --- | --- | --- |
| 手机浏览器也打不开 `http://<IP>:8080/` | IP 错误、网络不通、防火墙 | 1. 电脑 `ipconfig` 重新查 IPv4（换 WiFi 后 IP 会变）；2. 手机与电脑连同一 WiFi（不要一个连 5G 一个连 2.4G 且 AP 隔离）；3. 临时关闭 Windows 防火墙测试；4. 公司 WiFi 可能开启 AP 隔离，改用手机热点或家用 WiFi |
| 手机能 ping 通电脑但端口不通 | 防火墙未放行 8080 | 1. Windows 防火墙 → 高级设置 → 入站规则 → 新建 TCP 8080 允许规则；2. 或 `netsh advfirewall firewall add rule name="SpringBoot 8080" dir=in action=allow protocol=TCP localport=8080` |
| `adb reverse` 模式下 OOTD 仍连不上 | OOTD 不走 adb reverse，走局域网 IP | OOTD 模块固定走 `HOST_IP` 局域网地址，不使用 `127.0.0.1`。请按 1.3 节修改 `HOST_IP`，不要依赖 `adb reverse` |

---

## 五、Logcat 过滤标签速查

| 标签 | 用途 |
| --- | --- |
| `OotdSse` | SSE 连接、上传、分片接收、关闭、异常全链路日志 |
| `OotdImage` | 图片压缩、EXIF 旋正、相机输出 Uri 创建 |
| `OotdPage` | ViewModel 阶段切换（UPLOADING→STREAMING→DONE/FAILED）、失败原因 |

Android Studio Logcat 过滤：`tag:OotdSse | tag:OotdImage | tag:OotdPage`

---

## 六、关键代码位置

| 文件 | 说明 |
| --- | --- |
| `app/.../screens/ootd/OotdAnalyzeScreen.kt` | OOTD 副页面 UI + ViewModel（选图、压缩、上传、SSE、打字机） |
| `app/.../data/remote/ootd/OotdSseClient.kt` | 上传 + SSE 网络客户端（`HOST_IP` 在此修改） |
| `app/.../util/OotdImageUtils.kt` | 图片压缩、相机 Uri、权限判断 |
| `app/.../screens/ShareScreen.kt` | 分享主页「AI 穿搭」入口按钮 |
| `app/.../SkeletonApp.kt` | 路由注册：`ROUTE_OOTD_ANALYZE = "share/ootd"` |
| `app/src/main/AndroidManifest.xml` | INTERNET / CAMERA / READ_MEDIA_IMAGES / 明文 HTTP / FileProvider |
| `app/src/main/res/xml/network_security_config.xml` | 放行明文 HTTP（`base-config cleartextTrafficPermitted="true"`） |
