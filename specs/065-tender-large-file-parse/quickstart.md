# Quickstart: 招标文件大文件解析放行（验证指南）

**Feature**: `specs/065-tender-large-file-parse` | **Date**: 2026-09-18

> 本文件只给可运行的验证路径，不含实现代码。实现细节见 `tasks.md`（`/speckit-tasks` 阶段）。

## 前置条件

- 后端可编译：`cd backend && mvn -q -DskipTests compile`
- sidecar `/convert-stream` 可达（本地 `application-dev.yml` 覆盖 8009，测试/生产 8000）
- 一份 80MB 级扫描版招标 PDF（文本量正常）+ 同一文件的 50MB 内压缩版；一份文本量超限的构造文档（单测 stub 亦可）

## 验证场景

### V1. 大文件解析一次通过（US1）

1. 经 OBS 上传 80MB 扫描 PDF 到立项（`documentCategory=TENDER`）。
2. 打开「AI 评分标准解析」抽屉（或点「重新解析」）。
3. **预期**：解析成功，评分项清单与 50MB 压缩版项数/总权重一致；服务无 OOM。

### V2. 文本量超限明确拒绝（US2）

1. 上传文本量超限文档（或单测 stub 超限）。
2. 发起解析。
3. **预期**：任务 FAILED，提示为`招标文件文本量超过上限（约 500 万字），无法解析，请检查是否误传或拆分文档`；已有可用评分项不被覆盖。

### V3. 上传前置拦截（US3）

1. 选择超过上传配额的文件点上传。
2. **预期**：上传前即被拒绝（不进入存储与解析），提示含配额值与压缩指引。

### V4. 边界回归（既有行为）

1. 刚好 50MB 文件：内存快路径通过。
2. 超大 + 有可用历史底稿：静默回退底稿，不报错。
3. 无文件：`请先在立项阶段上传招标文件`。

## 单测锚点

```bash
# 后端（沙盒/任务 worktree 内执行）
cd backend && mvn test -Dtest=InitiationTenderTextResolverTest,ScoreParseAppServiceTest
# 前端
npx vitest --run src/composables/projectDetail/useScoreParseDrawer.spec.js
npx vitest --run src/views/Project/stages/InitiationStage.spec.js
```

## 回归门禁摘录

- `mvn test -Dtest=ArchitectureTest`（分层/纯核心）全绿
- 相关文件行预算：`ScoreParseAppService.java ≤300`、`InitiationTenderTextResolver.java ≤300`
- E2E：沿用 spec 043 quickstart 矩阵（阈值替换为文本量），无新增页面路径则 `[skip e2e-scope]`
