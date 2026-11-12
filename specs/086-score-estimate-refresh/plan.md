# 全量预判刷新 Implementation Plan
Goal:修复刷新范围、反馈和入口。Java21/Spring/JPA/MySQL、Vue3/Vitest，零新增依赖。
Architecture:复用EstimatedScoreService全部项计算与版本保护；Controller返回items/summary/meta和refresh={processedCount,scoredCount,pendingCount,pendingItems:[{id,dim,reason}]}。前端回读后显示本次摘要，按钮位于折叠外。
Constitution:保留FP-Java核心策略、真实API、项目权限与独立事务，无迁移，不改宪法。Spec Kit分支钩子脚本缺失，已有独立任务分支提供等价隔离，可选自动提交不执行。
## Steps
1. 后端无证书refreshForProject回归先RED，移除certificateOnly过滤，再GREEN。
2. 增加兼容结果DTO；持久化后统计，零分有效，无项拒绝，冲突拒绝。
3. 前端按钮折叠可见测试先RED，移动工具栏，真实反馈与未确认原因，项目切换隔离。
4. MySQL核对业绩/仓储落库、旧依据清理、候选/阶段2不变；JUnit/Vitest与build。
5. ai-slop-cleaner后重验、独立review，记录现场验收待部署。
6. 独立审查round1发现回执结果竞态：保存结果形成不可变身份；Controller独立读取若不匹配返回409；前端POST/GET结果字段变化拒绝摘要。两个时间窗口分别新增RED→GREEN回归，之后重验复审。
## Alternatives
不重新解析/采纳方案修补预估字段；不改生产数据；不引入新任务平台。
