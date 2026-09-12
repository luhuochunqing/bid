# Sidecar 发布闭环 Stage D 演练记录（2026-09-12，test 环境口径）

> Workflow: wf-xiyu-bid-poc-a380753e-20260911-230101 / Task impl-05-sidecar-gate。
> 与 Task1（fixture 门禁）正交：本任务未碰 scoreparse 解析规则、去重逻辑与任何 fixture 文件
> （见 §5 diff 清单）。未 push main，成果由 Controller 集成。

## 1. 改动清单

| 文件 | 改动 |
|---|---|
| `backend/.../scoreparse/application/SidecarTableExtractor.java` (+76) | 新增 `EXPECTED_SCHEMA_VERSION=1.0` / `SCHEMA_VERSION_HEADER` 常量；响应增加版本守卫 `requireV1Contract` + 纯决策函数 `versionMismatchReason`（无 IO/日志/状态）；无头回落 v2 / 头体不匹配时抛 `IllegalStateException`（含期望/实际版本号） |
| `backend/.../scoreparse/application/SidecarTableExtractorTest.java`（新增，148 行） | 内嵌 HttpServer 7 例：v1 解析+请求头断言、OnDisk 入口、v2 体无头 fail-fast、v2 头 fail-fast、头体矛盾 fail-fast、artifact 标记 fail-fast、500 透出 |
| `document-converter-sidecar/app.py`（+17/-） | `/extract-tables` v1/v2（含 422）与 `/health` 回显 `X-Schema-Version` 响应头；**响应体零变化**（v1 无 schemaVersion 字段，保持夹具兼容） |
| `document-converter-sidecar/tests/test_extract_tables.py`（+35） | 新增 `TestExtractTablesVersionHeaders` 3 例：v1 请求→1.0 头+v1 体、默认→2.0 头+v2 体、health 体不变+2.0 头 |
| `scripts/release/package-release.sh` | sidecar 必需 6 文件门禁；`SCHEMA_VERSION` 指纹提取+失败阻断；tar 排除 `.venv/.runtime/.DS_Store`（根治 444M venv 打进发布包）；`release-metadata.json` 新增 `sidecarSchemaVersion` |
| `scripts/release/remote-deploy.sh` | 重建后 Sidecar 健康门禁（30×2s，失败在后端停止前退出、零影响）+ 部署版本指纹打印；无变动分支打印现部署 schemaVersion |
| `scripts/release/smoke-sidecar.sh`（新增，156 行） | 发布冒烟：P0 `/health`+版本头、P0 `1371_tables_v1.json` v1 形态回放（只断言契约形态：tableIndex/rows/isScoreTable/cell 四键，不碰评分语义）、P1 空 docx 双版本探针（v1 头→v1 形 / 默认→v2 形） |
| `scripts/release/deploy-test.sh` / `deploy-prod.sh` | Step4 增传 `smoke-sidecar.sh` + `1371_tables_v1.json`；Step6 弱探针（裸 POST 空包、无版本不断言）替换为 `smoke-sidecar.sh` 硬门禁（失败置 `DEPLOY_EXIT=1`） |

## 2. 测试证据（真实执行）

- Java：`mvn -Dtest='SidecarTableExtractorTest' test` → `Tests run: 7, Failures: 0, Errors: 0`；
  `mvn -Dtest='SidecarTableExtractorTest,SidecarStreamTextExtractorTest,SidecarImageExtractorTest' test` → EXIT 0。
- Python：`pytest tests/test_extract_tables.py tests/test_app.py` → 30 passed（含 3 个新版本头用例，既有 health 精确体断言仍绿）。
- Python 全量：83 passed + 5 failed，5 个均为 `test_converter_pdf.py` / `test_pdf_memory_guard.py`；
  经 `git stash` pristine 对照：同一 5 例在基线同样失败（conda 环境缺 PDF 系统依赖），与本改动无关。
- 脚本：`bash -n` 全部通过（package/remote/deploy-test/deploy-prod/smoke）。
- 打包片段实执行（/tmp，不污染仓库）：必需文件门禁 OK；`schema=2.0`；
  新 tar 26KB 且 `tar -tzf | grep .venv` = 0 条（旧逻辑会卷入 444M `.venv`）。
- 发布冒烟实执行：本 worktree sidecar 源码 + 自带 `.venv`，临时端口 **18091**
  （避开开发宿主 8009 与服务器 8000），`smoke-sidecar.sh http://127.0.0.1:18091` → **5/5 PASS**：
  `GET /health → 200 UP`、`X-Schema-Version=2.0`、
  `fixture 回放 tables=14 cells=8215 scoreTables=1`、
  `v1 探针 200 v1 形态（响应头 1.0）`、`v2 探针 200 schemaVersion=2.0`。

## 3. 故障注入（停 Sidecar，三选一之壹）

kill 18091 实例后重跑冒烟 → exit=1，预期告警全部出现：

```text
❌ GET /health → DOWN（期望 200 UP，sidecar 未存活则阻断发布）
❌ 健康响应缺少 X-Schema-Version 头（sidecar 版本不可指纹，阻断发布）
✅ fixture v1 形态回放通过（tables=14 cells=8215 scoreTables=1）
❌ v1 探针 → DOWN（/extract-tables 不存在，发布后会全量降级 Markdown）
❌ v2 探针 → DOWN（期望 200 + schemaVersion + 2.x 响应头，实际头=）
结论: ❌ SIDECAR SMOKE FAILED — 阻断发布
```

（降版本路径由 `SidecarTableExtractorTest` 4 个 fail-fast 用例覆盖：v2 体/头/头体矛盾/artifact 标记，均断言消息含 `1.0` 与实际版本号。）

## 4. 风险与未覆盖项

1. `remote-deploy.sh` 新增健康门禁仅过 `bash -n` + 逻辑评审，未在真实 test/prod 服务器执行
   （需 `ENV=test/prod` + SSH，本 worktree 按纪律不直连生产链；合入后建议首个 test 部署观察）。
2. 全量 `package-release.sh`（npm build + mvn + 容器测试）未跑；sidecar 相关片段已单独实执行。
3. 演练中发现本 worktree `.venv/bin/pip` shebang 指向旧路径 `/Users/user/xiyu/xiyu-bid-poc/...`，
   首次 `pip install pymupdf` 误装到该位置的 venv（公共包、无害、可逆）；后改用 `python -m pip`
   装到本 worktree `.venv`（gitignored，仅环境修补，未进版本库）。建议后续重建该 worktree venv。
4. `remote-deploy.sh` 383 行（改前 360 行）超 300 行预算——该预算针对 Java 文件
   （`ARCHITECTURE.md §Agent Contract` 第 10 条），本任务 Java 文件均合规
   （208 / 148 行）；shell 发布脚本维持现状风格，仅追加必要门禁。
5. macOS 系统 bash 3.2 无多字节支持：`$VAR）` 会把全角括号粘进变量名导致 `unbound variable`
   （已踩坑，`smoke-sidecar.sh` 内全部改用 `${VAR}` 形式；同类旧脚本若新增中文括号需同样处理）。
