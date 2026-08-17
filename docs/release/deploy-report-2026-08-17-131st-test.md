# 第 131 次测试环境部署报告 — 2026-08-17

## 部署环境

| 项目 | 值 |
|---|---|
| 环境 | **测试 (test)** |
| 目标主机 | `winbid-01` |
| 目标 IP | `172.16.38.78` |
| 用途 | spec 045 T025 终验（流式落盘 MVP 验收过程 bug 修复） |
| 部署序号 | 第 131 次（测试） |
| 部署时间 | 2026-08-17 22:07 CST（backend 激活）/ 22:26 CST（sidecar 热更新） |

## 部署概览

| 项目 | 值 |
|---|---|
| Release ID | `da58d3f92-t025fix`（基于 main `da58d3f92` 的未提交热修复，jar 168M） |
| 上一版本 Release | `da58d3f92`（spec 045 US1 MVP，PR !2317） |
| 基线 commit | `da58d3f92` + 6 文件本地修复（待提 PR） |
| 健康检查通过 | ✅（db/redis/sidecar/aiProvider/jwt/diskSpace 全 UP） |
| 部署结果 | ✅ 成功 |
| 回滚状态 | 未需要 |
| 新增 Flyway 迁移 | 无 |

## 背景：T025 第一轮终验暴露的两个 bug

第 130 次部署（50MB 止血）后，spec 045 US1 流式落盘 MVP（`da58d3f92`）部署并重放 project 226 的 1.62GB 打分，任务 FAILED，排查发现两个独立 bug（症状相似但根因不同）：

1. **sidecar OOM**：1.62GB 文件 Java 端配额 2GB 放行 → 全量推给 sidecar `/convert-stream` → markitdown 转换时 uvicorn RSS 冲到 4.8GB，被内核 OOM killer 杀死（sidecar 无内存限制，`mem=0`）。
2. **异步线程事务缺失**：`failScoringWithPending` 在 `@Async` 线程直接调 `resultRepository.deleteByScoreItemIdIn`（派生 delete 走 `em.remove`），无事务上下文抛 `TransactionRequiredException`，把友好失败文案覆盖成 `打分失败: No EntityManager with actual transaction...`（任务表 21:50 记录为铁证）。

## 本次修复内容（6 文件，未提交 → 待 PR）

| 文件 | 修复 |
|---|---|
| `ScoreScoringAppService.java` | 新增 `@Transactional replaceResultsAtomically`（DELETE+INSERT 原子覆盖），`self` 代理调用保证异步线程事务；catch 块补 `log.warn` 记录加载/提取失败根因（T025 教训：异常被吞导致盲飞） |
| `StreamingBidFileFetcher.java` | 单文件配额默认值 2GB → **512MB**（对齐 sidecar markitdown 实际转换能力） |
| `ScoreParseController.java` | 补 `BID_FILE_QUOTA_EXCEEDED` → `BidFileQuotaException.MESSAGE` 文案映射（原裸露语义码） |
| `SidecarStreamTextExtractor.java` | sidecar URL 默认值 8009 → **8000**（对齐测试/生产容器映射；本地 dev 由 application-dev.yml 覆盖 8009） |
| `application-dev.yml` | 本地开发显式配置 `sidecar-url: 127.0.0.1:8009` |
| `document-converter-sidecar/app.py` | `SIDECAR_MAX_UPLOAD_MB` 默认 2048 → **512**，与 Java 端 `max-file-bytes` 同值（注释双向锚定） |

单测：`StreamingBidFileFetcherTest,ScoreBidDocumentLookupTest,ScoreScoringAppServiceTest` 全绿。

## 部署方式说明

- backend：常规 release 目录 + symlink 切换 + systemd 重启。
- sidecar：`docker build` 因华为云 apt 源拉 tesseract 超时（>12min）放弃，改用 **`docker cp` 热更新** + `docker restart`（容器内 `/app/app.py` 已验证 512 默认值生效）。⚠️ 技术债：`document-converter-sidecar:latest` 镜像仍是旧代码，`/opt/xiyu-bid/sidecar/` 目录已同步新 app.py，下次完整 build 自动收敛。

## T025 终验结果（SC-001 铁证，project 226 重放）

| 检查项 | 结果 |
|---|---|
| 触发接口 | `POST /api/projects/226/score-parse/scoring`（06234 认证） |
| HTTP 状态 | ✅ `400`（同步段 Content-Length 预检拒绝，非 500） |
| 响应文案 | ✅ `投标文件过大超出服务器处理配额，请联系管理员`（与 `BidFileQuotaException.MESSAGE` 逐字一致，Controller 映射生效） |
| 拦截耗时 | ✅ `0.771s`（未下载 1.62GB body，未到 sidecar） |
| 后端 OOM | ✅ `grep -c "OutOfMemoryError\|Java heap space"` = **0** |
| 临时文件 | ✅ `xiyu-score-parse/` 目录无 `.part` 残留 |
| sidecar | ✅ 全程 Up，`/health` = `{"status":"up"}`（未被本次请求打挂） |
| 任务表 | ✅ 本次未创建新任务（同步拒绝语义正确）；历史 FAILED 记录为第一轮两 bug 现场 |

验收口径说明：1.62GB > 512MB 配额，走 FR-002 资源类拒绝路径。「流式落盘后成功打分」场景（≤512MB 大文件）待有真实中等尺寸文件时回归（quickstart 场景 1 对等本地验证）。

## 回滚方案（未启用）

```bash
ssh jetty@172.16.38.78 'sudo systemctl stop xiyu-bid-backend && \
  sudo ln -sfn /opt/xiyu-bid/releases/da58d3f92/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar && \
  sudo systemctl start xiyu-bid-backend'
# sidecar 回滚：docker cp /opt/xiyu-bid/sidecar/app.py.bak-t025 xiyu-sidecar:/app/app.py && docker restart xiyu-sidecar
```

## 风险提示

1. **磁盘水位 95%**：`/opt` 可用仅 5.6GB（R9 要求 ≥6GB）。本次同步拒绝不落盘无影响；但 100-512MB 文件流式落盘场景需要空间。建议清理 buildkit 缓存（`docker builder prune`）与旧 release。
2. sidecar `latest` 镜像 ≠ 运行容器（热更新债，见上）。
3. 6 文件修复未提交：待提 PR（含 spec 045 tasks.md T025 勾选 + quickstart 场景 4 文案更新为 512MB 口径）。

## 部署确认清单

- [x] 环境门禁确认（test / 172.16.38.78）
- [x] 基线确认（main da58d3f92 + 6 文件修复）
- [x] 相关单测全绿
- [x] backend 部署 + 健康检查（全组件 UP）
- [x] sidecar app.py 热更新 + 容器内 512 默认值验证 + health up
- [x] T025 终验：400 + 精确文案 + 0.77s + OOM=0 + 无 .part + sidecar 存活
- [x] 部署报告生成（本文件）
