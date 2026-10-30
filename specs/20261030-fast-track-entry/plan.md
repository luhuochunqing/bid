# 快速通道统一入口 Implementation Plan

**Goal:** 实现模型纯文本直提优先，失败才执行旧证据管线。
**Architecture:** 新建应用层入口路由负责模型I/O与惰性旧路径；领域层完成无网格核验及父子计分，复用现有文本核验、计分单元和持久化。移除旧后处理中的伪主通道，避免失败后再次错误升级。
**Tech Stack:** Java21、JUnit5、Spring Boot3.2、Maven。

## Constitution Check
纯核心无I/O；外部模型只在应用层调用；不新增数据库表；保留历史快照激活限制。

## Files and responsibilities
- NEW domain/PrimaryScoreSnapshot.java：为版本化结果生成真实文本SourceSpan和单方案Qualification，冻结后走既有激活接口。
- NEW infrastructure/openai/PrimaryTextScoreRequest.java：单次全文模型请求，超过80000字符不截断，直接兜底。
- domain/OriginalScoreWeightEvidencePolicy.java：复用文本分值证据解析，不引入网格调用。
- application/MultimodalScoreFactExtractor.java、infrastructure/openai/OpenAiMultimodalScoreFactExtractor.java：独立主通道模型端口。
- application/VersionedScoreParseCompletion.java：新增主通道冻结收口。
- NEW domain/PrimaryScoreFactPolicy.java：把有证据、无歧义模型事实转换为末级候选。
- NEW application/ScoreParseEntryRouter.java：主通道抽取和惰性fallback。
- NEW application/PrimaryScoreParseCompletion.java：主通道任务摘要和现有持久化收口。
- application/ScoreParseAppService.java：统一入口先走路由。
- application/ScoreCandidatePostProcessor.java：删除旧入口内的快速通道标记。
- domain/DualTrackScoreExtractor.java：非空规则核验与缺失分值防护。
- 对应domain/application测试、scoreparse-golden合成夹具及GoldenCasesReplayTest注册。
- docs/lessons/lessons-learned.md、.wiki/pages/scoreparse-cross-format-strategy.md：修复证据。

## Execution
1. 先写入口行为及空规则/重复/父子核验测试；运行Maven观察RED。
2. 实现主通道抽取与惰性fallback；闭合失败保留旧审核流程。
3. 统一持久化并验证版本化分支不会绕过激活；删除旧位置的双轨标签。
4. 跑定向测试、金标和架构检查；用1135证据完成本地验证，再记录真实重解析缺口。
5. 执行删除优先清理、重新验证、审查；不自动合并/部署。

## Verification
`mvn -q -f backend/pom.xml test -Dtest=PrimaryScoreFactPolicyTest,ScoreParseEntryRouterTest,DualTrackScoreExtractorTest,ScoreParseAppServiceTest,ScoreCandidatePostProcessorTest,GoldenCasesReplayTest`
`mvn -q -f backend/pom.xml test -Dtest=FPJavaArchitectureTest,MaintainabilityArchitectureTest`
`npm run build`

## 审查修正与清理范围
第一轮两轴审查指出错分和同源重复可凑满100、版本化legacyDraft缺少scheme、默认100缺少来源，均纳入本次根因范围。
清理按ai-slop-cleaner执行：删除旧后处理双轨分支及无用import；保留独立模型I/O、纯核验、完成器三种职责，不删除旧兜底代码；避免为新通道复制几何引擎。
本地前端构建已通过（3459模块，15.28秒），依赖通过任务工作区忽略的node_modules软链复用宿主安装，未修改宿主源码。
