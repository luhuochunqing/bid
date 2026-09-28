# 评分项解析 3 个月根治计划

**制定日期**: 2026-09-28  
**计划周期**: 2026-10 至 2026-12（共 12 周）  
**目标**: 将评分项解析准确率从当前未知基线（M1 待建立）提升至 ≥95%，将月均 fix commit 从 115（过去 30 天实测）降至 ≤3  
**依据**: `docs/architecture/score-parse-root-cause-analysis.md`（四条根因链）+ git log 实测统计

---

## 执行原则

1. **地基优先**：先修根因1（输入有损）和根因4（测试盲区），再修根因2、3（否则无法验证效果）
2. **增量验证**：每个 milestone 必须有量化指标（准确率/召回率/通过率）对比
3. **双轨并行**：测试基础设施（M1）与输入清理（M2）可并行，其余串行
4. **回退预案**：每个 milestone 完成后打 tag，失败可立即回退
5. **门禁前置**：M1 完成后，所有后续 PR 必须通过真实语料回归测试才能合并

---

## Milestone 1: 建立量化验证基座（Week 1-2，2026-10-01 ~ 10-14）

### 目标
**第一次建立量化基线**，让团队能够用数字回答"这次改动让准确率提升了多少"，而非"这个项目现在能跑通了"。

**重要**: 当前没有任何准确率/召回率的实测数据，本 milestone 的首要任务是产出第一份真实基线报告。

### 核心产出
1. **真实语料回归测试集**（CI 可选、本地必跑，新建）
   - 将项目 33 / 1117 / 1358 / 1359 / 1371 的真实招标文件沉淀为金标 case
   - 每个 case 包含：原始 Word/PDF + 期望的候选列表（JSON）+ dim+weight 配对断言
   - 语料存放位置：`$XIYU_EVAL_CORPUS_DIR/real-corpus/project-{id}/`（**本地目录，禁止入仓**，沿用 `docs/lessons/scoreparse-golden-corpus/index.md` 的既有约定）
   - 测试类：`RealCorpusRegressionTest`（**新建**，继承现有 Golden 框架，沿用 `@EnabledIfEnvironmentVariable(named = "XIYU_EVAL_CORPUS_DIR", matches = ".+")` 门控；CI 无该环境变量时自动跳过，**本地开发机验证是强制项**，不因 CI 跳过而免除）

2. **准确率/召回率追踪脚本**（新建）
   - 脚本路径：`scripts/scoreparse-accuracy-report.mjs`（**新建**）
   - npm script: `npm run scoreparse:accuracy-report`（**新建**）

3. **强化断言规范**（改造现有 Golden 测试基类）
   - 所有 Golden Case 必须包含 `totalWeightAssertion` 和 `dimWeightPairing`

### 详细任务拆解（Day 1-10）

#### Day 1-2: 准备真实语料（产品 + 后端）
