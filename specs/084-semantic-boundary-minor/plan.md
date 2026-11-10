# Implementation Plan: Semantic boundary Minor

## Summary
按用户Minor反馈顺序校正语料，再纯结构清理同句证据策略。采用已有Java21/JUnit5/Surefire，不引入依赖和公共API。

## Constitution Check
FP-Java纯核心不变，所有输入/循环样本保留；先记录基线778项中59失败，生产不变时校正测试到全绿，然后重构。生产代码只改CertificateEvidenceContextPolicy；Quality1360BusinessContractTest与AuthorizationFieldPolicy内容哈希冻结。≤300行、≤120字符。无迁移、I/O或Mock业务路径。

## Project Structure
生产：CertificateEvidenceContextPolicy.java（公共CLAUSE_STOP/行内STOP片段、find冗余、换页分支及同句分类）。测试：SemanticHeuristicCorpusTest.java（逐输入静态期望）。文档：本feature、原corpus-differences追加关闭状态、compliance README、既有Wiki与经验入口。

## Validation
先默认93契约+显式685语料基线；修期望后778项全部通过，再生产重构后原样778项通过。校验旧/新正则find对现有语料与分句的等价及正则原文重构相同部分；不增加合成句式。相关compliance+三架构（排除已复现的旧FullDocument夹具）与npm build。最后review-workflow完整三技能，最多三轮、不清历史。
