# 项目1117 CA平台名称修复：本地验证记录

- 日期：2026-10-25；基线：3e82ffdebe9a2d1a1aa6595dd8c07dc688d405e1。
- 沙盒：/Users/user/.herdr-controller/clones/xiyu-ca-platform-1025；分支agent/codex/fix-ca-platform-1025。
- 交付：**本地源码修复及验证通过**；Standards/Spec独立审查均MERGE_READY。验证时尚未提交；用户随后授权创建PR，提交与远端状态以Git/PR记录为准，未授权部署。开发宿主trae保持干净。

## 最终行为与用户确认的边界

用户确认：“没有可核实平台身份的候选，统一转待确认”。平台候选提取和身份核实分开：

| 情形 | 最终裁决 |
|---|---|
| 招商原文全称“招商局集团电子招标采购交易网”与台账简称“招商局集团电子招采平台” | 同一身份，按真实证书状态裁决 |
| “电子招标投标交易平台”“本次交易平台” | MANUAL_REVIEW，保留候选/原文 |
| 未核实的新公司平台，即使台账有matched=true | MANUAL_REVIEW |
| 已核实平台，台账查询成功且无匹配证书 | FAIL |
| 平台未知、冲突或CA查询失败 | MANUAL_REVIEW |
| 正文存在未填CA占位模板 | 保留独立FAIL，不受查询失败/身份未知覆盖 |

正向身份规则采用已核实的精确平台别名与明确地域CA/公共资源/政府采购机构契约；没有覆盖的新平台也转人工，不能仅以名称像公司或平台推定身份。新增身份须有证据与对应测试，不扩充否定词表掩盖未知身份。

## 生产输入与本地完整回放

- 生产旧版本f3ff316f2、项目1117原报告56：保证金第12页PASS，CA因“或称交易平台”FAIL。本报告未把旧生产状态当作修复后状态。
- 只读下载的115页招标PDF哈希：040c608b9465876952a8fd1d383fe652756868209486827761841cedca705d33，与生产质量报告中的tenderFileHash一致。
- 通过正式`DeterministicRequirementsFuser`入口本地回放：提取“招商局集团电子招标采购交易网”，匹配台账简称，CA核查PASS；包含第4页“招商局集团电子招标采购交易网（或称交易平台）”逐字证据。台账字段脱敏映射后作为纯核心输入，不修改生产证书。
- 公告发布网站、其简称定义，以及同句逗号/无逗号公告描述，均不覆盖真实投标平台。同优先级的不同平台转人工。

## 执行证据

- 既有RED：/tmp/ca-platform-red.log（68项7失败）、/tmp/ca-platform-review-red.log（49项4失败）、/tmp/ca-platform-website-red.log、/tmp/ca-platform-time-clause-red.log、/tmp/ca-publication-red.log。
- 用户裁决后RED：`mvn -q -f backend/pom.xml -Dtest=CaCertificateCheckPolicyResourceTest test` exit=1，/tmp/ca-identity-red.log，9项1失败，实际旧行为为FAIL而期望MANUAL_REVIEW。
- 最终关联回归exit=0：`mvn -q -f backend/pom.xml '-Dtest=com.xiyu.bid.compliance.domain.policy.*Test,TenderRequirementsParserTest,TenderRequirementsExtractorTest,KnowledgeBaseOnDemandLoaderTest,CaCertificateEvidenceQueryAdapterTest,OpenAiDepositReceiptVisionDetectorTest,ArchitectureTest,FPJavaArchitectureTest,MaintainabilityArchitectureTest,ResponsibilityArchitectureTest' test`；日志/tmp/ca-identity-regression.log。对应suite XML：**72类566项、0失败、0错误**。包含保证金视觉回归和4个架构测试类；不是backend全库测试。
- `java --class-path backend/target/classes /tmp/CaFrozenReplay.java` exit=0，/tmp/ca-identity-frozen.log。命令明确断言PDF哈希、字面平台全称、台账别名匹配、PASS、第4页原文。
- 原残余反例实际输出见/tmp/ca-identity-residual-green.log，两个泛称均MANUAL_REVIEW；新增JUnit还覆盖陌生企业名称、matched=true不能绕过身份边界与占位优先级。
- 前端`npm run build`此前exit=0，/tmp/ca-platform-frontend-build.log；本轮最终文档更新后重跑exit=0，/tmp/ca-identity-frontend-build.log。
- `git diff --check`与`node scripts/check-line-budgets.mjs` exit=0。

## 清理、规格与审查

- Mode B清理：删除旧首命中平台正则、任意中文地域CA正则、不可达的未知平台过期FAIL分支；复用一套地域表达式，无新依赖/数据库/API变更。身份与业务裁决位于纯核心，无IO或框架依赖；正式入口只保留既有加载/查询副作用。
- 077追加FR013—017已映射到T016—022：泛称/上下文/别名、原文页码、失败源守卫、未知身份保守人工。规格、计划、任务一致，无新增阻断。
- 前轮三次Standards阻断留作历史；用户明确新边界后恢复独立审查。当前Standards与Spec两位独立同模型Agent均MERGE_READY，各自独立javac源码运行反例，未污染Maven target。该结论只覆盖本地源码和测试，不能替代生产验收。

## 发布与验收边界

用户已授权创建PR；验证时未提交，生产仍运行旧版本，PR创建不代表合并或部署。尚未对新候选启动后端并进行浏览器E2E。未来发布后必须重新核查项目1117并核对页面、报告ID、平台与原文证据；不能用本地PASS代替生产通过。工作树交付未调用要求clean committed HEAD的acceptance-evidence-gate，也不宣称其机器回执通过。
