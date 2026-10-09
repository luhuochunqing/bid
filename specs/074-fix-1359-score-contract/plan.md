# Implementation Plan: 项目1359评分契约

**Branch**: agent/codex/074-fix-1359-score-contract | **Date**: 2026-10-08 | **Spec**: [spec.md](spec.md)

## Summary
先通过真实原文恢复包2的38项技术/商务规则与价格公式；原始分分别乘0.5/0.2/0.3。恢复限定1359数据库，备份、dry-run、行级前置断言、事务、前后核对。应用代码只过滤小计读取的列语义，不动来源和发布门禁。

## Technical Context
Java21/JUnit5/Jackson纯核心测试，既有MySQL8/JPA应用，Python3标准库生成和验证专用恢复材料。无持久表DDL，仅创建/删除本次临时运维存储过程和临时校验表，无新依赖、无部署、无服务开关变化。

## Constitution Check
纯核心无框架依赖；真实API/数据库唯一源；RED→GREEN→cleanup→复验；既有Golden全集和保护反例回归；不改宪法、不改受保护持久worktree。数据库修复是用户授权的运维数据恢复，不伪装为解析成功。

## Project Structure
业务源码仅OriginalScoringContext.java与纯核心ScoreSubtotalColumnPolicy.java；Project1359ScoringContractTest.java及Golden回放扩展验证真实1359和列语义反例；contracts/project1359-recovery.json存来源材料；本地一次性运维工具scripts/scoreparse/restore-project1359.py生成严格1359事务（不入源码PR）；specs目录存计划、任务、契约与验收记录。

## Implementation Steps
1. 基线: mvn -f backend/pom.xml test -Dtest=GoldenCasesReplayTest，npm run eval:scoreparse:report。源代码小计故障用例先RED。
2. 真实1359合计最低12/38最高100、列换序、未知多列、单明确满分、可信列冲突测试。最高/满分列唯一时只读该列，不能选最大数值冒充列证据。
3. 逐项明细来源核对，原始商务100、技术100、价格公式100及50/20/30；两扣分规则原样保留，规则数39。
4. 专用恢复脚本dry-run验证来源ID、唯一规则、38旧项指纹、精确小数和类别总计；数据库备份和事务提交前复核。
5. ai-slop-cleaner Mode B后重新跑新测试、相关来源/父子/方案/发布保护测试、Golden全集、npm build及mvn test。
6. google-code-review、code-review及interrogate独立审查；源码和恢复材料分别判定。审查通过后执行授权SQL并核对真实API/数据库/页面。

## Scope and Delivery
working_tree源码交付；额外用户明确授权1359数据库修复；禁止push/PR/merge/deploy、禁止打开版本开关。不重构模型或解析管线。

## Verification and Rollback
.omc/evidence保存真实命令和退出码，.omc/verify及review保存新鲜证据。备份旧score_item、关联score_result及受改task字段；失败回滚整个事务；提交后回滚按本次快照与行级指纹恢复，不覆盖别人的后续修改。

验收范围经用户裁定：只恢复评分标准39/100，AI实际打分暂不可用，按钮未强制禁用。MySQL修复/回滚全部DML封装在临时存储过程的EXIT HANDLER（ROLLBACK/RESIGNAL）内，即使客户端--force继续也不能部分提交。

用户后续“提交PR”授权源码提交到Gitee；不合并、不部署。一次性数据库工具不入源码PR，历史恢复事实保留在验收说明。
