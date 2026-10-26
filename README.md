# CityGo

- `app/`：Android 客户端（根目录 Gradle 工程）。
- `citygo-web/`：Web 前端。
- `travel-guide-web/`：攻略 Web 前端。
- `backend/`：Spring Boot 后端（独立 Maven 工程），配置及启动方式见 [后端说明](backend/README.md)。
- `docs/`：项目文档。
- `design-preview/`：设计预览。

前后端在同一仓库管理，分别构建和启动。本机密钥、依赖缓存及构建产物不入库。
