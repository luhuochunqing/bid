# 第 132 次测试环境部署报告 — 2026-08-19

## 部署环境

| 项目 | 值 |
|---|---|
| 环境 | **测试 (test)** |
| 目标主机 | `winbid-01` |
| 目标 IP | `172.16.38.78` |
| 用途 | score-parse 多模态 data-URI 修复（PR !2340）+ analytics 验收修复（PR !2339）上线 |
| 部署序号 | 第 132 次（测试） |
| 部署时间 | 2026-08-19 15:46 CST（backend 激活） |

## 部署概览

| 项目 | 值 |
|---|---|
| Release ID | `84bc63278`（jar 154M） |
| 上一版本 Release | `67f9b259e-api8080`（2026-08-19 04:49 激活） |
| 基线 commit | `84bc63278` = origin/main HEAD（PR !2340 已合入） |
| 健康检查通过 | ✅（db/redis/sidecar/aiProvider/jwt/diskSpace 全 UP） |
| 部署结果 | ✅ 成功 |
| 回滚状态 | 未需要 |
| 新增 Flyway 迁移 | 无 |

## 增量变更（8 个 commit，67f9b259e..84bc63278）

| Commit | 内容 |
|---|---|
| `84bc63278` | auto-merge PR !2340：score-parse 喂 Gemini 前剥离 markdown 图片/data-URI，消除 500 |
| `6d5461880` | docs(wiki)：规避 wiki link 误检——多模态根因用文字描述正则 |
| `ca8ec6e0f` | docs(wiki)：score-parse 多模态 data-URI→Gemini 500 根因回填 |
| `28f127d13` | docs(scoreparse)：048 T020 测试环境补测完成，SC-001 达成 |
| `977224faf` | fix(scoreparse)：喂 Gemini 前剥离 markdown 图片/data-URI，消除 500 |
| `6e0e8b34a` | auto-merge PR !2339：analytics 修复 round2 |
| `fc98a6d66` | review: PR!2339 自审修复——JPQL 实测回归 + order by 显式化 + 死 prop 清理 |
| `1a39e7785` | docs：知识沉淀 + wiki 回填——数据分析口径分裂与入口拦截四连漏 |
| `3c78608e2` | fix(analytics)：PR!2333 验收四连漏修复——口径分裂/入口拦截/部门下拉交互 |

**迁移变更**：无（`git diff --name-only` 无 migration-mysql / rollback 变更）。

## Flyway 预检结果

- Step 1：`flyway-repair-runner.sh validate` → **VALIDATE OK（258 migrations，checksum 全匹配）**
- Step 2：DB 已应用至 V1197（最新），本次无新迁移
- Step 3：remote-deploy.sh 激活前自动 validate 通过

## 部署步骤

1. 早操三连：dev-env + sync-env + check-git-wrapper ✅
2. 基线确认：HEAD = origin/main = `84bc63278`，工作区干净
3. 本地打包：`RELEASE_ID=84bc63278 VITE_API_BASE_URL= VITE_OBS_ENABLED=true COPYFILE_DISABLE=1 package-release.sh`（BUILD SUCCESS，45s）
4. 产物校验：jar 内迁移版本无重复；OBS 直传已启用（`obsEnabled=true`，Detail chunk `.upload(` 数=2）；前端入口 `index-DSsp411F.js`
5. 上传 + remote-deploy.sh（SYSTEMCTL_SUDO=true，含 DB backup）
6. 前端旧资源保留：`cp -rn` 上一版本 `67f9b259e-api8080` assets ✅

## 验证结果

| 检查项 | 结果 |
|---|---|
| 后端 health（127.0.0.1:18080） | ✅ UP（consecutive 3/3，80 次尝试） |
| readiness（经 Nginx 8080） | ✅ HTTP 200 |
| 前端一致性 | ✅ `src="/assets/index-DSsp411F.js"` 与 release 一致 |
| login 空密码 | ✅ HTTP 400（参数校验：Password/Username required） |
| projects 未认证 | ✅ HTTP 403 |
| crm health 未认证 | ✅ HTTP 401 |
| 前端 `/` | ✅ HTTP 200 |
| 前端 `/login` | ✅ HTTP 200 |

## GitHub 镜像同步

⚠️ **异常**：GitHub 领先 Gitee 1 个 commit（`a0578860a chore(locks): prune stale expired locks`，github-actions[bot] 2026-08-18 清理过期锁文件），且 Gitee 落后 GitHub 329 个 commit。

`sync-to-github.sh` 安全检查会拦截（Gitee main 是 GitHub main 祖先）。正确流程：先用 `sync-from-github.sh` 将 `a0578860a` cherry-pick 回 Gitee → 审查 → 合入 main → 再镜像。**本次未执行，待用户确认。**

## 回滚方案（未启用）

```bash
ssh jetty@172.16.38.78 'sudo systemctl stop xiyu-bid-backend && \
  sudo ln -sfn /opt/xiyu-bid/releases/67f9b259e-api8080/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar && \
  sudo systemctl start xiyu-bid-backend'
```

## 风险提示

1. **GitHub 镜像方向反了**（见上），待用户确认是否合回 Gitee。该 commit 仅删除过期锁文件（`.agent-locks/performance-bundle-export.yml`），内容安全。
2. `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always` 仍保留（第 13/14/15 次部署用户决定保留项，非本次临时配置）。

## 部署确认清单

- [x] 环境门禁确认（test / 172.16.38.78）
- [x] 早操三连 + 基线确认（main 84bc63278）
- [x] Flyway 预检 3 步法（validate OK / 无新迁移）
- [x] 本地打包 + 产物校验（OBS 直传 + 前端入口）
- [x] backend 部署 + 健康检查（全组件 UP）
- [x] 前端旧资源保留（67f9b259e assets）
- [x] Smoke 测试（readiness 200 / login 400 / projects 403 / crm 401 / 前端 200）
- [x] 部署报告生成（本文件）
- [ ] GitHub 镜像同步（待用户确认方向反了处理）
