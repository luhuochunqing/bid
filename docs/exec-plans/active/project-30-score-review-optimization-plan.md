# 项目 30 AI 评分解析“215分/30项/维度破折号”优化与修复执行计划

## 一、背景与问题现场

- **问题地址**：`https://winbid.ehsy.com/project/30`
- **招标文件**：`中国电子2026年电商服务供应商入围招标项目 招标文件发售版 20260717.docx`
- **当前页面现场表现**：
  1. 页面提示：`💡 检测到待人工复核的候选评分标准（共 30 项，合计 215 分）`；
  2. 页面按钮：`⚡ 一键采纳为正式评分标准 (215分)`；
  3. 候选表格：
     - 序号 1 为 `商务部分 (30分)`（一级大类汇总行，分值 30 分）；
     - 序号 2 为 `业绩`（具体细则，分值 6 分）；
     - 序号 3~6 评分维度显示为破折号 `—`（财务状况 3分、企业资质 3分、上架响应 1分、授信垫付 2分）；
     - 序号 7 为 `技术服务部分 (45分)`（一级大类汇总行，分值 45 分）；
  4. 正式评分标准提取为 0 项，状态为 `待人工复核 (REVIEW_REQUIRED)`。

---

## 二、全链路根因溯源（关联 PR）

1. **使项目 30 能够被成功解压的 PR**：`!2697`（`agent/trae/fix-project30-scoreparse-truncated-docx`）
   - 修复了 docx 截断缺失 EOCD 导致的 Sidecar 崩溃挂死与 Java 降级乱码，使得底层能解压出 14 张表格。
2. **使候选卡片与采纳按钮展示出来的 PR**：`!2692`（`feat(scoreparse): 支持待复核候选评分标准前端预览与人机一键采纳落库`）
   - 在系统判定为 `REVIEW_REQUIRED` 时，新增 `ScoreCandidateReviewCard.vue` 与一键采纳闭环。
3. **数据失真核心根因（P0 Bug）**：
   - 真实招标文件 Table 2 的末级细则为 **28 项**，满分正好为 **100.0 分**；
   - 底层 `DeterministicScoreFactExtractor.java`（commit `447be1fe5`）为合并单元格大类提取了带有总分的 `parentFact`（商务 30分、技术 45分等汇总行，共 115分）；
   - **PR !2692** 在前端 `useScoreCandidateAdoption.js` 和后端 `ScoreReviewAdoptionPolicy.java` 中，计算候选集时直接写成 `filter(f => f.weight > 0)`，**漏掉了对 Parent Fact（父项）的排除**；
   - 导致 28 项细则（100分）+ 2 项大类汇总（115分）被重复相加，形成了 **30 项、合计 215 分** 的错误展示；
   - 若用户点击一键采纳，会将 215 分脏数据直接写入正式 `score_item` 表。
4. **维度显示破折号 `—` 根因（P1 Bug）**：
   - 序号 3~6 为合并单元格续行（Continuation Rows），其单元格文本为空；
   - `DeterministicScoreFactExtractor.java` 在 Pass 2 中关联父项时，仅调用了 `fact.withParentFactId(parentFactId)`，**未将 Origin 单元格的有效维度回填给续行**，导致续行 `dimension` 保持为空串，前端 fallback 显示为破折号 `—`。

---

## 三、修复目标与交付契约

1. **精准过滤 Parent Fact**：候选评分标准列表与一键采纳严格只保留末级叶子项（Leaf Facts），排除被引用的父项汇总，项数精准回到 **28 项**，合计分值精准回到 **100.0 分**；
2. **维度自动继承**：合并单元格续行自动继承父级维度，序号 3~6 维度正常显示为“商务部分”，消灭所有的破折号 `—`；
3. **一键采纳与展示防呆**：候选总分与 100 分不符时前端展示醒目警示，后端强校验禁止落库父节点；
4. **全自动化自愈验证**：维度与父子关联闭环后，推动 Table 2 达到自洽门禁，探索直接 `AUTO_APPROVED` 自动落库。

---

## 四、具体实施代码修改清单

### 1. 前端 Composable 修复：排除父项
**文件**：`src/composables/projectDetail/useScoreCandidateAdoption.js`
- 逻辑：
  ```javascript
  const parentIds = computed(() => {
    return new Set(reviewFacts.value.map((f) => f.parentFactId).filter(Boolean))
  })

  const candidateFacts = computed(() => {
    return reviewFacts.value.filter(
      (f) => Number(f.weight) > 0 && !parentIds.value.has(f.factId)
    )
  })

  const candidateTotalWeight = computed(() => {
    const sum = candidateFacts.value.reduce((acc, f) => acc + (Number(f.weight) || 0), 0)
    return Math.round(sum * 100) / 100
  })
  ```

### 2. 后端采纳策略加固：排除父项
**文件**：`backend/src/main/java/com/xiyu/bid/scoreparse/domain/ScoreReviewAdoptionPolicy.java`
- 逻辑：
  在 `adopt` 方法遍历候选前，提取集合：
  ```java
  Set<String> parentIds = facts.stream()
          .map(CandidateInput::parentFactId)
          .filter(id -> id != null && !id.isBlank())
          .collect(Collectors.toSet());
  ```
  遍历时，若 `parentIds.contains(fact.factId())`，直接跳过，严禁转为 `ScoreCandidate`。

### 3. 提取器续行维度继承：消灭破折号 `—`
**文件**：`backend/src/main/java/com/xiyu/bid/scoreparse/domain/DeterministicScoreFactExtractor.java`
- 逻辑：
  在 Pass 2 的 resolved 构建循环中，若续行 `dimension` 为空，从 origin cell 回填维度：
  ```java
  String resolvedDim = fact.dimension();
  if (resolvedDim == null || resolvedDim.isBlank()) {
      String originCellId = dimensionOriginCellIdByRow.get(row);
      Integer originRow = originRowByCellId.get(originCellId);
      if (originRow != null) {
          ScoreFact originFact = parentFactByOriginRow.getOrDefault(originRow, factByRow.get(originRow));
          if (originFact != null && !originFact.dimension().isBlank()) {
              resolvedDim = originFact.dimension();
          }
      }
  }
  resolved.add(fact.withParentFactId(parentFactId).withDimension(resolvedDim));
  ```

### 4. 前端卡片提示与防呆
**文件**：`src/views/Project/stages/components/ScoreCandidateReviewCard.vue`
- 若 `candidateTotalWeight !== 100`，展示告警条，提示用户核实；采纳按钮文案同步显示过滤后的实际叶子总分。

---

## 五、测试与验证清单

1. **前端测试**：
   - 运行：`npm test src/composables/projectDetail/useScoreCandidateAdoption.spec.js`
   - 补充测试用例：模拟包含 `parentFact`（30分）和 `childFact`（6分）的集合，断言 `candidateFacts` 仅包含 1 项（6分），`candidateTotalWeight` 为 6 而非 36。
2. **后端测试**：
   - 运行：`mvn -f backend/pom.xml test -Dtest='ScoreReviewAdoptionPolicyTest,DeterministicScoreFactExtractorTest'`
   - 补充用例：
     - `ScoreReviewAdoptionPolicyTest`：输入 parent + child 事实，断言 parent 被剔除，采纳总分不含 parent；
     - `DeterministicScoreFactExtractorTest`：验证合并单元格续行（Continuation Row）继承 Origin 的有效维度。
3. **全量门禁**：
   - `npm run check:line-budgets -- --working-tree`（确认符合 300 行预算约束）
   - `bash scripts/pre-push-gate.sh`
