# Tasks: 项目1359评分契约

## Setup
- [x] T001 隔离CoW工作区并记录入口门禁 .omc/entry-gate-01a11bb1-0647-7530-b524-3d0fcdf0ec44.md
- [x] T002 完成spec/plan/research/data-model/contracts和任务范围 specs/074-fix-1359-score-contract/
- [x] T003 执行Golden健康基线并留日志 .omc/evidence/baseline-golden.log

## US1 正确小计
- [x] T004 [US1] 实测RED：真实双列、列换序、单满分、未知列、冲突 backend/src/test/java/com/xiyu/bid/scoreparse/domain/Project1359ScoringContractTest.java
- [x] T005 [US1] 仅修小计列读取 backend/src/main/java/com/xiyu/bid/scoreparse/domain/OriginalScoringContext.java
- [x] T006 [US1] 注册真实1359Golden夹具和独立契约断言 backend/src/test/java/com/xiyu/bid/scoreparse/golden/GoldenCasesReplayTest.java

## US2/US3 项目恢复
- [x] T007 [US2] 真实DOCX与旧38项逐项复核，39条来源和50/20/30换算 specs/074-fix-1359-score-contract/contracts/project1359-recovery.json
- [x] T008 [US3] 恢复dry-run、漂移和拒绝反例脚本 scripts/scoreparse/restore-project1359.py
- [x] T009 [US3] 备份旧数据、复核事务和回滚脚本 .omc/evidence/

## Verify / Review / Deliver
- [x] T010 执行ai-slop-cleaner Mode B并留实测记录 .omc/verify-01a11bb1-0647-7530-b524-3d0fcdf0ec44.md
- [x] T011 执行相关保护测试、Golden全集、npm build和mvn test并记录退出码 .omc/evidence/
- [x] T012 独立代码/规格/恢复材料审查 .omc/review-01a11bb1-0647-7530-b524-3d0fcdf0ec44.md
- [x] T013 [US3] 审查后执行1359授权事务并核对数据库和真实API/页面 .omc/evidence/live-acceptance.md
- [x] T014 Spec Kit analyze只读一致性复核及Wiki证据回填（结论纳入acceptance.md）

## Dependencies
T003→T004→T005→T006→T010→T011→T012；T007→T008→T009→T012→T013→T014。源码实现与恢复材料独立，本次单负责人顺序写入；审查只读并行。

## Actual Outcomes
T011表示验证已执行且记录，不代表所有门禁绿色：受影响Java117/117、Python10/10、Golden旧27案F1=100%、npm build通过；全量后端975/2fail/42error仍FAIL。独立干净基线精选75/4fail/0error，两项原断言失败均基线复现，42缺失class错误未在基线复现，不能推断当前全库已通过。T012仅DATA_READY允许评分标准数据库恢复，没有源码整体MERGE_READY；未commit/push/PR/merge/deploy。T013已实测DB及页面39/100，AI实际评分未执行。T014全Wiki检查另有165个已有健康日期违例，不批量伪造复核日期。
