# Implementation Plan: 跨页评分来源闭合
**Goal:** 修复来源单元去重、跨页完整规则绑定及分组闭合，保留歧义保护。
**Architecture:** Pure Core复用SourceFactIdentity/LogicalRuleSourceUnit/OriginalRuleRestoration，外壳复用TextPrimaryEvidenceBinder与MultimodalExtractionReviewPolicy；不新增存储/服务。
**Tech Stack:** Java21、JUnit5、现有PDF Sidecar网格。
## Constitution Check
FP-Java纯策略无I/O；不引入Mock运行模式；先RED再GREEN；不修改数据库；保留文件预算。
## Tasks
1. 检查同物理行不同来源cell集合、短摘要与完整引文、跨页行引用、分类父项边界的失败用例；在当前实现上执行。
2. 逐一修复已证明条件：复用完整逻辑行确认唯一身份，引用区分独立子规则；绑定完整续页来源；以原件分组验证层级与总分，不强制归一。
3. 运行来源/规则/金标回归、反事实回放和构建；清理后再次验证，再执行独立只读审查。
## Verification
mvn -q -f backend/pom.xml test -Dtest=SourceFactDeduplicationTest,OriginalRuleRestorationTest,TextPrimaryEvidenceBinderTest,GoldenCasesReplayTest
SIDECAR_PYTHON=/Users/user/xiyu/worktrees/trae/document-converter-sidecar/.venv/bin/python bash scripts/check-scoreparse-root-cause.sh
npm run build；git diff --check；完整mvn test。私有原件回放与合成测试分开记录。
## Rejected alternatives
按项目号特例、强制总分100、全量模型重抽取、删除来源歧义保护均不采用。
