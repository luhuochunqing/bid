# Quickstart: 投标文件流式落盘与文本量设限打分 验证指南

**Feature**: 045-bid-file-streaming-scoring | **Date**: 2026-08-17

> 端到端可运行验证场景。前置：主工作区开发环境（前端 1323 / 后端 18089 / sidecar 8009 / MySQL xiyu_bid_main），`export XIYU_DEV_CONFIRMED=1`。

## 场景 0：环境准备

```bash
cd /Users/user/xiyu/worktrees/trae
docker compose up -d                    # MySQL + Redis
npm run dev:all                         # 前后端 + sidecar
curl -s http://127.0.0.1:8009/health    # sidecar 存活
curl -s http://127.0.0.1:18089/actuator/health | grep UP
```

## 场景 1：1.62GB 大文件打分（US1 / SC-001，核心验收）

前置：project 226 已上传 1.62GB 投标文件（测试库已有，`obs-direct:f2a75682-...`）且评分项已解析。

```bash
# 登录取 cookie（access_token 走 Set-Cookie；端点是 /api/auth/login，/api/auth/sessions 是会话管理接口）
curl -si -c /tmp/ck.txt -X POST http://127.0.0.1:18089/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"XiyuAdmin2026!"}' >/dev/null

# 触发打分（本地环境用本地库中的对等大文件；测试环境用 project 226）
curl -s -b /tmp/ck.txt -X POST http://127.0.0.1:18089/api/projects/226/score-parse/scoring -H 'Content-Type: application/json' -d '{}'
```

**预期**（对照 SC-001/SC-002/SC-003）：
- 同步响应 200（任务受理），任务最终 `COMPLETED` 或（若该文件文本量真超限）`FAILED` + 文本量超限文案。
- 全程后端日志无 `OutOfMemoryError`；JVM 堆无 1GB 级尖峰（`jstat -gc <pid> 5s` 观察或 APM 截图）。
- `ls ${java.io.tmpdir}/xiyu-score-parse/` 任务终态后 60s 内无该 taskId 的 `.part` 文件。

## 场景 2：小文件快路径回归（US4 / SC-006）

用 ≤50MB 的普通投标文件项目触发打分。

**预期**：结果与现网一致；临时目录全程无 `.part`（InMemory 路径，零落盘）；耗时与改造前相当（±10%）。

## 场景 3：文本量超限明确拒绝（US3 / SC-004）

构造纯文本文档 ≥5M 字符（如 `python3 -c "print('投标应答 '*1200000)" > huge.txt` 打包 docx，或直接 10MB+ 纯文本改名 .pdf 走解析失败路径验证不误报），上传后触发打分。

**预期**：任务 `FAILED`，errorMessage = 「投标文件文本量超过上限（约 500 万字），无法完成打分，请检查是否误传或拆分文档」；`score_result` 无部分得分落库；临时文件被清理。

## 场景 4：磁盘配额拒绝（US2）

`app.score-parse.bid-file.max-file-bytes` 临时调小（如 1024）重启后端，用任意投标文件触发打分。

**预期**：400 + 「投标文件过大超出服务器处理配额，请联系管理员」（同步段 Content-Length 预检即拒）；恢复配置。

## 场景 5：临时文件清理兜底（US2 / SC-003）

1. 手动在 temp-dir 放一个伪造 `{taskId}.part` → 重启后端 → 文件被启动全清删除。
2. 打分进行中 `kill -9` 后端 → 重启 → `ScoreParseTaskRecoveryRunner` 将任务标 FAILED 且 `.part` 被删。

## 场景 6：sidecar 流式接口直连（contracts/sidecar-streaming-api.md）

```bash
head -c 5242880 /dev/urandom > dummy.bin   # 5MB 任意文件
curl -s -X POST http://127.0.0.1:8009/convert-stream -F "file=@dummy.bin" | head -c 200
```

**预期**：`422`（不可解析，返回错误 JSON）；换真实 PDF → `200` + `text/plain` chunked 文本流。

## 单元测试入口（TDD，Constitution III）

```bash
cd backend
mvn test -Dtest='ScoreBidDocumentLookupTest,ScoreScoringAppServiceTest'          # 改造点回归
mvn test -Dtest='TempBidFileStoreTest,StreamingBidFileFetcherTest'              # 新组件（任务阶段新增）
mvn test -Dtest=ArchitectureTest                                                # 分层守卫
```

## 测试环境部署后终验（SC-001 铁证）

部署测试环境后重放 project 226 打分：`grep -c "OutOfMemoryError\|Java heap space" /opt/xiyu-bid/shared/backend/logs/*.log` = 0 + 任务终态截图。生产上线前执行 R9 检查项：`df -h /opt` ≥ 6GB。
