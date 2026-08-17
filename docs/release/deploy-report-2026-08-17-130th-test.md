# 第 130 次测试环境部署报告 — 2026-08-17

## 部署环境

| 项目 | 值 |
|---|---|
| 环境 | **测试 (test)** |
| 目标主机 | `winbid-01` |
| 目标 IP | `172.16.38.78` |
| 用途 | 日常部署验证、功能测试 |
| 部署序号 | 第 130 次（测试） |
| 部署时间 | 2026-08-17 19:07:42 CST（服务激活） |

## 部署概览

| 项目 | 值 |
|---|---|
| Release ID | `0a5748b04` |
| 上一版本 Release | `a3bb16478`（2026-08-17 16:37 CST，第 129 次测试部署） |
| 基线 commit | `0a5748b04`（origin/main，PR !2314 merge commit） |
| jar 构建时间 | 2026-08-17 19:06:54 CST（168M） |
| 健康检查通过 | ✅（db/redis/sidecar/aiProvider/jwt 全 UP） |
| 部署结果 | ✅ 成功 |
| 回滚状态 | 未需要 |
| 新增 Flyway 迁移 | 无（增量 7 commit 中迁移文件变更数为 0） |
| 前端入口 | `index-DO9sVFz9.js` |

## 背景：本次为打分读取侧 50MB 防护（OOM 止血）

第 129 次部署（PR !2311）恢复了投标文件 OBS 直传并**解除上传侧 50MB 限制**后，测试环境出现 1.62GB 投标文件触发打分时后端 `Java heap space` OOM（页面报「投标文件加载失败：obs-direct:f2a75682-…」）。

根因：`ScoreBidDocumentLookup` 通过 `HttpResponse.BodyHandlers.ofByteArray()` 全量加载投标文件到内存，1.62GB 远超 JVM 堆上限。

本次部署 PR !2314（`23ecb049c`，agent/trae/fix-oversized-bid-oom）止血：

- OBS 直传链路默认 fetcher 换为 `BoundedHttpDownloader`（Content-Length 预检 + 流式累计，超限不读 body）
- OBS/本地/doc-insight 三链路统一 `capSize` 50MB，超限抛 `OversizedBidFileException`
- 同步段转 `OVERSIZED_BID_FILE` 语义码，`ScoreParseController` 转用户友好文案
- 异步段 `failScoringWithPending` 统一失败出口兜底

### 增量变更（a3bb16478 → 0a5748b04，共 7 个 commit）

| commit | 类型 | 说明 |
|---|---|---|
| `23ecb049c` | **bug fix** | 投标文件 50MB 预检防 OOM，超限返回可操作提示（PR !2314，核心变更） |
| `a09f277fe` | perf | echarts 按需引入，chunk 821.9K→406.9K（PR !2313） |
| `00b8061eb` | docs | frontend-pitfalls §15 echarts 按需引入约定回填 |
| `f7b64d3a0` / `b750971ec` | docs | 第 129 次部署报告 |
| `da74f4da9` / `0a5748b04` | merge | !2313 / !2314 auto-merge |

## 1.62GB 文件真实验证（本次核心验证）

验证对象：project 226 的 1.62GB 投标文件（`bid_file.file_size = 1,743,831,704` 字节，obs-direct 直传）。

| 检查项 | 结果 |
|---|---|
| 触发接口 | `POST /api/projects/226/score-parse/scoring`（admin 认证） |
| HTTP 状态 | ✅ `400`（预期内业务拒绝，非 500） |
| 响应文案 | ✅ `投标文件超过 50MB，无法完成打分，请压缩后重新上传`（与 `OversizedBidFileException.MESSAGE` 逐字一致） |
| 拦截耗时 | ✅ `elapsed=519ms`（access_log 铁证；Content-Length 预检生效，未下载 1.62GB body） |
| 后端 OOM | ✅ 部署以来 `OutOfMemoryError|Java heap space` 计数 = 0 |
| 服务状态 | ✅ `active`（拦截后无重启、无降级） |

access_log 证据（traceId `a9f0ae4e83df43fdba522a60867c42b8`）：

```
POST /api/projects/226/score-parse/scoring status=400 elapsed=519ms userId=1 roleCode=admin
```

## 回滚方案（未启用）

上一版本 `a3bb16478` 良好。如需回滚：

```bash
ssh jetty@172.16.38.78 'sudo systemctl stop xiyu-bid-backend && \
  sudo ln -sfn /opt/xiyu-bid/releases/a3bb16478/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar && \
  sudo systemctl start xiyu-bid-backend'
# 前端回滚：将 /srv/www/xiyu-bid 指回 releases/a3bb16478/frontend/
```

## 风险提示

1. **50MB 为止血方案**：投标文件天然偏大，50MB 拒绝会影响真实大文件打分可用性。根治方向为「流式落盘 + 按提取后文本量设限」，已开 Spec Kit 立项（specs/045-bid-file-streaming-scoring）。
2. 上传侧（PR !2311）与读取侧（本 PR !2314）限制口径暂不一致：上传允许 >50MB 直传 OBS，打分读取拒绝 >50MB。属止血期的已知妥协，Spec Kit 根治后统一。
3. 超限拦截路径无 WARN 日志（`BoundedHttpDownloader` 仅抛异常），排障时以 access_log 400 + traceId 为准。

## 部署确认清单

- [x] 环境门禁确认（test / 172.16.38.78）
- [x] 早操三连 + 基线确认（origin/main @ 0a5748b04）
- [x] 服务器现状 + Flyway 预检（迁移变更 0）
- [x] 本地打包 + 产物校验（jar 168M / dist 一致）
- [x] 上传部署 + 健康检查（19:07:42 激活，全组件 UP）
- [x] 前端资源保留（防跨版本 404）
- [x] Smoke 测试（health 200 / 登录 200 / 认证接口正常）
- [x] 1.62GB 文件真实验证（400 精确文案 + 519ms 拦截 + OOM=0）
- [x] 部署报告生成（本文件）
