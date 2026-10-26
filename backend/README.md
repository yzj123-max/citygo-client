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
