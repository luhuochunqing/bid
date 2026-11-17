# Implementation Plan: 案例内容相关性约束
Branch: agent/codex/case-topic-relevance | Date: 2026-11-17 | Spec: [spec.md](spec.md)
## Summary
主题分与背景分分离；主题零命中返回0；现有服务先过滤后置顶排序保持不变。补齐配送词表并同步离线规则，现有pre-push执行根因行为回归。
## Technical Context
Java21/JUnit5/AssertJ；纯核心无Spring依赖，现有Spring应用服务编排不变；Python离线特征脚本与bash门禁。无存储/接口变更，无新增依赖。
## Constitution Check
FP-Java纯核心规则；真实实现计算不mock策略；不启动临时worktree服务；任务worktree隔离。源文件保持300行内。
## Steps
1. 根因反例测试RED：同客户无关证书及误分类证书、置顶应用编排、批量语料和配送主题；保留明确证书与默认推荐正例。
2. 移动实体评分至内容适用性判定之后；ScoreItemCaseTextMatchPolicy标题相似度不再吸收搜索词作为内容证据；以内容分单独决定背景加成比例。同步配送词表。
3. 测试GREEN，接入现有pre-push，撤销核心修复再RED，恢复后重新回归。
4. 清理、全量检查、独立只读审查、Wiki与教训沉淀。
## Rejected Alternatives
只调权重/阈值不能消除背景代替主题；技术与资质全面互斥会破坏现有证书辅助技术要求的兼容契约。无需新推荐引擎、数据迁移或前端过滤。
## Verification
mvn -f backend/pom.xml test -Dtest=ScoreItemCaseRelevanceRegressionTest；mvn -f backend/pom.xml test -Dtest=com.xiyu.bid.casework.**；python3 scripts/extract-historical-case-features.py --self-test；bash -n scripts/pre-push-gate.sh；mvn -f backend/pom.xml test；npm run build；git diff --check。命令实际执行结果落入验证工件，环境故障单列。
