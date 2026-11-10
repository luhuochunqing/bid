# 验收与一致性分析

## 当前实现依据

用户已批准的逐方法清单→spec FR01/AC01→T002/T003；结构等价→FR02/AC03→T004；未知主体复核→FR03/FR05/AC04→T005/T006；!2994门禁→FR06/AC05→T007/T008。三个故事均有任务与可执行检查，无额外业务范围。

## 已执行阶段证据

- 原基线：`mvn -o -f backend/pom.xml -Dtest=Quality1360UnblockTest test`，773/0/0/0，日志 `/tmp/semantic-boundary-baseline.log`。
- 迁移：四类全量773/0/0/0，67业务+12HTML+10JSON+684语料；逐方法调用数Counter与基线完全相同。日志 `/tmp/semantic-boundary-migration.log`，XML与逐方法body哈希manifest `/tmp/semantic-boundary-evidence/`。
- 纯结构：四类显式全量仍773/0/0/0，日志 `/tmp/semantic-boundary-structure-final.log`；生产三类快照在上述evidence目录 `*.structure.java`，供与最终行为收口分别比较。
- 默认排除：三契约类89项；显式语料覆盖命令使用 `-DexcludedGroups=`，不修改旧语料断言。最终契约另加4方法。
- RED：CertificateEvidenceScopeContractTest 4项2失败，均为未知主语同页/跨页“未到场”期望人工实际PASS。原明确同句证书缺陷/肯定与明确其他对象已通过。日志 `/tmp/semantic-boundary-scope-red.log`，`scope-red.xml`。
- 编译中的String误用matcher已按实际类型纠正；未把编译失败作为行为RED证据。

## 范围决定与限制

撤回previousRelated/lastCertificateRelated/pendingDefect/certificateContextKnown指代传播，仅保留样例正文作用域。当前分句明确其他对象可排除；未具名证书材料、权属、状态只触发复核。未修改资质库、保证金、OCR调用、模型或25项定义。

本机未提供真实原件hash/页码的语料不称真实样本。无服务启动、部署或生产重算。实施阶段仍未提交，阶段间用已验收源码快照与XML保留差异；若后续交付提交，须保持结构与行为差异可单独复核。

## 后续验证

- 相关域＋三架构测试：144个suite，1369项，0失败、0错误、1跳过；命令排除已在原policy复现失败的FullDocumentSemanticRetrievalAcceptanceTest夹具，日志 `/tmp/semantic-boundary-domain-final.log`。此为限定范围通过，完整域首次运行1385项2失败：其中1条基线遗留，1条混合指代断言已按用户授权迁语料，未改旧预期。
- 原policy隔离复跑该旧夹具：16项1失败，实际同为MANUAL_REVIEW但旧断言要求全部PASS，日志 `/tmp/semantic-boundary-unrelated-baseline.log`。
- 前端 `npm run build` 退出0，日志 `/tmp/semantic-boundary-frontend-build.log`；line-budget与diff检查通过。
- 最终显式语料685项，59条旧期望差异、0错误；逐项确认全部为原PASS转保守复核，没有MANUAL_REVIEW转PASS。差异见[完整表](corpus-differences.md)，不扩大正则修复。
- 三技能完整流程使用仓库runner与!2994既有历史/熔断，执行结果由私有Git目录review-gate/state.json及其关联日志记录；不以本文手写声明代替凭证。
