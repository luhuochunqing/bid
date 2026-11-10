# Semantic boundary Minor 验收与一致性

## 来源与范围

用户在!2995合并后提出三条Minor，本任务完成字符类/冗余清理与绿色语料，业务大测试A/B/C/D精简留后续。基线2193dcab2；分支agent/claude/semantic-boundary-minor。FR01→T002→AC01；FR02/FR04→T003/T004→AC02/AC03；FR03→T007→AC04，无未覆盖用户要求。

## 已执行证据

- 原基线：默认93契约通过；685语料中59个JUnit调用失败，日志`/tmp/semantic-minor-baseline.log`。59为调用数，不能误当方法内所有文本变体数。
- 期望校正：生产源码哈希未变，显式685+默认93共778项全绿，日志`/tmp/semantic-minor-expectations-green.log`。混合数据以CorpusExpectation/Arguments静态行指定结果，保留明确其他对象的PASS；没有Disabled或被测函数生成期望。
- 纯生产重构后：相同778项0失败/错误/跳过，日志`/tmp/semantic-minor-refactor.log`。旧/新XML及冻结文件哈希在`/tmp/semantic-minor-evidence/`。
- 编译后反射核对：Pattern.compile实际20→19；18个未改规则的pattern原文与flags完全相同。仅移除INELIGIBLE_OR_INVALID_STATUS单独预编译（仅供组合规则使用），DEFECT组合词表删冗余；2251个现有分句的原文及归一化find结果一致，日志`find-equivalence.log`。

## 具体减少了什么

1. 将完整`[^。；;，,\n\f]`与保留换页字符的`[^。；;，,\n]`分别提为常量，不把两者合并成不同语义。
2. 分句时直接排除换页分隔符，删除随后只跳过换页标记的continue。
3. find中“尚未/未曾/未能”已被“未”覆盖；“已经?|已”分支重复；“不再具有/未生效”和有效期到期/截止等有既有覆盖，只保留独有的“有效期满”。该规则只取find布尔值，不使用匹配范围，不能据此声称完整正则语言或匹配起止位置相等。
4. 扫描只维护样例作用域，当前分句分类独立；失效/第三方/日期判断复用同一方法，避免两处重复的三联检查。

THIRD_PARTY_OWNERSHIP、CONTEXT_BOUNDARY仍保护同句权属和明确其他业务对象，不整段删除、不扩词表、不引入新类。字符类之外的正则结构允许片段拼接，但每个字符类在单个字面量或命名常量中完整。

## 知识与交付

corpus-differences.md明确标记历史差异已关闭，保留原始59调用记录。业务大测试与AuthorizationFieldPolicy冻结不改。本轮仅本地提交，不自动复用上一PR推送授权，无部署、重算或生产UAT。

完整域/架构、构建与三技能流程结果保存于本机验证日志和私有Git review-gate凭证；文档不以预写PASS替代执行证据。!2994历史和熔断沿用，不清状态、不自行allow-rerun。

## 提交前验证范围

相关compliance域＋三架构测试1369项：0失败、0错误、1跳过；显式语料685项全绿；npm run build退出0。完整域的旧FullDocumentSemanticRetrievalAcceptanceTest夹具在!2995已记录基线失败，本次仍不改该夹具、不将其计入通过范围。最终三技能verify显式包含语料，测试命令传`-DexcludedGroups=`，避免只验证默认93项。

受保护的Quality1360BusinessContractTest与AuthorizationFieldPolicy SHA-256对照未变化；所有685语料的逐方法调用次数（含记录的方法重命名）与基线一致，字符类字面量完整性检查通过。
