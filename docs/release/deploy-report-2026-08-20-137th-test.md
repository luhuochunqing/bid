# 第 137 次测试环境部署报告 — 2026-08-20

## 部署环境

| 项目 | 值 |
|---|---|
| 环境 | **测试 (test)** |
| 目标主机 | `winbid-01` |
| 目标 IP | `172.16.38.78` |
| 用途 | 修复 AI 生成案例超大标书 OOM 导致入库 0 条（PR !2357） |
| 部署序号 | 第 137 次（测试） |
| 部署时间 | 2026-08-20 11:33 CST（backend 激活） |

## 部署概览

| 项目 | 值 |
|---|---|
| Release ID | `97a86d3a3-api8080` |
| 上一版本 Release | `31916139a`（2026-08-20 10:24 激活） |
| 基线 commit | `97a86d3a3`（本次打包的 release 源码） |
| 健康检查通过 | ✅（UP） |
| 部署结果 | ✅ 成功 |
| 回滚状态 | 未需要 |
| 新增 Flyway 迁移 | **无**（本次仅改 Java 代码） |

## 变更内容

- **PR !2357** `fix(case): 修复 AI 生成案例超大标书 OOM 导致入库 0 条`

### 根因（修 A 破 B）
修复 OBS 预签名 URL 二次编码（403）后，暴露整文件读堆问题：
- `RestTemplate.getForObject(url, byte[].class)` 把 401MB 标书**整体读进堆**，无大小防护。
- `fallbackExtract` 对二进制 `new String(content, UTF_8)` **二次分配**，堆峰值 ≈ 2× 文件 → `Java heap space OOM`。
- OBS（`obs-direct:`）是真实标书数据源，提取失败却回退占位文本 → 假"成功但入库 0 条"。

### 修复
1. `MarkItDownSidecarExtractor.extract` 入口加**大小防护**（默认 30MB，与 sidecar `SIDECAR_MAX_UPLOAD_MB` 一致），超限抛 `DocInsightException`（不在 `@Retryable retryFor` 内不重试）。
2. `ProjectClosedEventListener.loadBidFileMarkdown`：OBS 标书**提取失败/超限 rethrow 走失败通知**，禁止回退占位文本假成功。

### 复测预期（针对项目 1350）
- 标书 ≤30MB → 正常提取、正常入库（通知显示 N>0）。
- 标书 ≥30MB（如原本 401MB）→ 受控报错，通知原因含"超过转换上限 30MB"，**不再假成功、入库 0 条**。

## 验证结果

### Flyway
| 检查项 | 结果 |
|---|---|
| validate（flyway-repair-runner） | ✅ VALIDATE OK（259 迁移 checksum 全匹配） |
| DB 已应用最新版本 | ✅ V1198 |
| 本次迁移变更 | 无（增量 commit 不含 `migration-mysql/` 改动） |

### 部署后 Smoke（经 Nginx 8080）
| 检查项 | 结果 |
|---|---|
| 后端 health | ✅ UP |
| readiness | ✅ 200 |
| deployed-release.json | ✅ `97a86d3a3-api8080` |
| OBS 直传启用 | ✅ `.upload(` 调用数=2 |
| 前端入口与 release 一致 | ✅ `src="/assets/index-8AyZ-yJr.js` |
| `/actuator/health` | ✅ 200 |
| `/actuator/health/readiness` | ✅ 200 |
| `POST /api/auth/login`（空密码） | ✅ 400（路由正常） |
| `GET /api/projects`（未认证） | ✅ 403（权限守卫） |
| 前端根路径 `/` | ✅ 200 |

## 部署步骤

1. 环境门禁确认：测试环境 172.16.38.78（用户通过 AskUserQuestion 确认）
2. 合并 PR !2357 到 Gitee main（squash，`97a86d3a3`）
3. 早操三连：dev-env + sync-env + check-git-wrapper ✅
4. 基线确认：HEAD=97a86d3a3，服务器现状（部署 31916139a，健康 UP）
5. Flyway 预检 3 步：validate OK + DB 版本 V1198 + 增量迁移变更=空
6. `ENV=test bash scripts/release/deploy-test.sh` 一键部署（固化 OBS=true + 同源 API）
7. 部署后验证：健康检查 UP + 前端一致 + OBS 检测 + Smoke

## GitHub 镜像状态

- ⚠️ 部署前 GitHub main 落后 Gitee main 1 commit（含 PR !2357 squash 合并）。待收尾时 `sync-to-github.sh` 同步。

## 配置清理检查

- `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`：**保留**（技能明确记录"3 次决定保留"项，非临时 DEBUG/TRACE）。

## 回滚方案（未启用）

```bash
ssh jetty@172.16.38.78 'sudo systemctl stop xiyu-bid-backend && \
  sudo ln -sfn /opt/xiyu-bid/releases/31916139a/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar && \
  sudo systemctl start xiyu-bid-backend'
```

## 部署确认清单

- [x] 环境门禁确认（test / 172.16.38.78）
- [x] PR !2357 squash 合并到 main
- [x] 早操三连 + 基线确认
- [x] Flyway 预检 3 步（validate + DB 对比 + 增量迁移=空）
- [x] 本地打包 + 产物校验（同源 API + OBS + 前端入口）
- [x] backend 部署 + 健康检查（UP）
- [x] API Smoke + 前端验证（全部通过）
- [x] OBS 直传启用校验（.upload( 数=2）
- [x] 配置清理检查（SHOW_DETAILS=always 保留）
- [ ] GitHub 镜像同步（待收尾）

## 待办

- [ ] 复测项目 1350：登录测试环境 → 点击【AI自动生成案例】→ 验证入库或失败通知
- [ ] 收尾时 `sync-to-github.sh` 同步 GitHub 镜像