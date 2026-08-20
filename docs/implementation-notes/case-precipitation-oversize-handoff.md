# Handoff: AI 自动生成案例「入库 0 条」——成功生成案例路径攻坚（方案A）

> 提交时间：2026-08-20
> 提交人：Trae Agent（fix-case-precipitation-oversize-oom / fix-docinsight-recover-noise）
> 接收方：接手「西域数智化投标管理平台」案例沉淀功能的 Agent
> 状态：**未完成，需继续攻坚**

---

## 1. 需求目标（用户想要的）

测试环境项目 **1350（"ai测试01"）** 点击【AI自动生成案例】后，期望**成功生成并入库案例**，而不是「入库 0 条」或「失败通知」。

用户明确反馈：
> 「这才是方案：**成功生成案例**。现在的方案把所有标书（100% 超过 30MB，几百MB，最大 2G，当前是 400MB）都拒了，不是我要的效果。」

**核心矛盾**：标书文件**全部超 30MB**（几百MB～2G）。任何「设上限拦截」的方案都会拒绝全部真实数据，无法满足需求。

## 2. 现状：已合入的修复（PR !2357，含方案A渐进 + 方案B）

PR !2357（`!2357 fix(case): 修复 AI 生成案例超大标书 OOM`）已合入 main 并部署测试环境。
PR #2358（方案B）已创建待合入。

### 已修复的真实 bug 链（从 OOM 到 403 到 二次编码）

1. **修 A 破 B**：OBS 预签名 URL 二次编码（`getForObject(URI, ..)` 而非 `getForObject(String, ..)`，避免 `%` 被 UriTemplateHandler 二次编码 → 403 SignatureDoesNotMatch）——已修复。
2. **OOM 根因**：`RestTemplate.getForObject(byte[].class)` 将整文件读进堆；`fallbackExtract` 对二进制 `new String(content, UTF_8)` 二次分配，400MB 标书**整读堆 OOM**。
3. **现行兜底（方案A渐进，非最终方案）**：`MarkItDownSidecarExtractor.extract` 入口加大小防护，超过 `app.doc-insight.sidecar-max-upload-mb`（默认 **30MB**）抛 `DocInsightException`（确定性失败、不重试、不降级占位文本），由 `ProjectClosedEventListener` 走失败通知。
4. **方案B（PR #2358）**：新增 `@Recover(DocInsightException)` 原样 rethrow，消除 `ExhaustedRetryException: Cannot locate recovery method` 日志噪音。

### 关键文件

- `backend/src/main/java/com/xiyu/bid/casework/application/ProjectClosedEventListener.java`
  - `loadBidFileMarkdown()`：读 OBS 标书 → 调 `docTextExtractor.extract`；OBS 提取失败 **rethrow**（不再回退占位文本假成功）。
  - `loadFileBytes()`：OBS 走 `restTemplate.getForObject(URI.create(signedUrl.get()), byte[].class)`。
- `backend/src/main/java/com/xiyu/bid/docinsight/infrastructure/extractor/MarkItDownSidecarExtractor.java`
  - `maxUploadBytes` 字段（默认 30MB），`extract` 入口超限抛 `DocInsightException`。
  - 新增 `@Recover(DocInsightException)` 原样 rethrow。
- 文档沉淀：`.wiki/pages/docinsight-engine.md §6`、`docs/lessons/lessons-learned.md §132`。

## 3. 当前行为（非最终目标，但已描述为"现状"）

- 标书 ≤30MB：可提取 → 可入库。✅ 请求成功链路正常。
- 标书 >30MB（**全部真实标书**）：抛 `DocInsightException` → `ProjectClosedEventListener` 捕获 → `notifyFailure` → 用户看到「失败通知」（不再 OOM，也不再假「入库 0 条」成功）。
- 已复测：项目 1350 复测，走失败通知路径（不再 OOM，但**未成功入库**——这正是用户不满意处）。

## 4. 为什么还达不到「成功生成案例」？

三条独立约束叠加，**任一条不解除都无法成功**：

| # | 约束 | 现状 | 影响 |
|---|------|------|------|
| 1 | Sidecar 上传上限 | sidecar 端 `SIDECAR_MAX_UPLOAD_MB` 默认 **30MB**，超限 multipart 必被 413 | 全部标书无法走 sidecar 转 Markdown |
| 2 | 整文件读堆 | 后端 `getForObject(byte[].class)` + `loadFileBytes` 整文件读 JVM 堆 | 400MB 标书 OOM；2G 直接不可行 |
| 3 | fallback 二次分配 | `fallbackExtract` 对二进制 `new String`, UTF-8 再分配内存 | 放大问题 2 |

## 5. 接续方向（候选方案，供接手 Agent 评估，非定论）

> 目标：让几百MB～2G 的标书**成功提取文本并入库案例**，全程内存可控、不 OOM。

### 方向 A：流式 / 分块下载，绕开「整文件读堆」
- 后端不 `getForObject(byte[].class)`，改为**边下载边写临时文件**（`RestTemplate` execute + `Resource`/流，或直接 OBS SDK 流式下载到磁盘临时文件）。
- 再从**磁盘临时文件**喂给处理链路，避免 400MB/2G 长时间驻留堆。

### 方向 B：提高 / 解除 sidecar 上传上限 + sidecar 端读盘
- 后端把标书写到 sidecar **共享磁盘**路径，sidecar 读本地文件路径而非接收 multipart（彻底绕开 `SIDECAR_MAX_UPLOAD_MB`）。
- 需评估 sidecar 部署形态（是否与后端同机/共享卷）。

### 方向 C：前端分片上传 OBS + 后端仅传 OBS 路径
- 若标书已存 OBS，后端应传 **OBS 文件路径** 给 sidecar 让它直连读，而不回传大字节流。
- 需确认 sidecar 是否具备访问 OBS / 共享存储的能力。

### 方向 D：超长文档切片后再提取
- 对 >30MB 的文档，先按结构/章节切片，切片小到 ≤ 各阶段内存/上传限额后再逐片转换合并。
- 保底可行，但对 2G 文档输入仍须先解决「读取」本身（回到方向 A）。

### 方向 E（若场景允许）：仅对评分项命中区域做局部提取
- 案例沉淀本质是「评分项 + 标书正文对照」，可能无需全文转换；只提取评分项相关章节，减小体积。

## 6. 接手 Agent 建议的下一步

1. **先复现**：测试环境项目 1350，点【AI自动生成案例】，确认当前走失败通知（预期行为，非 BUG），确认 OOM 已消失。
2. **确认 sidecar 的能力边界**：能否访问共享磁盘 / OBS / 支持流式；`SIDECAR_MAX_UPLOAD_MB` 是否可调。
3. **优先验证方向 A（流式写盘）**：最小改动解掉「整文件读堆」，通常是最短路径。
4. 单一方向验证通过后，**再**评估是否需解除 sidecar 上限（方向 B/C）。
5. 改完必须**回归测试环境项目 1350**，符合「成功生成案例、入库 >0」才算关闭。
6. 遵守项目纪律：
   - 真实 API 唯一源（禁止 Mock）。
   - 复杂任务先走 Spec Kit 流程门禁（`PLANS.md`）。
   - 原子提交 + 测试证据，每变必测。
   - 迁移脚本用 `scripts/new-migration.sh`，不手改版本号。

## 7. 参考

- 方案B经验（`@Recover` 匹配机制）：`.wiki/pages/docinsight-engine.md §6.2`、`docs/lessons/lessons-learned.md §132`。
- `docs/lessons/lessons-learned.md §132`（OOM 根因 + 大小防护现状）。
- PR：!2357（方案A渐进+OOM修复）、#2358（方案B，当前分支）。