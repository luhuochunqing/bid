# Implementation Plan: AI 评分标准解析 V3 验收缺口修复 (Spec 042)

## 架构与分层设计

### 1. 前端（Vue 3 + Composition API）
- **`useScoreParseDrawer.js`**：
  - 数据模型统一：`actualScore` 作为客观项得分唯一字段，主观项统一展示 `待确认`。
  - 打开状态机时序：`GET /items` → 判断标书与已有打分 → 仅在有标书且无打分记录时自动触发打分，已有记录直接展示。
  - 动态状态同步：阶段 2 根据 `result.status` 动态计算 `satisfiedCount`、`riskCount`、`unknownCount`。
- **`ScoreParseTable.vue`**：
  - 修复 `getScoreText` / `getScoreClass` 读取 `actualScore`。
  - 合计行 8 列对齐原型，权重 ≠ 100 时显示告警文案。
  - 符号体系对齐：`✓ 满足` / `✗ 不满足` / `• 待确认`。
- **`ScoreItemDetailModal.vue`**：
  - 修复 `quote` 为空时显示 `标书引用：无`。
  - 阶段 1 依据区域展示紫色 `知识库命中` 徽标。
  - 修改建议仅在阶段 2 不满足/待确认时展示，移除无谓的硬编码兜底。
- **`ProjectTaskBoardCard.vue`**：
  - 生产入口展示阶段 1 不满足项的红色数字徽标。
  - 按钮文案统一为 `AI 评分标准解析`。

### 2. 后端（Java 21 + Spring Boot + JPA）
- **`ScoreScoringAppService.java`**：
  - 识别现网 `BID` 分类投标文件（兼容 `BID_FILE`、`BID_DOCUMENT`）。
  - 优化标书文本输入：按评分项关键词/语义检索相关段落，替换固定 12000 字硬截断。
  - 阶段 2 计算依据 PRD §3.4 类型公式与 `matchRatio`。
- **`CertMatchService.java` & `EstimatedScoreService.java`**：
  - 等级提取与匹配；
  - 过期证书标记为命中（`kbHit=true`），状态设为待确认，依据标明过期提示。
- **`OpenAiScoreAnalyzer.java` & `ScoreParseAppService.java`**：
  - 补全四路召回（关键词、结构、语义、全文），避免漏召。
  - 增强分值闭环（按维度）与编号连续性校验，缺项自动二次回补。
- **`ScoreParseTaskStateService.java` & `scoreParseTask.js`**：
  - 超时与错误文案严格匹配 PRD 原文。

---

## 验证与门禁标准
- 单元测试：`mvn test -Dtest=com.xiyu.bid.scoreparse.**.*Test` 必须全绿。
- 前端测试：`npm test src/composables/projectDetail/` & `npm test src/views/Project/stages/` 必须全绿。
- 门禁合规：通过 Token Coverage、TDD 覆盖、Doc Governance 等所有本地 pre-commit / pre-push 门禁。
