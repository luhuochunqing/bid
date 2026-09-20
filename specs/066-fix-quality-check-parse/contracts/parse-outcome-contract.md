# Contract: 解析结果分类 → 用户文案 → 动作

**Feature**: 066-fix-quality-check-parse | **Date**: 2026-09-20

本契约锁定「AI 标书质量核查」解析失败的用户可见语义。任何实现改动导致下表行为变化即视为契约破坏，必须有对应测试断言。

## 1. 失败分类契约

| Kind | 用户文案（error_message）模板 | 禁止出现 |
|---|---|---|
| `TIMEOUT` | `投标文件解析超时：{fileName}（{sizeMB}MB），本次未在等待上限（{limit}s）内完成（可能因文档较大或解析服务繁忙），请稍后重试；文件本身无需重新上传。` | 「文件可能打不开」「重新上传」（除稍后重试外不得引导重传） |
| `SERVICE_ERROR` | `投标文件解析服务异常：{fileName}（{detail}），请稍后重试；若持续出现请联系管理员。` | 任何指向文件损坏/重传的表述 |
| `SERVICE_ERROR`（413 超上限） | `投标文件超过解析服务大小上限：{fileName}，当前无法解析。` | 「稍后重试」（重试不会成功） |
| `EMPTY_TEXT` | `未能从投标文件提取文本内容：{fileName}。该文件为图片扫描件或无可复制文本（无文本层），当前 AI 质量核查不支持此类文件，请提供可复制文字的版本后重新核查。` | 「请确认文件可正常打开后重新上传」 |
| `UNPARSEABLE` | `投标文件无法解析：{fileName}，请确认文件可正常打开后重新上传。` | —（这是唯一允许引导重传的解析分类） |
| 存储缺失（既有 resolver 分类） | `投标文件在存储中不存在，请重新上传：{fileUrl}` / `内容为空，请重新上传` | —（允许引导重传） |

**规则**：
- R1：多册场景，任一册失败即整体失败，文案必须包含失败册文件名。
- R2：`TIMEOUT`/`SERVICE_ERROR`/`EMPTY_TEXT` 三类文案不得建议重新上传同一文件。
- R3：`{sizeMB}`、`{limit}s`、`{detail}` 为诊断信息，必须来自真实测量（原始文件大小、生效上限、HTTP 状态或异常摘要）。

## 2. 等待上限契约

| 原始文件大小 | 生效上限（base=60 默认） |
|---|---|
| ≤50MB | 60s |
| 100MB | 450s |
| 165MB | 645s |
| 441MB | 1473s |
| ≥550MB | 1800s（硬上限） |
| 未知（探测失败） | 300s（大文件档下限，保守） |

- R4：上限必须按**原始文件大小**判定（媒体瘦身不降档）。
- R5：上限可通过 `APP_COMPLIANCE_SIDECAR_TIMEOUT_SECONDS`（绑定 `app.compliance.sidecar.timeout-seconds`）调节，无需改代码。
- R6：同一文件重复触发时上限恒定（确定性）。
- R12：分类边界——`HttpConnectTimeoutException`（连接建立超时）归 `SERVICE_ERROR`，仅请求读超时归 `TIMEOUT`；2xx 但 JSON 非法归 `SERVICE_ERROR`；401/413 归 `SERVICE_ERROR`（413 文案不承诺重试）；400/422 等文档级 4xx 归 `UNPARSEABLE`。
- R13：`EMPTY_TEXT` 判据 = markdown 空且 pages 全空；`low_text_density` 警告仅作 detail 补充证据，不单独决定分类（避免误杀合法短文档）。

## 3. 前端等待契约

- R7：前端轮询窗口 ≥ 30 分钟；达到窗口上限时不得展示「核查任务执行异常/未完成」错误卡片，应提示「任务仍在后台执行，可稍后刷新查看」。
- R8：任务在窗口内 COMPLETED → 正常加载报告（既有行为）。
- R9：用户关闭并重开抽屉时，按任务真实状态恢复（PARSING/CHECKING → 继续展示进度）。

## 4. 可观测性契约

- R10：Sidecar 每次转换（`/convert`、`/convert-stream`）必须记录一条结构化日志：`file`、`size`、`duration_ms`、`chars`、`pages`、`warnings`；空文本时升级为 WARN。
- R11：Java 适配器每次调用必须记录：`kind`、原始文件大小、生效上限、耗时；失败分类附带 detail。
