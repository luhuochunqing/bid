# 任务
- [x] T1 TextPrimaryEvidenceBinderTest绑定→mapper原文恢复→采纳链路、ScoreReviewAdoptionPolicyTest空原文拒绝、ScoreParseTable.spec.js编号展示的RED。
- [x] T2 LogicalRowEvidenceBinder闭合唯一物理行绑定；ScoreReviewAdoptionPolicy拒绝空/—原文采纳；ScoreParseTable仅转换描述性编号展示；相邻GREEN。
- [x] T3 固定原标书/真实价格引文回放、重复/篡改/跨方案/未闭合反例、脱敏Golden及隔离旧类重引入实验。
- [ ] T4 全scoreparse、前端/build/架构已通过；全仓10545项：10488通过/0失败/40环境错误/17跳过；错误在未改基线复现，独立MySQL就绪后40项重跑全部通过；正常推送门禁待提交后执行。
- [ ] T5 既有lessons/Wiki回填与独立全diff审查已完成；提交PR待T4收尾。
- [ ] T6 用户部署后核对新任务/原文、采纳审计、DB/API/页面编号与权重及外部配置/跨角色验收（待部署）。

## 第三次任务
- [x] R1 新增格式边界回归，执行旧实现RED和相邻基线。
- [x] R2 修复唯一闭合物理行绑定，执行GREEN和真实task315保留父子结构完整链路。
- [x] R3 Golden/root-cause登记，架构/模块/全仓/build回归及关键修复变异实验。
- [x] R4 清理、fresh证据、知识回填与独立全diff审查；本地工作区交付。
- [ ] R5 单列后续授权部署及真实项目新任务DB/API/UI验收。

第三次最终证据：全后端10640项/10623通过/0失败/0错误/17跳过、Mavenexit0；root-cause门禁exit0。部署R5及原任务的PR/推送门禁未执行，不把本地交付等同于发布。详见任务.omc/verify-score-row-quotation.md。
