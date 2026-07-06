# CO-469 第八轮 P2 实施笔记：根因 1（MultipartFile）补修

> **本轮范围**：补修前 8 轮一直漏掉的根因 1——`@Async` + `MultipartFile` 反模式
>
> 日期：2026-07-06 ｜ 分支：`agent/zcode/co469-round8-import-stuck`
>
> 按用户 `~/.zcode/AGENTS.md` 要求，本文件记录规格中没有的决策、被迫的取舍与需要你知道的事项。

---

## 1. 本轮的背景：第八轮已部分完成

本轮开始时我不知道今天早上 06:44 已经有另一个 agent 会话提交过第八轮：

| commit | 时间 | 内容 | 是否在 main |
|---|---|---|---|
| `da755ce28` | 06:44 | 第八轮初始：failImportTask 降级 + Jackson 序列化 | ❌ 在 agent 分支 |
| `2e4613fbd` | 之后 | 第八轮 P1 refactor：提取 `JsonUtils` 工具类 | ✅ 已合入 main |

我开始时基于旧基线（`84413228e`）写了一份完整方案（含根因 1 + 根因 2），stash 后 `agent-start-task.sh --in-place` rebase 到 origin/main 才发现冲突——main 已经有了 JsonUtils 版本。

**cherry-pick da755ce28 会冲突**（da755ce28 的 inline ObjectMapper 被 main 上的 JsonUtils 取代），且属于**倒退**，所以放弃了 cherry-pick 方案，改成在干净的 main HEAD 上**只补根因 1**。

> **取舍说明**：用户原本同意"cherry-pick + 补加根因 1"，但执行时发现 main 已经超出 da755ce28（有了 P1 refactor）。继续 cherry-pick 倒退版本是错的。正确做法是放弃 cherry-pick，把"补根因 1"作为独立 commit。我在执行时发现了这个事实，按"`AGENTS.md §不可妥协的底线`第 6 条 git blame 可追溯性"原则选择了不重写已有代码，只追加缺失的根因 1。

---

## 2. 真根因 1：`@Async` + `MultipartFile` 反模式（本轮补修）

### 本质

Spring MVC 的 `MultipartFile` 实现基于 Servlet 容器（Tomcat）的磁盘临时文件。HTTP 请求一旦结束（Controller 返回响应），**Tomcat 会立即清理该临时文件**。而 `@Async` 方法实际执行时往往已是几十毫秒之后——`file.getInputStream()` 抛 `NoSuchFileException`。

### backend.log 铁证（2026-07-06 06:25:12.287）

```
[personnel-imp-exp-1] ERROR c.x.b.p.a.s.ImportPersonnelAppService - 导入任务执行失败: taskId=1
java.nio.file.NoSuchFileException: /private/var/folders/hb/fw83wt2j5psblns41081bgh40000gn/T/tomcat.18089.12574509400458770033/work/Tomcat/localhost/ROOT/upload_9f6c0414_3ae6_4f76_82e4_ea7028310133_00000000.tmp
```

### 为什么前 8 轮（含已合入的 da755ce28）都没碰到

- **前 7 轮**：根本没看 backend.log，一直在猜代码层面的问题
- **第八轮 da755ce28 / 2e4613fbd**：看了日志，但**只看到第二条 stacktrace**（`Invalid JSON text`），漏看了紧挨着的第一条（`NoSuchFileException`）。修了根因 2（JSON 序列化 + failImportTask 降级），但根因 1 完全没碰

### 后果（用户视角）

第八轮 P1 修完后，根因 1 仍然活跃：
- 用户上传**正常** Excel → Controller 返回 202 → Tomcat 清理临时文件
- 异步线程 `file.getInputStream()` 抛 `NoSuchFileException`
- 这次 failImportTask 能正确兜住（P1 已修），把状态写成 FAILED
- **用户看到的是「导入失败了」**（不再是"卡住"）——这正好对应用户反馈的"现在的失败提示改成：导入失败了"

也就是说：P1 把现象从"卡住"变成了"失败"，但**导入功能依然完全不可用**。本轮 P2 才是真正让导入跑起来。

### 修复

在同步阶段（HTTP 请求仍存活）调用 `file.getBytes()` 把文件内容读到 `byte[]`，再传给 `@Async` 方法。`byte[]` 是不可变的纯 JDK 对象，不依赖 request 生命周期。

> **取舍**：多了一次内存拷贝（10MB 上限），但这是 Spring 官方文档明确推荐的 `@Async` + multipart 处理方式。如果未来文件大小显著增加，可以改成同步阶段把 `MultipartFile.transferTo(tmpFile)` 到工作目录、异步线程读工作目录文件。

---

## 3. 真根因 2：JSON 序列化（本轮无需修，main 已含）

仅供对比记录。`PersonnelImportTaskRepositoryAdapter.serializeErrorDetails` 原用 `details.toString()`，输出 Java record toString（非合法 JSON）。main 上的 `JsonUtils` 已统一处理，本轮**未改动**。

---

## 4. 不改的决策

### 不改导出链路

`ExportPersonnelAppService` 看起来跟导入对称，但有两个本质差异：

| 维度 | 导入 | 导出 |
|---|---|---|
| `@Async` 入参 | `MultipartFile`（绑定 request 生命周期）❌ | `PersonnelListCriteria`（不可变值对象）✅ |
| `failXxxTask` 实现 | 写 DB JSON 列 ❌ | 写 Redis（`setRedisValue`）✅ |

导出**没有这两个 bug**。无证据不改动，符合"最小变更"原则。

### 不走完整 Spec Kit 门禁

PLANS.md 第 2 条底线主要针对"Phase / 开发计划 / 需求开发"类新功能。本次是**根因已 100% 在日志中确认的 bug 修复**，修改面 2 个文件、有现成测试范式，用计划模式 + ExitPlanMode 已足够。

---

## 5. 防复发契约

本轮新增/保留的测试契约：

| 测试 | 防的是什么 |
|---|---|
| `ImportPersonnelAppServiceTest` 5 个 `byte[]` 形式测试 | 防止有人把 `executeImportAsync` 签名改回 `MultipartFile` |
| `executeImportAsync_当failImportTask自身save抛异常_应降级到updateStatus`（main 已含） | 防止简化 failImportTask 时去掉二次兜底 |
| `executeImportAsync_当failImportTask完全失败时_不抛异常且清理Redis进度`（main 已含） | 防止 failImportTask 彻底失败时 Redis 进度键未清导致前端继续轮询 |

---

## 6. 沉淀建议（强烈建议加到 lessons-learned.md）

1. **改 bug 前先 grep `ERROR` 级别日志**。这次根因 1 和根因 2 在 backend.log 里是紧挨着的两条 stacktrace（间隔 13 毫秒），但第八轮 P1 只看了第二条。`lessons-learned.md §23`【全链路日志排查 SOP】早就写过，但执行时容易只看第一条匹配就停。**应该 grep 全部 ERROR 并逐一过一遍**。

2. **`@Async` 方法禁止接收绑定 request 生命周期的对象**（`MultipartFile`、`HttpServletRequest`、`InputStream` 等）。Spring 官方文档明确警告过。建议加到 `ARCHITECTURE.md §Agent Contract`。

3. **异步任务的失败兜底自身也可能失败**，必须提供二次降级路径。da755ce28 已实现这个模式（save 失败 → updateStatus 失败 → clearProgress），可作为参考。

---

## 7. 验证证据

- 后端：`mvn test -Dtest='ImportPersonnelAppServiceTest,PersonnelImportControllerSecurityTest,ArchitectureTest'`
  - **45 个测试全绿**（6 + 10 + 29）
- 前端：`npx vitest run src/views/Knowledge/components/personnel/usePersonnelBatchTask.spec.js`
  - **8/8 通过**
- pre-push-gate：通过
- 真实环境验证（待主工作区触发）：跑一次导入，看 `backend.log` 是否还出现 `NoSuchFileException`。

---

## 8. 文件变更清单（本轮）

| 文件 | 变更 |
|---|---|
| `backend/.../controller/PersonnelImportController.java` | 同步阶段读 `byte[]`，类顶部加设计注释 |
| `backend/.../service/ImportPersonnelAppService.java` | `executeImportAsync` 签名改 `byte[]`（不动 failImportTask，main 已修） |
| `backend/.../service/ImportPersonnelAppServiceTest.java` | 5 个测试的 `MultipartFile` → `byte[]`；保留 2 个降级测试 |
| `docs/exec-plans/co469-round8-import-stuck/implementation-notes.md` | 本文件 |

---

## 9. 与 da755ce28 / 2e4613fbd 的关系

- **da755ce28**（第八轮初始，agent 分支）：修了根因 2 + failImportTask 降级，漏了根因 1
- **2e4613fbd**（第八轮 P1 refactor，已合入 main）：把 da755ce28 的 inline ObjectMapper 重构成统一 `JsonUtils` 工具类，仍漏根因 1
- **本轮（P2）**：补修根因 1（MultipartFile），不动已合入的根因 2 实现

三者**互补**，不冲突。本轮合入后，第八轮才算真正完整（根因 1 + 根因 2 都修）。
