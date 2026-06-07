# Production Release Pipeline

## 目标

把 `main` 合并后的交付链路收敛成一条明确闭环：

1. 合并到 `main`
2. 自动执行更严格的 post-merge gate
3. 生成正式发布产物
4. 通过 SSH 下发到生产机并激活
5. 自动执行生产 smoke 验活
6. 输出可归档的报告到 `docs/reports/`

## 工作流

主入口：`.github/workflows/main-release.yml`

执行顺序：

1. `Post-Merge Gate`
   - 复用 `Staging Gate`
   - 运行发布演练、自动 UAT、E2E 门禁、备份恢复演练、签字包产物
2. `Build Release Artifact`
   - 调用 `scripts/release/package-release.sh`
   - 生成包含前端 `dist`、后端 `app.jar`、元数据的压缩包
3. `Deploy Production`
   - 通过 SSH 上传发布包
   - 在目标机执行 `scripts/release/remote-deploy.sh`
   - 完成备份、静态资源切换、后端重启和健康检查
4. `Production Smoke Verification`
   - 调用 `node scripts/release/run-prod-smoke.mjs`
   - 执行线上只读验活并上传报告

## GitHub 环境变量与 Secrets

建议都配置在 GitHub `production` environment。

### 必填 secrets

- `PROD_SSH_HOST`
- `PROD_SSH_PORT`
- `PROD_SSH_USER`
- `PROD_SSH_KEY`
- `PROD_SMOKE_USERNAME`
- `PROD_SMOKE_PASSWORD`

### 选填 secrets

- `PROD_DB_BACKUP_COMMAND`
  - 生产机上的数据库备份命令
  - 例如：`/opt/xiyu/bin/backup-prod-db.sh`

### 必填 vars

- `PROD_APP_ROOT`
  - 例如：`/opt/xiyu-bid`
- `PROD_FRONTEND_PUBLIC_DIR`
  - 例如：`/srv/www/xiyu-bid`
- `PROD_BACKEND_SERVICE_NAME`
  - 例如：`xiyu-bid-backend`
- `PRODUCTION_API_BASE_URL`
  - 例如：`https://bid-api.example.com`
- `PRODUCTION_WEB_BASE_URL`
  - 例如：`https://bid.example.com`

### 选填 vars

- `PROD_RELEASES_DIR`
- `PROD_BACKEND_RUNTIME_DIR`
- `PROD_BACKEND_JAR_PATH`
- `PROD_DEPLOYED_RELEASE_RECORD`
  - 默认为 `$PROD_APP_ROOT/deployed-release.json`
  - 记录当前线上真实生效的 release、运行路径和打包元数据
- `PROD_BACKEND_PORT`
- `PROD_HEALTHCHECK_URL`
- `PROD_SYSTEMCTL_SUDO`
  - `true` 时用 `sudo systemctl`
- `PROD_POST_DEPLOY_COMMAND`
  - 例如重载 Nginx、清理旧版本等
- `PROD_PROMETHEUS_MODE`
  - `protected`（默认）、`public`、`skip`

## 生产验活口径

`scripts/release/run-prod-smoke.mjs` 当前采用只读 smoke 策略，不在线上创建测试数据。

P0 通过项：

- 前端首页 200
- `/actuator/health` 返回 `UP`
- Prometheus 暴露策略符合预期
- smoke 账号可登录
- `/api/auth/me` 正常
- Dashboard 概览可读
- 标讯列表可读
- 项目列表可读
- 资质、案例、模板列表可读
- 费用、BAR 资产列表可读

Go / No-Go 口径：

- 任一 P0 失败即 `NO-GO`
- 脚本退出码非 0 即视为验活失败
- 报告文件输出到 `docs/reports/prod-smoke-report-*.{json,md}`

## 数据库部署路径

生产数据库统一为 MySQL 8.0，后端 profile 使用 `prod,mysql`，Flyway 迁移路径为 `classpath:db/migration-mysql`。启用时必须设置：

- `DB_ENGINE=mysql`
- `SPRING_PROFILES_ACTIVE=prod,mysql`
- `DB_HOST/DB_PORT/DB_NAME/DB_USERNAME/DB_PASSWORD`
- 发布脚本兼容历史变量 `DB_USER`；未设置时会从 `DB_USERNAME` 读取
- 可选 `DB_URL`，用于覆盖完整 JDBC URL

当前 baseline 为 `B73__full_schema_baseline.sql`。旧数据库路径已退出发布支持范围，生产、演练、备份和恢复只按 MySQL 8.0 口径执行。

本地发布演练可用：

```bash
DB_ENGINE=mysql bash scripts/release/rehearse-release.sh
```

## 目标机目录约定

`remote-deploy.sh` 默认假设：

- 发布根目录：`$PROD_APP_ROOT`
- 历史版本：`$PROD_APP_ROOT/releases/<release-id>`
- 后端运行目录：`$PROD_APP_ROOT/shared/backend`
- 当前部署记录：`$PROD_APP_ROOT/deployed-release.json`

这里的发布模型要特别注意：

- `releases/<release-id>` 用于保存历史发布包解压结果
- 前端真实生效路径是 `PROD_FRONTEND_PUBLIC_DIR`
- 后端真实生效路径是 `PROD_BACKEND_JAR_PATH`
- 当前线上版本以 `deployed-release.json` 为准，而不是软链指针

这样可以避免出现“保留了 `current` 软链，但运行时并不真正依赖它”的误导语义。

发布包默认包含：

- `frontend/`
- `backend/app.jar`
- `release-metadata.json`

## 远端激活脚本约束

`scripts/release/remote-deploy.sh` 的职责：

- 解压上传的 release archive
- 可选执行数据库备份命令
- 切换前端静态资源目录
- 更新后端 jar
- 写入当前部署记录
- 重启 systemd 服务
- 等待健康检查通过

它还会做一层路径约束校验：

- `FRONTEND_PUBLIC_DIR` 不能放在 `RELEASES_DIR` 下面
- `BACKEND_RUNTIME_DIR` / `BACKEND_JAR_PATH` 不能放在 `RELEASES_DIR` 下面

这样可以避免把“历史版本归档目录”和“当前运行目录”混成一套路径语义。

它不负责：

- 生成后端环境变量文件
- 初始化服务器基础设施
- 配置 Nginx、systemd、MySQL、Redis

这些属于一次性环境建设，应在上线前完成。
