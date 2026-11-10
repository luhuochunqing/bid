# Implementation Plan: Semantic boundary 技术债

Branch: `agent/claude/semantic-boundary-debt` | Date: 2026-11-10 | Spec: [spec.md](spec.md)

## Summary
按已批准[计划](../../docs/exec-plans/active/semantic-boundary-debt-plan.md)和[151方法清单](../../docs/exec-plans/active/semantic-boundary-debt-inventory.md)顺序执行。

## Technical Context
Java21、JUnit5、Surefire3；纯核心无存储或网络。测试拆分为业务/HTML/JSON/启发式四类，语料类标签默认排除。先迁移全量等价，后结构等价，再单独行为收口。两个原policy每行≤120字符，必要时整体提取证书上下文，不创建通用框架。

## Constitution Check
FP-Java纯核心不引入I/O；无Mock业务路径；既有测试characterization与行为变化RED/GREEN；生产文件≤300行。原巨大测试迁移是历史债，不机械要求语料文件满足生产行限。无外部接口、迁移或服务启动。前/后设计检查均符合。

## Project Structure
生产变更仅SemanticEvidenceBoundary、AuthorizationFieldPolicy及包可见CertificateEvidenceContextPolicy；测试仅Quality1360原类、三类契约、语料类和最小范围契约；配置仅backend/pom.xml。文档仅本feature和既有plan/wiki/经验入口。

## Execution and Validation
T001规格与基线 → T002拆测试并显式全量等价 → T003默认排除配置验证 → T004结构等价 → T005范围契约RED → T006撤回跨句推断GREEN → T007域回归/架构/构建 → T008三技能门禁与一致性分析。
先保存迁移阶段与结构阶段快照以便分别审查。三技能审查只使用现有review-workflow.py，不清历史、不自用allow-rerun。
