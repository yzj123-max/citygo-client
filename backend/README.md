# CityGo 后端

从 C:\login\login-demo 复制到此目录，原项目保留。此目录使用根仓库的 Git，不包含原仓库的提交历史。

## 环境与启动

需要 JDK 17、MySQL，以及功能使用到的 Redis、高德和火山引擎服务。

在本目录执行：

```powershell
# 新克隆项目时先复制配置；当前机器已保留原配置，无需覆盖。
Copy-Item application.properties.example src/main/resources/application.properties
# 配置 DB_PASSWORD、ARK_API_KEY、TOS_* 等环境变量，按实际情况修改数据库地址。
# JWT_SECRET 必须是至少 32 字节的私有随机字符串，并在各次启动、各服务实例间保持一致。
$env:JWT_SECRET = '<填写自己的至少32字节随机密钥>'
.\mvnw.cmd spring-boot:run
```

JWT 密钥改动会使旧登录令牌失效，需要重新登录。原项目中的硬编码密钥未随源码迁移。

原来的 application.yml 和 application.properties 已复制供本机使用，均被 Git 忽略；不要把真实密码、密钥提交到仓库。Spring Boot 同时存在这两份文件时，properties 中同名配置优先。

## 构建与测试

```powershell
.\mvnw.cmd -DskipTests package
.\mvnw.cmd test
```

完整 Spring 上下文测试需要可用的数据库及运行配置。src/main/resources/db/trip-sync.sql 仅包含行程同步表，不能替代整个系统的数据库初始化；迁移至新机器时还需要原数据库的建表脚本或备份。

后端独立使用 Maven 构建；仓库根目录的 Gradle 仍用于安卓端。现有前端服务地址不会因源码目录搬迁自动改变，联调时确认它们指向本后端。

## AI 攻略接入高德 MCP

`/travel/generate` 和 `/travel/generateTask` 共用此功能；安卓端的提交、轮询和攻略格式保持兼容。调用链为：安卓端 → 本后端 → 方舟模型工具调用 → 高德 MCP → 模型输出攻略。安卓地图和实时导航仍使用原 SDK。

高德官方 Streamable HTTP 地址为 `https://mcp.amap.com/mcp`。后端通过 initialize、tools/list、tools/call 建立真实 MCP 会话，支持 JSON/SSE 响应和会话清理。工具列表从服务端发现，限于景点/地点检索、天气、地理编码、路线与距离等只读查询，不向模型开放打车等其他操作。每份攻略最多 3 轮工具调用、8 次查询，单次 MCP 请求最长 20 秒；模型完成攻略还需额外时间。

启用方式（在启动后端的同一个 PowerShell 窗口执行；IDE 启动时填写到后端 Run Configuration 的环境变量）：

```powershell
cd 'C:\city go\backend'
$env:AMAP_MCP_ENABLED = 'true'
$env:AMAP_MCP_KEY = '<填写高德 Web 服务 Key>'
# agent.endpoint 必须对应支持 tools / tool_choice 的方舟模型。
# 保留已配置的数据库、方舟、TOS、JWT_SECRET 等运行环境。
.\mvnw.cmd spring-boot:run
```

默认关闭 MCP，未开启时保留原生成逻辑；显式开启后，缺少 Key、握手失败或模型不支持工具调用会使任务失败，不会伪装成已查询高德。单个查询失败会作为错误结果交给模型，提示其说明信息暂无法核实。模型单次读取超时为 45 秒，每次新查询/模型请求前检查 220 秒预算（已发起请求仍受各自超时限制）。天气仅能使用工具结果覆盖的日期；高德点位不等于宠物友好认证，也不能据此编造票价。

不要使用 Android SDK Key 代替 Web 服务 Key，不要把 Key 写到前端或提交 Git。`amap.mcp.enabled` 和 `amap.mcp.api-key` 也可以配置在本机被忽略的 application.properties 中；如果写了固定值，它们优先于上面通过占位符引用的环境变量。

本机已有 application.properties 无需覆盖：新增配置在 Java 注入处也支持上述环境变量。请确保启动的是本仓库 `backend/` 的新代码；继续运行 `C:\login\login-demo` 或旧服务器不会自动获得此功能，安卓端后端地址也应指向新实例。

验证：在 App 生成一份攻略，后端应出现 `AI攻略高德 MCP 查询` 和流程完成日志。只有真实查询成功并结合模型返回结果，才能确认在线接入完成。自动协议测试不需要真实密钥或数据库：

```powershell
.\mvnw.cmd -Dtest=AmapMcpTravelTest test
```

参考：[高德接入文档](https://developer.amap.com/api/mcp-server/gettingstarted)、[创建 Web 服务 Key](https://developer.amap.com/api/mcp-server/create-project-and-key)、[方舟工具调用](https://docs.volcengine.com/docs/ark/function-calling?lang=en)。
