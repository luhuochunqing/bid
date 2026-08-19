# 第 133 次测试环境部署报告 — 2026-08-19

## 部署环境

| 项目 | 值 |
|---|---|
| 环境 | **测试 (test)** |
| 目标主机 | `winbid-01` |
| 目标 IP | `172.16.38.78` |
| 用途 | 数据分析模块 TypeError + 429 限流修复上线 |
| 部署序号 | 第 133 次（测试） |
| 部署时间 | 2026-08-19 16:13 CST（backend 激活） |

## 部署概览

| 项目 | 值 |
|---|---|
| Release ID | `ae7e7a3f6-api8080` |
| 上一版本 Release | `84bc63278`（2026-08-19 15:46 激活） |
| 基线 commit | `ae7e7a3f6`（agent/trae/fix-analytics-treeselect-has） |
| 健康检查通过 | ✅（UP） |
| 部署结果 | ✅ 成功 |
| 回滚状态 | 未需要 |
| 新增 Flyway 迁移 | 无 |

## 变更内容

### 1. 前端：TreeSelectNode.vue `selectedSet` 未定义（ErrorBoundary TypeError）

**报错**：`[ErrorBoundary] Unhandled render error: TypeError: Cannot read properties of undefined (reading 'has')`

**根因**：数据分析模块 TreeSelect 组件树中，`TreeSelectNode.vue` 模板直接引用 `selectedSet`，但该值来自父组件 `TreeSelectDropdown.vue` 通过 `provide('treeSelectContext', ...)` 注入。Node 组件只取了 `tree`（context）但未解构 `selectedSet`，模板中 `selectedSet.has(...)` 为 undefined 访问 → 渲染抛错 → ErrorBoundary 兜底。

**修复**：`src/views/data-analysis/components/shared/TreeSelectNode.vue` 增加从注入上下文解构：

```javascript
const tree = inject('treeSelectContext')
if (!tree) throw new Error('TreeSelectNode 必须在 TreeSelectDropdown 内使用')
const { selectedSet } = tree
```

### 2. 后端：默认限流阈值 100 → 200 次/60s（429 缓解）

**报错**：`/api/analytics/trends/enhanced ... 429`（Rate limit exceeded: key=user:06234）

**根因**：数据分析页面（趋势图/筛选联动）在页面加载与筛选切换时会高频请求 analytics 接口，默认 100 次/60s 的匿名/默认限流阈值被快速打满，触发 429。

**修复**：`backend/src/main/resources/application.yml` 默认限流阈值提升至 200 次/60s：

```yaml
rate:
  limit:
    default:
      max: ${RATE_LIMIT_DEFAULT_MAX:200}
      window-seconds: ${RATE_LIMIT_DEFAULT_WINDOW:60}
```

测试环境 `/etc/xiyu-bid/backend.env` 无 `RATE_LIMIT_DEFAULT_MAX` 覆盖，默认 200 生效（仅 `RATE_LIMIT_LOGIN_MAX=20` 登录限流独立保留）。

## 验证结果

| 检查项 | 结果 |
|---|---|
| 后端 health（127.0.0.1:18080） | ✅ UP（consecutive 3/3，80 次尝试） |
| 前端一致性 | ✅ `src="/assets/index-BnvO5xM1.js"` 与 release 一致 |
| 线上 bundle 校验 | ✅ index-BnvO5xM1.js md5=`737f275d…` 与本地修复后构建产物完全一致 |
| OBS 直传 | ✅ 已启用（Detail chunk `.upload(` 数=2） |
| 部署记录 | ✅ `/opt/xiyu-bid/deployed-release.json` = `ae7e7a3f6-api8080` |

### 429 限流修复线上实证（journalctl）

- **部署前**（15:52:26，旧进程）：`Rate limit exceeded: key=user:06234, path=/api/analytics/trends/enhanced, retryAfter=60s` ← 用户遇报错的根因
- **部署后**（16:13~16:15，新进程 16531）：真实用户（172.16.96.136 / 172.16.103.113）连续多次访问 `/api/analytics/trends/enhanced` 全部 `status=200`，**无 429**

## 本地验证（部署前）

- `npm run check:front-data-boundaries` ✅
- `npm run check:doc-governance` ✅
- `npm run check:line-budgets` ✅
- `npm run build` ✅
- `mvn test -Dtest=RateLimitFilterTest,RateLimitResponseFactoryTest` ✅
- 容器测试 `FlywayMysqlContainerTest` ✅（Spring 全量上下文装配正常）
- `mvn clean package` ✅；jar 内 Flyway 迁移版本无重复

## 部署步骤

1. 早操三连：dev-env + sync-env + check-git-wrapper ✅
2. 基线确认：分支 `agent/trae/fix-analytics-treeselect-has`，HEAD `ae7e7a3f6`，commit 已 push
3. 本地打包：`ENV=test bash scripts/release/deploy-test.sh`（OBS=true，同源 API）
4. 上传 + remote-deploy.sh（SYSTEMCTL_SUDO=true，含 DB backup）
5. 前端新 bundle `index-BnvO5xM1.js` 激活，健康检查通过

> ⚠️ 本次部署的 Flyway validate 预检跳过（flyway-repair-runner.sh 未上传到 incoming 目录），但本次无 DB 迁移，无 checksum 风险。

## 回滚方案（未启用）

```bash
ssh jetty@172.16.38.78 'sudo systemctl stop xiyu-bid-backend && \
  sudo ln -sfn /opt/xiyu-bid/releases/84bc63278/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar && \
  sudo systemctl start xiyu-bid-backend'
```

## 部署确认清单

- [x] 环境门禁确认（test / 172.16.38.78）
- [x] 早操三连 + 基线确认（ae7e7a3f6）
- [x] 本地验证（前端 checks/build + 后端限流测试 + 容器测试）
- [x] 本地打包 + 产物校验（OBS 直传 + 前端入口）
- [x] backend 部署 + 健康检查（UP）
- [x] 前端一致性（bundle md5 与本地一致）
- [x] 429 修复线上实证（部署后 analytics 接口全部 200，无 429）
- [x] 部署报告生成（本文件）
