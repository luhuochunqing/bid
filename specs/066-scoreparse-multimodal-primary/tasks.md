# Tasks: 多模态模型主导评分项解析

- [x] T001 完成 spec/plan/data-model/contract/checklist。
- [x] T002 [US1] 先写多模态事实验证器红测，再实现纯核心验证器。
- [x] T003 [US3] 先写主客观分类红测，再修正独立分类策略。
- [x] T004 [US1] 实现单次多模态结构化输出适配器及输入裁剪。
- [x] T005 [US2] 接入 Evidence Pipeline；模型/验证失败只保存复核事实，禁止旧正则发布。
- [x] T006 [US2] 覆盖 AUTO_APPROVED 自动落库与 REVIEW_REQUIRED 不覆盖测试。
- [x] T007 [US5] 完整展示候选、差异、证据并支持编辑后重验采纳。
- [x] T008 [US4] 证书/业绩返回具体记录和逐条件 PASS/FAIL/MISSING_EVIDENCE。
- [x] T009 金标回放（12 cases / 73 facts）及全量门禁；五个生产项目待部署后验收。
- [x] T010 完成跨制品一致性检查、三轮独立代码/生产风险审查并关闭全部阻断；实现决策已沉淀到本 feature spec 与执行计划。
