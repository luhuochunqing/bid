# 证书评分正确性 Implementation Plan

**Goal:** 消除证书标题漏认、组合条件失真与旧预判残留，交付可验证本地修复。
**Architecture:** 纯核心CertificateScoreRule/Policy提取和裁决；CertMatchService将真实JPA证书快照映射到核心事实并生成输入指纹。已有EstimatedScoreService在证书分支使用该结果，其余业绩穿透逻辑保留。独立刷新服务复用规则快照与CAS；GET识别陈旧证书预判并从汇总中排除。
**Tech Stack:** Java21/SpringBoot3.2/JPA/MySQL8/Flyway；Vue3/Vitest。

## Global Constraints
- controller=none，delivery_target=working_tree，不执行commit/push/PR/部署或生产数据操作。
- 复用隔离工作区agent/claude/score-cert-root-cause-20261026；基线b7f3dac5e。
- 单一Spec Kit后端；宪法只读。用户已批准修复边界，无待澄清业务决策。
- 核心不读取时钟/框架/数据库，所有日期显式输入；新Java文件<=300行。
- 不支持或冲突规则PENDING；不得通过通用关键词回退给满分。
- 原有11/17字段DTO调用保留；新增estimateStatus、estimatedAt。

## Slice 1: 逐证书要求匹配（US1）
Files: NEW domain/CertificateScoreRule.java, CertificateScorePolicy.java；修改application/match/CertMatchService.java；新增对应纯核心与真实JPA测试。
接口：CertificateScoreRule.isCertificateScore(dim,detail)；CertMatchService.estimateScore(dim,detail,weight,asOf)返回ScoreEstimate(assessment,inputFingerprint)，assessment包含score/status/kbHit/basis；fingerprintForScore相同参数返回当前输入指纹。所有类型在实现前由worker确认，其他模块只通过这些接口访问。
RED: 原文三ISO齐全缺一0分，逐一缺证、无关证书、等级、过期退役、断行、企业荣誉阶梯。GREEN: 任意LIKE只作召回，逐标准唯一身份裁决，不按记录数凑标准；不支持规则不确定性降级。

## Slice 2: 新鲜度与独立刷新（US2/US3）
Files: ScoreItem/ScoreItemDTO/ScoreScoringVersionService/EstimatedScoreService/ScoreParseItemsReadSupport/MetaBuilder；NEW ScoreEstimateRefreshService、ScoreEstimateController、ScoreEstimateFreshness；迁移V1225及回滚。
新增nullable estimate_input_fingerprint(varchar64)与estimated_at(datetime6)。指纹含证书计算引擎版本、dim/detail/weight、证书事实、核验日；丢失/不一致=STALE。评分行陈旧时DTO返回PENDING/null分数及待更新依据，数据库保留历史列，汇总使用投影而非历史数值。
POST /api/projects/{projectId}/score-parse/estimates/refresh，先复用项目访问守卫，再同步计算已生效行。复用捕获规则指纹与saveEstimates CAS，不修改候选、权重或阶段2。响应回读items，不将CAS失败伪装成功。legacy来源时间按旧项parse_task_id读取。
RED: 老行显示STALE、更新后FRESH；证书变更/日期变更重新STALE；规则变动旧计算拒绝；无权限403；候选和阶段2数据不变。

## Slice 3: 页面入口（US3）
Files: src/api/modules/scoreParse.js、useScoreParseDrawer.js、ScoreParseDrawer.vue及对应Vitest。
新增refreshEstimates(projectId)调用上述POST，独立按钮“更新知识库预判”，busy防双击，成功后回读items；失败保留原数据并显示错误。显示需更新数量与时间；不触发parse/scoring/activate。API失败不显示成功。

## 验证与审查
先基线mvn -Dtest=KnowledgeCategoryPolicyTest,CertMatchServiceTest,EstimatedScoreServiceTest test，保存退出码。每切片RED→GREEN，真实MySQL8验证查询及事务边界。S4结束执行ai-slop-cleaner Mode B，再运行新鲜mvn受影响测试+架构、pnpm Vitest和build。Spec Kit analyze与converge检查FR映射。S6独立google-code-review和规格code-review，生产风险审查，最多三轮，源码变化使旧证据失效。记录.omc/verify和review。health工具缺失豁免已登记，使用行预算/架构基线替代并如实报告。

审查补充：持久化指纹还封装本次分数、状态、命中标记及依据，防止旧写入者修改估分列后借用新输入戳；真实MySQL先观察此反例FRESH误判，再修为STALE。数值去尾零，避免DECIMAL落库scale引起假过期。
