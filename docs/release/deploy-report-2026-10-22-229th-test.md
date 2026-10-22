# 第 229 次部署报告（test）

## 部署环境

| 项目 | 值 |
|---|---|
| 环境 | **测试** (`test`) |
| 主机 | winbid-01 / 172.16.38.78 |
| 部署日期 | 2026-10-22 13:24 (UTC+8) |

## 部署概览

| 项目 | 值 |
|---|---|
| Release ID | `fa8a65229` |
| 部署分支 | `agent/trae-init`（锚点分支，HEAD = origin/main） |
| HEAD Commit（本次部署的代码版本） | `fa8a65229` |
| 报告提交 | 首次 `c3a92f3a1`；S6 审查后的修订见本分支 HEAD（基于 fa8a65229） |
| 旧版本 | `021cf93e4-api8080`（第 228 次，2026-10-22 11:16） |
| 构建包 | `.release/xiyu-bid-release-fa8a65229.tar.gz`（155M） |
| 增量 | 13 个 commit，13 个文件（+441/-87） |

> 说明：本次 Release ID 未追加 `-api8080` 历史后缀，直接使用 commit 短哈希，不影响部署语义（后缀仅为命名习惯）。

## PR 列表（增量 13 个 commit，自 021cf93e4..fa8a65229）

| Commit | 说明 |
|---|---|
| `8bbb2eb35` | docs(release): 第 228 次测试部署报告 |
| `e6cef254f` | **!2839** docs(release): 第 228 次测试部署报告（auto-merge） |
| `63803ffef` | fix(release): sidecar 指纹改用内容哈希，消除打包 sha 漂移 |
| `b156c03d1` | fix(scoreparse): 解除多表无总分采纳硬死锁并优化人工复核交互 |
| `bfa4f6958` | chore(scoreparse): 补充评分解析 LLM 原始响应与校验明细诊断日志 |
| `0403a5397` | docs(wiki): 回填评分解析线上诊断入口与项目 1122 根因 |
| `611ea1785` | chore(scoreparse): 多模态结构化输出同步补 LLM 原始响应日志 |
| `1f3c1a469` | fix(release): sidecar 指纹失败路径硬中止 + metadata 缺失不中断部署 |
| `fe70ed0c4` | fix(release): metadata 不可读时回退归档字节哈希而非中断部署 |
| `15c3d9973` | docs: 沉淀评分标准多表无总分死锁解除与人工复核交互经验 |
| `49d95e98f` | **!2840** chore(scoreparse): 补充评分解析诊断日志 |
| `830a1d860` | **!2841** fix(release): sidecar 指纹改用内容哈希（auto-merge） |
| `fa8a65229` | **!2842** fix(scoreparse): 解除多表无总分采纳硬死锁并优化人工复核交互（auto-merge） |

## 改动范围

- **后端（4 文件）**：scoreparse 领域
  - `ScoreParseReviewAppService`（解除多表无总分采纳硬死锁，人工复核交互）
  - `MultimodalStructuredExtractionHandler`、`OpenAiSdkStructuredOutputTransport`（补 LLM 原始响应与校验明细诊断日志）
  - `ScoreParseReviewAppServiceTest`（配套测试）
- **前端（2 文件）**：`ScoreCandidateReviewCard.vue` + spec（人工复核卡片交互重构）
- **发布脚本（2 文件）**：`package-release.sh`、`remote-deploy.sh`（sidecar 指纹改为内容哈希，消除打包 sha 漂移误判；metadata 异常回退策略）
- **数据库迁移**：**无新迁移**（源码与 DB 同步在 V1220）
- **Sidecar 源码**：**零变更**（`git diff 021cf93e4..HEAD -- document-converter-sidecar/` 为空）
- **文档**：`.wiki`（2 页 + log）、`docs/lessons/lessons-learned.md`、第 228 次部署报告

## Flyway 预检结果

| 检查项 | 结果 |
|---|---|
| Step 1: 服务器 validate | ✅ VALIDATE OK（Successfully validated 280 migrations） |
| Step 2: DB 已应用版本 | 最新 V1220（2026-09-27），与源码 max V1220 一致，无 pending |
| Step 3: remote-deploy 内置 validate | ✅ 通过（激活前预检，280 migrations checksum 全匹配） |

## 部署步骤

1. **环境门禁**：AskUserQuestion 确认 ENV=test（172.16.38.78）✅
2. **早操三连**：`sync-env.sh`（init 分支 ff-only 已最新，本地门禁 7/7 就绪）✅
   - 注：锚点分支 `agent/trae-init` 上 session-gate 会阻断开发动作，部署为只读发布操作，直接以 `bash scripts/sync-env.sh .` 完成基线同步
3. **基线确认**：HEAD = origin/main = `fa8a65229`，工作区干净 ✅
4. **服务器现状**：旧版 `021cf93e4-api8080` 运行中，health 200 UP ✅
5. **Flyway 预检 3 步法** ✅（见上表）
6. **本地打包**：`RELEASE_ID=fa8a65229 VITE_API_BASE_URL= VITE_OBS_ENABLED=true COPYFILE_DISABLE=1 package-release.sh` ✅（容器测试门禁 `FlywayMysqlContainerTest` 通过；BUILD SUCCESS）
7. **产物校验** ✅：同源 `apiBaseUrl=""`、`obsEnabled=true`、OBS `.upload(` 调用数=2、jar 内 V 迁移无重复（279 个 V + 1 个 B73 = 280 个有效迁移）
8. **上传 + 部署**：scp 155M 归档 + `remote-deploy.sh`（`SYSTEMCTL_SUDO=true`）✅ 一次成功
9. **DB 备份** ✅：`winbid-fa8a65229-20261022132425.sql.gz`（15M）
10. **Sidecar 重建**：指纹变化（`5a3e833e` → `24c0104f`）触发 rebuild，镜像构建成功，`xiyu-sidecar` 重启后健康检查通过；`schemaVersion=2.2`
11. **前端资源保留** ✅：从 `021cf93e4-api8080` 目录 `cp -rn` 旧 assets，assets 数 277（补回上一版本 hash 文件）
12. **健康检查** ✅：80 次尝试后 3/3 连续通过（含 Kafka readiness 延迟，属已知行为）
13. **Smoke 测试** ✅（9/9 + Sidecar 5/5，见下节）
14. **配置清理检查**：仅历史保留项 `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`，非本次引入

## 验证结果

### 健康检查

```
✅ Flyway validate 通过（无 pending 新迁移）
✅ Sidecar 容器升级完成 / Sidecar 健康检查通过
✅ AI Provider 守卫通过：AI_PROVIDER=custom
✅ Health check passed (consecutive 3/3, total attempts: 80, service: active/running)
✅ Frontend matches release (src="/assets/index-CUPjUaBu.js")
```

健康组件全绿：aiProvider（custom / gemini-3.5-flash）/ db（MySQL）/ diskSpace / jwt（HMAC-SHA256, 64 字节）/ livenessState / ping / readinessState / redis（6.2.19）/ sidecar（reachable）均 UP。

### Smoke 测试（经 Nginx 8080 代理到后端 18080）

| 检查项 | 期望 | 实际 | 结果 |
|---|---|---|---|
| /actuator/health | 200 | 200 UP | ✅ |
| /actuator/health/readiness | 200 | 200 UP | ✅ |
| POST /api/auth/login（空参） | 400 | 400 | ✅ |
| /api/projects（未认证） | 401 | 401 | ✅ |
| /api/integration/crm/health（未认证） | 401 | 401 | ✅ |
| 前端 / | 200 | 200 | ✅ |
| 前端 /login | 200 | 200 | ✅ |
| 前端入口资源 | 与 release 一致 | `assets/index-CUPjUaBu.js` | ✅ |
| gzip + 强缓存头 | gzip + public, immutable | `Content-Encoding: gzip` + `Cache-Control: public, immutable, max-age=31536000` | ✅ |
| Sidecar 冒烟（smoke-sidecar.sh） | 5/5 | 5 通过 / 0 失败 / 0 警告 | ✅ |

Sidecar 冒烟明细：fixture v1 形态回放通过（tables=14 cells=8215 scoreTables=1）；v1 探针 200（X-Schema-Version=1.0）；v2 探针 200（schemaVersion=2.2）。

### 部署记录确认

```json
"releaseId": "fa8a65229",
"activatedAt": "2026-10-22T05:24:40Z",
"releaseDir": "/opt/xiyu-bid/releases/fa8a65229",
"sidecarSha256": "24c0104ff62244e9f16b66bc4f371b097714ecbae88dbe09a77afd930cb63273",
"obsEnabled": true
```

## 本次观察与改进点

**1. sidecar 指纹漂移问题本次已由 [!2841](https://gitee.com/allinai888/bid-xiyu/pulls/2841) 从根上修复**

- 本次仍是源码零变更却被判定为"指纹变化"（`5a3e833e` → `24c0104f`），触发完整 `docker build`；但根因修复已随本次发版上线：`package-release.sh` 改为写**文件内容哈希**，`remote-deploy.sh` 优先读 metadata 中的内容哈希，历史包才回退归档字节哈希。
- 预期：**下一次部署**若 sidecar 源码无变更，将直接跳过构建（`Sidecar 源码无变动` 分支）。本次属于"修复上线前的最后一次误判"，后续应观察验证。

**2. `/api/projects` 未认证返回 401 而非 403**

- SOP 模板期望 403，实际 401。语义上未认证返回 401 正确（403 应为已认证但无权限），与第 228 次一致，不视为问题；建议日后把 SOP 期望值更正为 401，避免误判。

**3. 前端 assets 保留仍未脚本化**

- 本次继续按 SOP 手工 `cp -rn`（`remote-deploy.sh` 未内置该步骤）。建议后续固化为部署脚本的默认后置动作，减少手工步骤遗漏风险。

## 回滚信息

| 项目 | 值 |
|---|---|
| 上一版本 Release | `021cf93e4-api8080`（第 228 次） |
| 上一版本 Release 目录 | `/opt/xiyu-bid/releases/021cf93e4-api8080`（本次部署后仍存在） |
| DB 备份 | `winbid-fa8a65229-20261022132425.sql.gz`（部署前自动备份，15M） |
| 回滚方式 | 原子 jar 替换恢复上一版 + `systemctl restart xiyu-bid-backend`；本次无 schema 变更，DB 无需回滚 |
| 回滚 owner | 运维 + 当班开发 |

## 风险提示

1. **评分解析行为变更**：本次上线"多表无总分采纳硬死锁解除 + 人工复核卡片交互重构"，测试环境验收需重点回归评分解析抽屉/复核卡片的采纳流程与多表场景。
2. **诊断日志增量**：新增 LLM 原始响应与校验明细日志，日志量会上升，注意磁盘与日志采集压力。
3. **Kafka readiness 延迟**：本次 80 次尝试后 UP，属已知行为；超过 4 分钟需排查 Kafka broker。
4. **重启窗口**：后端 `13:24:40` 重启，health 连续 3/3 通过前约 2–3 分钟 API 不可用；测试环境可接受，生产部署需按维护窗口安排。
5. **副作用**：sidecar 镜像本次被重建（源码未变），若后续再次出现无意义重建，需确认 !2841 修复是否按预期生效。

## 部署确认清单

- [x] 环境门禁（用户确认 ENV=test，部署 fa8a65229）
- [x] 早操三连通过（sync-env ff-only 已最新 + 本地门禁 7/7）
- [x] 服务器现状确认（旧版 health UP）
- [x] Flyway 预检 3 步法通过（无 pending、无 checksum mismatch）
- [x] 本地打包成功（容器测试门禁 + BUILD SUCCESS）
- [x] 产物校验通过（obsEnabled=true、同源 baseURL、OBS 直传、迁移无重复）
- [x] DB 备份完成（15M）
- [x] remote-deploy.sh 一次成功（SYSTEMCTL_SUDO=true）
- [x] Sidecar 重建 + 健康门禁通过（schemaVersion=2.2）
- [x] 健康检查通过（3/3 连续）
- [x] 前端旧 assets 保留
- [x] Smoke 测试 9 项 + Sidecar 冒烟 5 项全部通过
- [x] 前端入口资源与 release 一致
- [x] 临时调试配置检查（仅历史保留 SHOW_DETAILS=always）
- [x] 部署记录确认（deployed-release.json 已更新）
- [x] 部署报告生成

**部署状态：✅ 成功（一次成功，无人工回退；后端重启后约 2–3 分钟 health 重试窗口内 API 不可用，非零中断）**
