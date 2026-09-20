# Quickstart: 验证 AI 标书质量核查解析修复

**Feature**: 066-fix-quality-check-parse | **Date**: 2026-09-20

## 前置条件

- JDK 21 + Maven（后端）、Python 3.11 + venv（Sidecar）、Node 20 + pnpm/npm（前端）
- 本 worktree 为纯源码临时 worktree；前端依赖与 Sidecar venv 首次验证前需安装（见下）

## 1. 后端单测（核心契约）

```bash
cd backend
mvn -q test -Dtest='SidecarTimeoutBudgetTest,SidecarStreamDocumentAdapterTest,ComplianceTargetLoaderStorageDispatchTest,BidQualityCheckAsyncExecutorTest'
```

预期：全绿；覆盖 D1 档位边界（50MB/51MB/165MB/441MB/封顶/未知大小）、五分类文案、多册指名、瘦身不降档。

## 2. Sidecar 测试（分块与可观测性）

```bash
cd document-converter-sidecar
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt -r requirements-dev.txt
pytest tests/ -v
```

预期：全绿；`test_converter_pdf.py` 覆盖分块参数（默认 200、env 覆盖、跨块页完整）；`test_pdf_memory_guard.py` 断言新分块峰值内存上界；`test_app.py` 断言转换日志字段。

## 3. 前端抽屉测试

```bash
pnpm install --frozen-lockfile   # 或 npm ci
npx vitest run src/views/Project/stages/composables/useQualityCheckDrawer.spec.js
```

预期：全绿；覆盖「到顶不误报（后台执行提示）」「窗口内完成加载报告」「重开抽屉恢复进度」。

## 4. 真实环境端到端（S5，需部署授权）

对 4 个生产复现项目在测试环境复跑质量核查（trigger force=true）：

| 项目 | 预期 |
|---|---|
| 22（165MB PDF） | 任务 COMPLETED，25 项明细（解析应 ≤150s） |
| 35（565MB docx） | 任务 COMPLETED（解析按原始大小取 1800s 档） |
| 46（441MB PDF） | 任务 COMPLETED（解析应 ≤600s，实测目标 ~200s） |
| 580（纯扫描件） | 任务 FAILED，错误详情包含「图片扫描件/无文本层」，不含「请确认文件可正常打开后重新上传」 |

验证 SQL（测试库）：

```sql
SELECT project_id, status, current_step, LEFT(error_message, 200) err,
       TIMESTAMPDIFF(SECOND, checked_at, updated_at) secs
FROM compliance_check_results
WHERE project_id IN (22,35,46,580) AND check_type='BID_DOCUMENT_QUALITY'
ORDER BY project_id, id DESC;
```

Sidecar 日志对账：

```bash
sudo docker logs -t xiyu-sidecar --since <触发时刻> | grep -E "convert.*(duration_ms|chars)"
```

## 5. 门禁与回归（收尾前）

```bash
cd backend && mvn -q test          # 完整后端测试（Completion Gate）
npm run build                      # 前端构建（Completion Gate）
npm run check:line-budgets         # 行预算棘轮
git diff --check
```

预期：全绿；Java 单文件不超硬上限 300 行。
