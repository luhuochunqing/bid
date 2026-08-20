# 第 136 次测试环境部署报告 — 2026-08-20

## 部署环境

| 项目 | 值 |
|---|---|
| 环境 | **测试 (test)** |
| 目标主机 | `winbid-01` |
| 目标 IP | `172.16.38.78` |
| 用途 | 数据分析区域统计 collation 统一修复（V1198）+ 区域筛选归属省份归一化 |
| 部署序号 | 第 136 次（测试） |
| 部署时间 | 2026-08-20 10:24 CST（backend 激活） |

## 部署概览

| 项目 | 值 |
|---|---|
| Release ID | `31916139a` |
| 上一版本 Release | `c326ba01d`（2026-08-19 22:57 激活） |
| 基线 commit | `31916139a`（本次打包的 release 源码） |
| 健康检查通过 | ✅（UP） |
| 部署结果 | ✅ 成功 |
| 回滚状态 | 未需要 |
| 新增 Flyway 迁移 | **是：V1198**（全库 collation 统一为 utf8mb4_0900_ai_ci） |

## 变更内容

### 1. 多维度趋势分析区域统计 collation 统一修复（V1198，本次新增迁移）

**根因**（回放自 commit d1cbe8400）：
- `coalesce(nullif(pid.headquartersLocation,''), t.region)` 跨表 fallback 时，`project_initiation_details`（新表 utf8mb4_0900_ai_ci）与 `tenders`/`projects`（历史表 utf8mb4_unicode_ci）collation 不一致。
- MySQL 对两个无超集关系 collation 的列做 COALESCE 时，结果退化为 `utf8mb4_bin + NONE`，再与默认 collation（utf8mb4_0900_ai_ci）的 LIKE 字面量比较即报 `ERROR 1267`（数据分析「多维度趋势-区域统计」500）。

**迁移**：`V1198__unify_legacy_tables_collation_utf8mb4_0900_ai_ci.sql`
- 存储过程 `p_unify_table_collation()` 扫描 `information_schema`，把所有 `TABLE_COLLATION <> 'utf8mb4_0900_ai_ci'` 的基础表统一 `CONVERT`。
- 全库 141 张 utf8mb4_unicode_ci 表，总数据量约 51MB，单表重建秒级；幂等（已对齐表不在游标内）。
- 回滚：`db/rollback/migration-mysql/U1198_*.sql`。

**配套回归修复**（本次部署前置，未改动已发布迁移）：
- `FlywayMysqlContainerTest` 因 V1198 把 roles 表 CONVERT 为 utf8mb4_0900_ai_ci，与 V1092 临时表硬编码 `COLLATE utf8mb4_unicode_ci` 的 JOIN 冲突，补临时对齐语句，重放校验通过。

### 2. 数据分析区域筛选归属省份归一化（PR !2350）

- `31916139a`：多维度趋势分析区域筛选改用总部所在地省份归一化口径。

## 验证结果

| 检查项 | 结果 |
|---|---|
| Flyway 预检（flyway-repair-runner.sh validate） | ✅ VALIDATE OK |
| 新增迁移动态 | ✅ V1198 已应用（全库表 collation 统一为 utf8mb4_0900_ai_ci） |
| 后端 health（127.0.0.1:18080） | ✅ UP |
| readiness | ✅ HTTP 200 |
| jar 内 Flyway 迁移版本 | ✅ 无重复 |
| OBS 直传 | ✅ 已启用（obsEnabled=true） |
| 前端一致性 | ✅ `src="/assets/index-8AyZ-yJr.js"` 与 release 一致 |
| 部署记录 | ✅ `/opt/xiyu-bid/deployed-release.json` = `31916139a` |
| 上一版本 assets 保留 | ✅ 已从历史 releases cp -rn 保留（合计 528 个，旧 hash 资源抽查返回 200） |

### API Smoke（经 Nginx 代理）

| 检查项 | 结果 |
|---|---|
| 前端根路径 `/` | ✅ HTTP 200 |
| 前端入口 JS | ✅ `assets/index-8AyZ-yJr.js` 可访问 |
| 旧缓存 hash 资源 | ✅ 抽查 5 个历史文件均 HTTP 200（防 404） |

## 部署步骤

1. 环境门禁确认：**测试环境 172.16.38.78**（用户确认）
2. 早操三连：dev-env + sync-env + check-git-wrapper ✅
3. 基线确认 + 服务器现状 + 增量 commit 检查 ✅
4. Flyway 预检：validate + DB 版本对比（识别 V1198 及 V1092 collation 冲突）
5. 修复 `FlywayMysqlContainerTest` collation 回归（已发布 V1092 不动，测试侧对齐）
6. 本地打包 + 产物校验（OBS obsEnabled + jar 内迁移去重 + 前端入口）
7. 上传 scp + remote-deploy.sh（SYSTEMCTL_SUDO=true，含 DB backup）
8. 健康检查 UP + 迁移应用验证（V1198 生效）+ Smoke 测试
9. 前端资源保留：从历史 releases 目录 cp -rn 补回旧 hash 资源，清理 `._*` 元数据
10. 配置清理检查：`backend.env` 无临时 DEBUG/TRACE 配置（仅 JAVA_OPTS=-Xmx2g）

## GitHub 镜像状态

- ✅ 已执行 `sync-to-github.sh`：Gitee main 与 GitHub main 完全一致（75e0b415a）

## 回滚方案（未启用）

```bash
ssh jetty@172.16.38.78 'sudo systemctl stop xiyu-bid-backend && \
  sudo ln -sfn /opt/xiyu-bid/releases/c326ba01d/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar && \
  sudo systemctl start xiyu-bid-backend'
```

> 注：V1198 为幂等迁移（表 collation 已是 utf8mb4_0900_ai_ci 则不处理），且缺省 collation 对齐后不依赖旧 collation，回滚 jar 即可恢复功能；如需还原 collation 走 `U1198` 回滚脚本。

## 经验沉淀应用

- collation 冲突需在测试计费侧对齐：已发布迁移（V1092）不动，改测试镜像的临时对齐，避免 Flyway checksum 校验失败。
- 前端 hash 资源跨版本 404 防护：remote-deploy.sh `<rm -rf $FRONTEND_PUBLIC_DIR>` 会清空旧 assets，需从历史 release 目录 cp -rn 补回，覆盖缓存旧 index.html 的客户端。

## 部署确认清单

- [x] 环境门禁确认（test / 172.16.38.78）
- [x] 早操三连 + 基线确认
- [x] Flyway 预检（validate + 识别 V1198 冲突）
- [x] 本地打包 + 产物校验（OBS + 前端入口 + jar 迁移去重）
- [x] backend 部署 + 健康检查（UP）
- [x] Flyway 迁移应用验证（V1198 生效）
- [x] API Smoke + 前端验证（全部通过）
- [x] 前端资源保留（旧 hash 资源补回 528 个）
- [x] GitHub 镜像同步（Gitee/GitHub main 一致）
- [x] 部署报告生成（本文件）

## 待办

- 提交 `FlywayMysqlContainerTest.java` 测试对齐修改到版本控制并开 PR（当前仍在工作区未提交）。