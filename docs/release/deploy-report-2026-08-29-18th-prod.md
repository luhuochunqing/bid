# 第 18 次生产环境部署报告 — 2026-08-29

## 部署环境

| 项目 | 值 |
|---|---|
| 环境 | **生产 (prod)** |
| 目标主机 | `winbid-01` |
| 目标 IP | `172.16.10.149` |
| 用途 | 正式环境、对外服务 |
| 部署序号 | 第 18 次（生产） |
| 部署时间 | 2026-08-29 20:03 CST |

## 部署概览

| 项目 | 值 |
|---|---|
| Release ID | `d12f173bd` |
| 上一版本 Release | `8f568d5cc`（2026-08-07 19:38 CST，第 17 次生产部署） |
| 基线 commit | `d12f173bd00ecb517290c252b0292e5e9f9d36de`（origin/main） |
| 激活时间 | 2026-08-29T20:03 CST |
| 部署结果 | ✅ 成功 |
| 回滚状态 | 未需要（就绪） |
| 增量 commit | 817 个（22 天跨度） |
| 新增 Flyway 迁移 | 20 个（V1185 ~ V1204，全部 success=1） |
| 健康检查 | 连续 3 次成功探测通过（HTTP 200 UP，Qwen3.7-max / MySQL / Redis / Sidecar 全部就绪） |
| Smoke 测试 | 8 项通过（无 schema 错误，无 CRM token 回归，Headless 模式正常） |
| GitHub 镜像 | ✅ 已同步（两边 main 完全一致） |
| 前端资源保留 | ✅ 已保留上一版本 `8f568d5cc` 旧 assets，属主统一为 jetty |

## 基线信息

- 工作树：`/Users/user/xiyu/worktrees/trae`
- 锚点分支：`main`
- 工作区状态：干净，HEAD = origin/main = `d12f173bd00ecb517290c252b0292e5e9f9d36de`
- 部署前 Gitee 与 GitHub 镜像完成同步

## 增量改动（8f568d5cc → d12f173bd）

### 核心功能与重大更新

1. **AI 评分标准解析抽屉架构演进 (V3)**：
   - 提取预判 + 实际打分双阶段闭环架构
   - 强化并列能力评分项穿插识别、100分闭环质量门禁
   - 无表格线多行文本权重精准对齐，防止相邻评分项权重错位
   - 过滤推荐成交候选人等程序条款，修复价格分归一化稀释
2. **商机预测与项目管理能力增强**：
   - 竞争对手情况表格改造与字段标准化
   - 商机预测时间排版优化与提醒日志系统
   - 标讯创建事件总线对接
3. **数据分析模块全面重构 (M0~M4)**：
   - M0~M4 指标口径统一、趋势分析与竞品分析完善
   - 客户类型饼图与图表时序渲染优化
4. **性能与稳定性加固**：
   - 业绩合订本导出 OOM 修复与参数优化
   - Flyway 容器测试门禁与字符集规范对齐（V1198 统一 collation）

### 新增 Flyway 迁移（20 个）

| 版本号 | 描述 | 类型 | 执行耗时 | 状态 |
|---|---|---|---|---|
| V1185 | create tender event logs | SQL | 45ms | success=1 ✅ |
| V1186 | fix tender event logs status enum | SQL | 61ms | success=1 ✅ |
| V1187 | create score parse tables | SQL | 115ms | success=1 ✅ |
| V1188 | add contract amount to performance record | SQL | 20ms | success=1 ✅ |
| V1189 | add analytics dashboard permission to global roles | SQL | 6ms | success=1 ✅ |
| V1190 | score parse spend guard columns | SQL | 96ms | success=1 ✅ |
| V1191 | eval form field label update and gap text deprecation | SQL | 26ms | success=1 ✅ |
| V1192 | add knowledge case dedup unique index | SQL | 18ms | success=1 ✅ |
| V1193 | add-skipped-chunks | SQL | 63ms | success=1 ✅ |
| V1194 | add score item to case reference | SQL | 46ms | success=1 ✅ |
| V1195 | add original total weight | SQL | 67ms | success=1 ✅ |
| V1196 | extend compliance check results for quality | SQL | 95ms | success=1 ✅ |
| V1197 | fix compliance check status enum | SQL | 63ms | success=1 ✅ |
| V1198 | unify legacy tables collation utf8mb4 0900 ai ci | SQL | 7999ms | success=1 ✅ |
| V1199 | knowledge case scoring point id dedup | SQL | 75ms | success=1 ✅ |
| V1200 | add opportunity prediction reminder log | SQL | 31ms | success=1 ✅ |
| V1201 | add contact nature to tenders | SQL | 47ms | success=1 ✅ |
| V1202 | competitor status unique seed | SQL | 58ms | success=1 ✅ |
| V1203 | add competitor permissions | SQL | 7ms | success=1 ✅ |
| V1204 | add score task run summary | SQL | 52ms | success=1 ✅ |

## Flyway 预检 3 步法

1. **Step 1: Flyway validate（部署前）**：
   - 265 个迁移全部校验，历史 245 个迁移 checksum 100% 匹配。
2. **Step 2: DB 已应用版本对比**：
   - 部署前 DB 最新版本：1184
   - 源码最新版本：1204（待应用 20 个）
3. **Step 3: remote-deploy 内置校验与执行**：
   - 服务启动时 Flyway 自动增量执行 V1185~V1204，全部成功应用。

## 部署步骤与产物校验

### 1. 本地打包与构建门禁

```bash
ENV=prod RELEASE_ID="d12f173bd" VITE_API_BASE_URL="" VITE_OBS_ENABLED=true \
  VITE_SENTRY_DSN="https://afe598346bea591afeabcefe91562d9b@o4511652658937856.ingest.us.sentry.io/4511652674076672" \
  VITE_SENTRY_ENVIRONMENT="production" \
  VITE_SENTRY_TRACES_SAMPLE_RATE="0.1" \
  bash scripts/release/package-release.sh
```

| 门禁项 | 结果 |
|---|---|
| 前端 build:api（同源 baseURL=""） | ✅ 通过 |
| 前端无 dev API 地址 (`check:frontend-api-base`) | ✅ 通过 |
| 容器测试门禁 (`FlywayMysqlContainerTest`) | ✅ 通过（Spring 全量上下文装配正常） |
| Jar 内 Flyway 迁移版本无重复 | ✅ 通过 |
| OBS 直传启用校验（Detail chunk `.upload(` 调用数=2） | ✅ 通过 |
| 前端 index.html 入口 | `assets/index-BKmEDlAP.js` ✅ |

### 2. 部署与服务激活

```bash
SKIP_SESSION_GATE=1 ENV=prod bash scripts/release/deploy-prod.sh d12f173bd
```

- 生产数据库备份：`/opt/xiyu-bid/db-backups/winbid-d12f173bd-*.sql.gz` ✅
- AI Provider 守卫：`AI_PROVIDER=custom` (Qwen3.7-max) ✅
- 前端全层级属主校正与原子替换：✅
- 后端平滑重启：`active (running) since Sat 2026-08-29 20:03:12 CST`
- 连续健康探测：连续 3 次成功，服务健康就绪 ✅

### 3. 前端资源保留（防跨版本 404）

```bash
sudo cp -rn /opt/xiyu-bid/releases/8f568d5cc/frontend/assets/. /srv/www/xiyu-bid/assets/
sudo chown -R jetty:jetty /srv/www/xiyu-bid
```
- ✅ 已保留上一版本 `8f568d5cc` 旧 assets，防止线上活跃用户的旧前端 chunk 404。

## 验证结果

### 健康检查明细 (`http://127.0.0.1:18080/actuator/health`)

```json
{
  "status": "UP",
  "components": {
    "aiProvider": {
      "status": "UP",
      "details": {
        "status": "configured",
        "provider": "custom",
        "model": "qwen3.7-max",
        "baseUrl": "https://ai-tech.ehsy.com/...",
        "apiKeyConfigured": true
      }
    },
    "db": {
      "status": "UP",
      "details": {
        "database": "MySQL",
        "validationQuery": "isValid()"
      }
    },
    "diskSpace": {
      "status": "UP",
      "details": {
        "free": 83656392704,
        "path": "/opt/xiyu-bid/shared/backend/."
      }
    },
    "jwt": {
      "status": "UP",
      "details": {
        "algorithm": "HMAC-SHA256"
      }
    },
    "redis": {
      "status": "UP",
      "details": {
        "version": "6.2.19"
      }
    },
    "sidecar": {
      "status": "UP",
      "details": {
        "url": "http://localhost:8000",
        "status": "reachable"
      }
    }
  },
  "groups": ["liveness", "readiness"]
}
```

### Smoke 测试结果

| 检查项 | 结果 |
|---|---|
| P0: Health check (`/actuator/health`) | ✅ UP (200) |
| P0: Headless Mode (CO-438) | ✅ `-Djava.awt.headless=true` 启用，无 fontconfig 报错 |
| P1: Log Schema Check | ✅ 日志无 schema 错误 |
| P1: CRM 集成日志扫描 | ✅ 无 Token/配置回归 |
| 前端入口一致性 | ✅ `index-BKmEDlAP.js` 匹配 |
| Nginx 反向代理连通性 | ✅ HTTP 200 OK |

## GitHub 镜像同步

- 执行命令：`bash scripts/sync-to-github.sh`
- 同步范围：`83e8f57d6..d12f173bd`
- 结果：✅ Gitee 与 GitHub main 分支完全一致。

## 回滚方案（备用）

如遇紧急情况，可通过以下命令回滚到上一个稳定版本 `8f568d5cc`：

```bash
# 1. 停止后端服务
sudo systemctl stop xiyu-bid-backend

# 2. 恢复上一版本 jar
cp /opt/xiyu-bid/releases/8f568d5cc/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar

# 3. 恢复上一版本前端
sudo rm -rf /srv/www/xiyu-bid/*
sudo cp -r /opt/xiyu-bid/releases/8f568d5cc/frontend/* /srv/www/xiyu-bid/
sudo chown -R jetty:jetty /srv/www/xiyu-bid

# 4. 启动后端服务
sudo systemctl start xiyu-bid-backend

# 5. 健康检查确认
curl -fsS http://127.0.0.1:18080/actuator/health
```

---
**报告编写完成，本次生产环境升级成功！**
