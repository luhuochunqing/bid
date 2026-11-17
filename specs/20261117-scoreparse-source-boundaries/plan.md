# Implementation Plan: 评分来源边界
## Summary
执行 docs/exec-plans/active/scoreparse-1133-repair-plan.md，按同一输入RED/GREEN、相邻回归、关键修复撤销反证验证四类根因。
## Technical Context
Java21纯领域策略、JUnit5、MySQL读模型、Sidecar/OCR来源资产；既有前端API，不改数据库。客户PDF和OCR取证仅保留本地，合成金标入库。
## Constitution Check
FP-Java纯核心；真实API，无运行时Mock；先测试后实现；不在锚点分支写代码；不越过部署和生产数据写入授权。PASS。
## Structure
修改OriginalScoringTextScopePolicy/OriginalScoringSchemePolicy（方案）、TextPrimaryEvidenceBinder（文本来源）、LogicalRuleSourceUnit（行边界）、ScoreTypeClassificationPolicy（类型）。使用对应既有测试并新增1133集成回放测试和合成金标。
## Verification
先检查现有定向基线；新增失败用例证明目标行为RED；最小修复后GREEN；运行评分领域/应用定向与金标、架构检查、全量测试和前端构建；用隔离的修复撤销验证防复发。独立三技能审查最多三轮。真实生产最终验收未获授权，本轮不执行。
## Version / hooks
复用agent-start-task.sh已创建的受管分支，不新建第二分支；Spec Kit before_specify分支创建脚本在本仓库缺失，分支前置由既有受管worktree满足。optional自动提交hook不执行；未声称调用缺失脚本成功。

## 验证范围调整（实现过程中）
全仓Maven运行约20分钟，主要消耗在未触及模块的DirtiesContext集成启动；运行编译的是新增真实章节保护前的版本，已取消，不作为通过证据。调用检索确认五个修改策略无scoreparse之外调用者；最终改用scoreparse全域测试、ArchitectureTest/FPJavaArchitectureTest/MaintainabilityArchitectureTest、真实PDF及合成金标，前端构建已通过。全仓状态明确未完成，不推断全仓无回归。

## PR独立审查补充范围

西域审查发现Java目录行过滤与Python转换器的同步契约漂移。补充修改document-converter-sidecar/converter.py及tests/test_pdf_table_artifact.py，仅同步带横线尾页码的目录判据，覆盖章节和文本两个入口及后续真实章节。新增三例在旧实现均断言失败，修复后PDF表格及转换回归68项通过；不扩展评分引擎、数据库或生产操作。

## 离线贯通验收补证

按用户确认方案，保持业务实现不变。新增仅针对1133的实际PDF→实际Sidecar→ScoreParseAppService Worker→隔离MySQL→review Controller贯通测试。采用任务248已观察的EVIDENCE_PIPELINE_V1路径，相关开关明确记录。独立读取PDF物理55—57页的规则列作为全文断言依据；不手填解析候选。通过则停止业务优化，只补测试与证据到PR；失败才定位目标内的首次错误。
