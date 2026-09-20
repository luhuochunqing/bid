# Phase 1 Data Model: 解析结果分类与等待上限

**Date**: 2026-09-20 | **Branch**: `agent/qoder/fix-quality-check-parse-failure`

## 值对象

### SidecarParseOutcome（NEW，纯核心 `compliance/domain`）

一次投标文件解析的终态结果，替代"空文档哨兵"。

| 字段 | 类型 | 说明 |
|---|---|---|
| `kind` | enum `Kind` | 终态分类（见下） |
| `content` | `DocumentContent` | 解析产物；非 OK 分类时为空文档（不可消费） |
| `detail` | `String` | 诊断细节（HTTP 状态码/异常消息/警告摘要），用于日志与文案组装 |
| `warnings` | `List<String>` | Sidecar 返回的警告（如 `low_text_density`） |

**Kind 语义**：

| Kind | 触发条件 | 可消费内容 | 用户可操作动作 |
|---|---|---|---|
| `OK` | 2xx 且提取到文本/页 | 是 | — |
| `TIMEOUT` | 请求读超时（含排队） | 否 | 稍后重试（无需重传文件） |
| `SERVICE_ERROR` | 连接建立超时/拒绝、5xx、401/413、2xx 非法 JSON 等服务侧或协议错误 | 否 | 稍后重试；持续出现联系管理员（413 为超过解析服务上限） |
| `EMPTY_TEXT` | 2xx 但 **markdown 空且 pages 全空**（`low_text_density` 警告仅作补充证据） | 否 | 提供可复制文字的版本（无 OCR 支持） |
| `UNPARSEABLE` | 文档级 4xx（400/422 等）：格式不支持/损坏 | 否 | 确认文件可正常打开后重新上传（唯一允许重传的解析分类） |

**不变量**：
- `content` 永不为 null；非 OK 时 `content.fullText()` 为空且 `pages()` 为空。
- 多册合并：任一册非 OK → 整体失败，detail 与用户文案指名该册。
- 小体积纯文本（≤5MB，.txt/.md 等）在传输失败时保留本地读取降级（内容完整则视为 OK），既有行为不回归。

### SidecarTimeoutBudget（NEW，纯核心 `compliance/domain`）

纯函数：`secondsFor(baseSeconds, originalSizeBytes) -> long`。

| 输入 | 输出 |
|---|---|
| 0 < size ≤ 50MB | `baseSeconds`（默认 60，环境变量 `app.compliance.sidecar.timeout-seconds`） |
| size > 50MB | `min(1800, baseSeconds×5 + (sizeMB − 50) × 3)` |
| size ≤ 0（探测失败/未知） | `baseSeconds×5`（大文件档下限，保守） |
| 上限 | 1800s |

示例（base=60）：50MB→60s；51MB→303s；165MB→645s；177MB→681s；441MB→1473s；565MB→1800s（封顶）。

**判定口径**：使用**原始上传文件大小**（媒体瘦身前），避免 docx 瘦身后掉档。

### 核查任务（既有，不改结构）

`compliance_check_results`：`status`（INIT/PARSING/CHECKING/COMPLETED/FAILED）、`progress`、`current_step`、`error_message` 字段与状态机不变；变化仅在 FAILED 时 `error_message` 的语义准确化。

**状态与分类映射**：
- 解析成功 → 继续 CHECKING → COMPLETED（既有流程）。
- 任一册非 OK → FAILED，`error_message` 按 contracts/parse-outcome-contract.md 生成（指名册 + 分类动作）。

## 无数据库变更

本特性不新增表/列/索引，无 Flyway 迁移与回滚脚本。
