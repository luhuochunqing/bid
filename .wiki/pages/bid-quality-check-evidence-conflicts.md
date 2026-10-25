---
title: 标书质量核查证据冲突与组合资质裁决
space: engineering
category: lessons
tags: [质量核查, compliance, CA, 保证金, ISO, 评分解析]
sources:
  - backend/src/main/java/com/xiyu/bid/compliance/domain/policy/BasicInfoCheckPolicy.java
  - backend/src/main/java/com/xiyu/bid/compliance/domain/policy/DeterministicCaCertificateExtractor.java
  - backend/src/main/java/com/xiyu/bid/compliance/domain/policy/QualificationMatchPolicy.java
  - backend/src/main/java/com/xiyu/bid/compliance/domain/policy/QualificationDetailReconciler.java
  - backend/src/main/java/com/xiyu/bid/compliance/domain/policy/EvidenceAssembler.java
  - src/views/Project/stages/components/QualityCheckDetailDialog.vue
  - backend/src/main/java/com/xiyu/bid/compliance/domain/policy/SubItemAndAttachmentRules.java
  - backend/src/main/java/com/xiyu/bid/compliance/service/QualificationQueryDelegate.java
  - specs/077-fix-ai-quality-1117/spec.md
backlinks:
  - .wiki/pages/_index.md
  - .wiki/log.md
created: 2026-10-23
updated: 2026-10-26
health_checked: 2026-10-26
archive: false
---

# 标书质量核查证据冲突与组合资质裁决

## 根因矩阵

| 现象 | 根因 | 裁决契约 |
|---|---|---|
| 空格日期报缺失 | 只识别数字紧邻“年” | 识别完整年月日并允许空白 |
| 电子标被判纸质标 | 负向模板词首次命中即返回 | 汇总正反证据，明确电子/CA 证据优先 |
| 保证金回单报缺失 | 文本未抽取等同附件不存在 | 复用页面图像提取真实交易要素；视觉不可用或要素不足时转人工 |
| 三项 ISO 只关联一项 | 长要求未原子化 | 三项标准分别匹配有效证书 |
| 评分类型错误 | 页面读取旧激活解析 | 分类器与生产激活版本分别核验 |
| 临期降级文案自相矛盾 | 细分不彻底，统一拼接“证明待核实”且残留“匹配通过” | 细分 ATTENTION 为临期与未维护；清洗残留冲突短语 |
| 标书证据命中目录页 | 资质与否决检索未跳过目录索引页 | 全面切换为 `locateBidSkippingCatalog`，仅正文命中才返回页码 |
| 资质核查显示业绩库来源 | 装配未设白名单，前端未阻断 | 后端白名单限定源（QUALIFICATION 仅放行资质库），前端纵深防御 |

## 不变量

- `MANUAL_REVIEW` 表示证据通道不足或需人工复核状态，不是业务材料缺失。
- 证书临期触发需关注（ATTENTION）时，精准归因为证书临期与换证复核，禁止报“标书证明材料待核实”；且结论中禁止残留“资质匹配通过/完全满足”等矛盾表述。
- 标书正文证据装配禁止将目录页（带引导点线）作为有效定位；若正文未命中，诚实置空页码由前端展示“未定位”。
- 纯资质检查项（`QUALIFICATION_MATCH`）的证据装配仅允许资质库（`SOURCE_QUALIFICATION`），禁止业绩库等异构库来源穿透。
- 保证金文本关键词不能单独产生 PASS；视觉凭证必须包含至少三项交易要素，且包含金额或银行流水号。
- 单张证书不能代表多个 ISO 标准，过期证书不得关联。
- 新解析候选不等于生产事实；部署后必须核验并显式激活。

## 副作用（Cross-Module Impact）

- CA 提取同时影响招标要求融合与 CA 台账联动。
- ISO 原子化同时服务知识库查询和结构化明细，禁止双轨漂移。
- 证据装配跳过目录页影响资质比对与一票否决项的页码提取，避免假定位引导。
- 前端过滤必须精确限定为 `QUALIFICATION_MATCH`/`QUALIFICATION_VETO`，放行包含业绩库的 `QUALIFICATION_REVIEW` 综合审查项。
- 代码修复不会回填已持久化报告或评分项。

## 验证入口

```bash
cd backend
mvn -Dtest=BasicInfoAndAttachmentRulesTest,DeterministicCaCertificateExtractorTest,DepositReceiptVisionFusionPolicyTest,OpenAiDepositReceiptVisionDetectorTest,QualificationMatchPolicyTest,QualificationDetailReconcilerTest,EvidenceAssemblerTest,QualificationQueryDelegateTest,ScoreTypeClassificationPolicyTest,ResponsibilityArchitectureTest test
```

## 2026-10-25 生产复验与 CA 平台名称修复

- 生产 `f3ff316f2`、项目1117报告56、任务`6588f79d527646afb544a866de96f249`于13:50:40完成。保证金回单第12页视觉PASS，但CA仍因平台“或称交易平台”错误判FAIL。该生产报告是修复前证据。
- 招标PDF SHA-256 `040c608b9465876952a8fd1d383fe652756868209486827761841cedca705d33`，原文第4页“招商局集团电子招标采购交易网（或称交易平台）”；第10页递交条款与第31页操作指引使用同一系统名称。台账使用“招商局集团电子招采平台”，记录65/66有效且在库，到期2027-09-08。
- 纯核心`CaPlatformNamePolicy`按名称定义、实际投标上下文、普通候选顺序取证；公告发布网站不能盖过实际递交平台，同级冲突转人工。地域CA候选限明确地区，不把“采用CA数字证书”“办理CA数字证书”当平台。
- `CaCertificateRequirement`携带原文页码证据，经`DeterministicRequirementsFuser`透传；确定性平台未知时不恢复LLM猜测。
- CA台账查询失败在正文占位检查之后处理，避免把独立缺陷FAIL覆盖成人工状态。未知平台不得因无关过期台账判定失败。
- 本轮工作为隔离沙盒中的源码修复；代码发布、生产重新核查及业务验收仍需独立完成。

- **历史未闭合边界（已按下述用户裁决处理）**：第三轮独立审查曾复现“电子招标投标交易平台”“本次交易平台”误判具体平台并FAIL。当前候选代码仅证明完整项目1117输入回放PASS；通用泛称防护未完成，不可声明整体修复或发布。见`docs/reviews/2026-10-25-ca-platform-review.md`。

## 用户裁决后的最终边界

- 用户批准无可核实平台身份统一MANUAL_REVIEW。新增正向身份边界，形似公司/平台或台账matched=true不构成身份核实。未覆盖的新平台也转人工，保留候选与原文。
- 原两个泛称及陌生企业名称均已由JUnit验证转人工；招商完整PDF回放仍PASS、第4页原文保留。关联回归566项通过，两轴独立审查通过。
- 当前是本地源码完成，未commit/push/部署；生产仍需发布后重新核查。最终记录：`docs/reviews/2026-10-25-ca-platform-review.md`。

## 2026-10-25 报告58程序修复候选（未交付）

- 沙盒quality58-program-fix，分支agent/claude/quality58-program-repair，未发布。评分规则误判、选页/页码覆盖、受限扫描转写、退款模板反假已有候选；业务补章/续证/台账未处理。
- 关联835项及冻结PDF回放通过，但第3轮独立审查仍发现授权期限【此处填写】被判PASS。结论NEEDS_FIXES，不能以测试绿宣布修复完成。
- 根因：非空/长度/有限占位词不足以证明授权期限真实填写。建议三态正向证明，未知转人工；人工裁决前停止继续叠补。详见specs/077-fix-ai-quality-1117尾部R58。

## 2026-10-26 人工确认后本地程序验证闭环

- 新正向三态替代非空/负词表外放行，字段无法确认、占位及不同授权页冲突均人工；AI PASS/FAIL均不得覆盖规则未知人工。姓名候选不等于身份验证。
- 退款元标签、括号字段说明、某某/XX掩码不构成企业/银行名称；完整字段不等于账户归属或有效性核验。
- 本地850项、冻结PDF、三架构、构建和变更源码Checkstyle通过，独立两轴审查通过。基线7e0481cd2，分支agent/claude/quality58-program-repair，源码未提交/未部署。
- 生产报告58未在本轮重算。部署后必须另验真实模型、页面和持久化结果，不能把本地通过当业务UAT。
