# 任务
- [x] T1 TextPrimaryEvidenceBinderTest绑定→mapper原文恢复→采纳链路、ScoreReviewAdoptionPolicyTest空原文拒绝、ScoreParseTable.spec.js编号展示的RED。
- [x] T2 LogicalRowEvidenceBinder闭合唯一物理行绑定；ScoreReviewAdoptionPolicy拒绝空/—原文采纳；ScoreParseTable仅转换描述性编号展示；相邻GREEN。
- [x] T3 固定原标书/真实价格引文回放、重复/篡改/跨方案/未闭合反例、脱敏Golden及隔离旧类重引入实验。
- [ ] T4 全scoreparse、前端/build/架构已通过；全仓10545项：10488通过/0失败/40环境错误/17跳过；错误在未改基线复现，独立MySQL就绪后40项重跑全部通过；正常推送门禁待提交后执行。
- [ ] T5 既有lessons/Wiki回填与独立全diff审查已完成；提交PR待T4收尾。
- [ ] T6 用户部署后核对新任务/原文、采纳审计、DB/API/页面编号与权重及外部配置/跨角色验收（待部署）。
