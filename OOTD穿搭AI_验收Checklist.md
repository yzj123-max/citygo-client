# OOTD 穿搭 AI — USB 真机端到端验收 Checklist

> 模块：分享页「AI 穿搭」→ OotdAnalyzeScreen 副页面
> 联调模式：adb reverse（USB 端口转发）+ ServerConfig 动态地址（默认 `http://127.0.0.1:8080/`）
> 后端接口：`POST /api/ootd/upload`（图片上传）、`POST /api/ootd/chat/sse`（SSE 流式分析）
> 最近更新：2026-10-19

---

## 一、前置环境检查

| # | 检查项 | 操作 | 通过标准 | 结果 |
| --- | --- | --- | --- | --- |
| P1 | 后端服务启动 | 电脑启动 SpringBoot，监听 `127.0.0.1:8080` 或 `0.0.0.0:8080` | 浏览器访问 `http://127.0.0.1:8080/` 有响应 | ☐ |
| P2 | adb reverse 转发 | 电脑执行 `adb reverse tcp:8080 tcp:8080` | `adb reverse --list` 显示 `tcp:8080 tcp:8080` | ☐ |
| P3 | 真机 USB 调试 | 手机开启开发者模式 + USB 调试，连电脑 | `adb devices` 能看到设备 | ☐ |
| P4 | App 已安装 | Android Studio Run 或 adb install | 手机桌面出现 CityGo 图标 | ☐ |
| P5 | 后端地址正确 | 进入 OOTD 页，看底部「当前后端地址」 | 显示 `http://127.0.0.1:8080/`（或 ServerConfig 配置的地址） | ☐ |

---

## 二、入口与页面栈

| # | 检查项 | 操作 | 通过标准 | 结果 |
| --- | --- | --- | --- | --- |
| R1 | 分享页入口可见 | 登录 → 底部 Tab 切【分享】 | 顶部栏右侧有「✨ AI 穿搭」按钮 | ☐ |
| R2 | 点击进入副页面 | 点击「AI 穿搭」 | 进入「OOTD 穿搭 AI 分析」页，无底部导航栏 | ☐ |
| R3 | 返回不跳首页 | 点左上角返回箭头 | 回到【分享】主页，瀑布流位置和数据保留 | ☐ |
| R4 | 系统返回键 | 在 OOTD 页按系统返回键 | 回到【分享】主页，不退出 App | ☐ |

---

## 三、图片选择（相册 / 相机）

| # | 检查项 | 操作 | 通过标准 | 结果 |
| --- | --- | --- | --- | --- |
| I1 | 相册选图 | 点【相册】→ 选一张 JPG 穿搭图 | 原图预览正常显示，无拉伸变形 | ☐ |
| I2 | PNG 选图 | 相册选 PNG 图片 | 原图预览正常，分析时能正常压缩上传 | ☐ |
| I3 | 大图压缩 | 选一张 4000×3000 以上大图 | 上传阶段无 OOM，Logcat 显示压缩后字节数 < 500KB | ☐ |
| I4 | 相机拍照 | 点【拍照】→ 授予相机权限 → 拍一张 | 拍照成功，原图预览显示 | ☐ |
| I5 | 相机权限拒绝 | 首次点【拍照】→ 拒绝权限 | Toast「相机权限被拒绝，无法拍照」，不崩溃 | ☐ |
| I6 | 重新选图 | 选图后点【重新拍照】或【相册重选】 | 旧图被替换，状态重置 | ☐ |
| I7 | 损坏图片 | 选一张 0 字节或损坏图片 | 显示「图片处理失败：…」，不崩溃 | ☐ |

---

## 四、上传 → SSE 流式分析（核心链路）

| # | 检查项 | 操作 | 通过标准 | 结果 |
| --- | --- | --- | --- | --- |
| A1 | 上传中状态 | 选图后点【开始 AI 分析】 | 按钮显示「图片上传中…」+ 转圈，不可重复点击 | ☐ |
| A2 | 上传成功切流式 | 等待上传完成 | 按钮变「AI 正在分析…」，Logcat 出现 `upload ok, presigned url = ...` | ☐ |
| A3 | 打字机效果 | SSE 开始返回文本 | 结果卡片逐字输出分析文本，非一次性全部出现 | ☐ |
| A4 | 知识库引用 | 后端返回 reference 事件 | 页面下方出现「知识库引用（N 条）」标签列表 | ☐ |
| A5 | 流正常结束 | 后端下发 `[DONE]` 或关流 | 按钮变【重新分析】，结果卡显示「分析完成」，**不弹错误** | ☐ |
| A6 | 重新分析 | 分析完成后点【重新分析】 | 清空旧文本，重新走上传→流式流程 | ☐ |

---

## 五、异常分支

| # | 检查项 | 操作 | 通过标准 | 结果 |
| --- | --- | --- | --- | --- |
| E1 | 后端未启动 | 关闭后端 → 选图 → 开始分析 | 上传失败，红色错误卡提示「无法连接后端 http://127.0.0.1:8080/…」 | ☐ |
| E2 | 断网（上传阶段） | `adb shell svc wifi disable` → 选图 → 分析 | 上传阶段失败，显示网络异常卡，不崩溃 | ☐ |
| E3 | 断网（SSE 阶段） | 上传成功后立即断网 | 已收到文本保留，不弹「服务响应超时」；仅 onFailure 才弹错误 | ☐ |
| E4 | 分析中退出页面 | 流式分析中点返回 | 页面关闭，SSE 连接断开（Logcat 出现 `sse client closed by caller`），无内存泄漏 | ☐ |
| E5 | 重复进入页面 | 退出 OOTD → 重新进入 → 选图 → 分析 | 正常工作，无旧连接残留，无崩溃 | ☐ |
| E6 | 上传接口返回非 2xx | 后端模拟上传失败（如 500） | 显示「图片上传失败（HTTP 500）」，不进入 SSE 阶段 | ☐ |

---

## 六、资源释放与内存（Logcat 验证）

| # | 检查项 | 操作 | 通过标准 | 结果 |
| --- | --- | --- | --- | --- |
| M1 | SSE 连接释放 | 分析中退出页面 → Logcat 过滤 `OotdSse` | 出现 `sse client closed by caller`，无 `sse failed` 异常 | ☐ |
| M2 | Worker 线程退出 | 退出页面后查看线程 | OkHttp worker 线程被 cancel，无残留线程 | ☐ |
| M3 | deltaChannel 关闭 | 压缩失败 / 取消场景 → Logcat | 无 Channel 未关闭警告，无泄漏 | ☐ |
| M4 | OkHttpClient 复用 | 多次进出 OOTD 页 → Logcat | 仅一个共享 OkHttpClient 实例，无重复创建日志 | ☐ |

---

## 七、网络与权限

| # | 检查项 | 操作 | 通过标准 | 结果 |
| --- | --- | --- | --- | --- |
| N1 | 网络权限 | AndroidManifest 有 INTERNET | 编译通过，运行时网络请求正常 | ☐ |
| N2 | 相机权限 | Manifest 有 CAMERA | 拍照时能正常申请权限 | ☐ |
| N3 | 相册权限 | Manifest 有 READ_MEDIA_IMAGES / READ_EXTERNAL_STORAGE(maxSdk32) | Android 13+ 走 Photo Picker 免权限，Android 12 及以下正常申请 | ☐ |
| N4 | 明文 HTTP | network_security_config 放行 | `http://127.0.0.1:8080/` 请求不被拦截 | ☐ |
| N5 | ServerConfig 地址切换 | 在 App 内地址输入框改为局域网 IP → OOTD 页底部显示新地址 | 底部「当前后端地址」实时更新为新值 | ☐ |

---

## 八、Logcat 过滤命令

```
tag:OotdSse | tag:OotdImage | tag:OotdPage
```

关键日志关键词：
- 上传成功：`upload ok, presigned url =`
- SSE 连接：`sse connect: http://127.0.0.1:8080/api/ootd/chat/sse`
- SSE 关闭：`sse client closed by caller`
- 分片到达：`sse chunk received, len=`
- 异常：`sse failed:` / `upload failed http`
