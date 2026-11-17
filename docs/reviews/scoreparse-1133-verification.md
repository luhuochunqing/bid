# 项目1133本地修复验证记录

## 范围

本轮交付工作区修复；无部署、生产重解析或采纳操作。基线92d522f8ba7b7d95d3d5882c4da81e3ce1d5a912。
生产任务155/248保持历史记录。真实PDF与数据库完整原文仅保留任务Git私有目录scoreparse-1133-evidence；提交的金标为合成样本。

## 执行证据

- 修前定向66项：5失败、0错误。错误分别来自目录、方法字段、材料短标题、分档类型和合计续行。
- 首轮定向/金标/真实资产回放156项通过；五处关键实现独立替换为HEAD基线字节码后均被对应断言捕获。
- 补充保护：真实无编号「评标办法：综合评分法」章节必须保留；数量门槛不能只因「基本满足」改主观。
- 扩大评分解析及架构回归2179项：96失败、13跳过，未宣称全绿。83项因SIDECAR_PYTHON缺docx/lxml；2项来自新来源约束丢失合同条款定位；11项为基线同样失败。
- 修正来源约束并设置已验证Python后，隔离重跑TextPrimaryEvidenceBinder、Project1382、CrossFormatDocumentSourceReplay、FullRuleSourceReplay及ScoreExtractionPersistenceMysql：160项发现，155通过、5跳过、0失败。该次复跑覆盖上述83项环境失败和2项新回归。
- 真实项目1133的重获Artifact经过生产结构化抽取/来源核验，得到12项100分、六主观六客观；仓储不再含合计，商品覆盖/售后跨页文字完整。输入文件SHA见原诊断记录与私有manifest。
- 前端pnpm锁文件依赖安装及pnpm run build均exit 0；构建未修改前端代码。
- 全仓Maven运行约20分钟后取消：测试编译早于补充边界修改，且仍在无关模块Spring上下文中。全仓未完成，不作为最终通过证据。

## 基线失败对照（不通过修改预期消除）

五个被修改类的HEAD原版编译到独立目录并置于classpath首位；不改仓库或target。相同已有测试在HEAD覆盖下重现：

| 类 | 数量 | 已观测原因 |
|---|---:|---|
| PdfEvidencePipelineTest | 1 | 旧断言期望WEIGHT_SUM_MISMATCH/30，现有来源门返回ORIGINAL_TOTAL_UNKNOWN |
| CertificateScoreMysqlTest | 1 | 退休证书后的输入指纹未变 |
| OriginalScoreEvidenceRoleAndHeadingTest | 1 | 候选IN_SCHEME，但同artifact两行未覆盖，publishable=false |
| GoldenCorpusBaselineTest | 2 | 旧确定性抽取器父源格引用及纵横合并拆成3项；不经过本次逻辑行单元 |
| ScoreFactShadowPipelineTest | 1 | BigDecimal严格equals的80.00/80精度差异 |
| BaiduOcrFailureDiagnosticsTest | 3 | 测试未开gatewayEnabled，provider未执行 |
| LegacyScoringVersionMysqlTest | 2 | 旧stub/verify接口与当前openScoring、estimateSimpleScore等接口不符 |

前两类对照分别17项/5项，基线仅1/1失败；另外五类对照35项、9失败。Project1382原有两项在HEAD可通过，新来源限制曾导致它们失败；已按因果修复并复跑通过，不归入基线失败。

## 最终门禁与限制

最终验证由仓库review-workflow执行，精确命令、退出码、清理与独立审查报告保存在本任务Git元数据review-gate/state.json。该记录是审查结果的权威来源，本文件不预填未来审查结论。
最终验证须显式设置SIDECAR_PYTHON并包含真实1133、材料清单拒绝、合同OUT_OF_SCHEME排除、主客观反例、金标、原文/跨页及架构检查。

历史百度OCR原始响应未取得；重获PDF资产不是它的字节级替代。生产数据库/API/UI与OCR逐字准确性仍须在部署及新任务授权后验收。不能据本地回放宣称项目已在线恢复，也不能宣称全仓无回归。

## fix 第1轮：JaCoCo报告阶段失败核实

- 核对基线到工作区的完整已跟踪diff及全部未跟踪任务文件；本轮未修改业务实现或测试断言。
- 所给verify-1.log唯一Maven失败为JaCoCo 0.8.12报告读取覆盖率数据时的`Unknown block type 72`，发生在测试执行之后；不能将退出码1解释为业务断言失败。原有Surefire报告所列17类均为0失败、0错误，部分有跳过；旧报告仅作为诊断线索。
- 无监听保护重跑6类：Project1133RealFileReplayTest、Project1133SourceBoundaryTest、ScoreTypeClassificationPolicyTest、LogicalRuleSourceUnitTest、TextPrimaryEvidenceBinderTest、GoldenCasesReplayTest。140项、0失败、0错误、0跳过，Maven退出0，JaCoCo报告生成成功。
- 重跑命令：`env SIDECAR_PYTHON=/Users/user/xiyu/worktrees/trae/document-converter-sidecar/.venv/bin/python mvn -q -o -f backend/pom.xml test -Dtest=Project1133RealFileReplayTest,Project1133SourceBoundaryTest,ScoreTypeClassificationPolicyTest,LogicalRuleSourceUnitTest,TextPrimaryEvidenceBinderTest,GoldenCasesReplayTest -Dscoreparse.realArtifact=/tmp/1133-ocr-pdf-reextracted-artifact.json -Dscoreparse.realText=/tmp/1133-current-conversion.md -Djacoco.destFile=/tmp/scoreparse-1133-fix-20261117.exec -Djacoco.dataFile=/tmp/scoreparse-1133-fix-20261117.exec`。日志：`/tmp/scoreparse-1133-fix-20261117-tests.log`。没有关闭JaCoCo、删除测试或修改门禁。
- 独立覆盖率文件仅用于诊断；未改动原`backend/target/jacoco.exec`，因此不宣称原verify命令已恢复。需主执行器在verify阶段保全并重新生成失效的覆盖率产物，或显式使用独立的destFile/dataFile，再执行原完整测试集；该产物及验证配置不在本阶段原任务文件白名单，返回ESCALATE。数据为何损坏尚未查明，不推断为并发写入。
- 本轮只追加本验证记录；未提交、push、创建PR、部署、启动服务、嵌套review-workflow或写入自批凭证。

## 提交PR阶段

用户要求提交PR后，主执行器将原损坏JaCoCo产物移入任务Git私有取证目录保全，允许后续验证生成新覆盖率数据；不修改业务实现或测试预期、不禁用JaCoCo。重新运行仓库三技能门禁，实际结果以新的review-gate记录为准。生产部署与历史任务重解析仍不在本次PR授权内。

## PR独立审查意见处置

第一轮Google审查通过。西域审查发现Minor：Python _PDF_TOC_LEADER与Java目录过滤存在同步契约漂移，原修复阶段受文件白名单阻断，返回ESCALATE，未推送。主执行器复核源码并扩展本次目录修复范围至converter.py和既有test_pdf_table_artifact.py，不修改门禁配置或测试预期。三种横线页码目录的新断言在旧实现均失败；同步既有正则后，两个标题入口拒绝目录行且保留后续真实章节，PDF表格/转换68项通过。证据日志为/tmp/1133-python-toc-red.log和/tmp/1133-python-toc-green.log。该意见已修复，最终判定仍由后续完整门禁给出。

## ai-slop-cleaner 第1轮清理计划

- 检查基线到工作区的完整diff及未跟踪文件；仅在原任务白名单内清理。
- 行为保护先行：六类Java定向测试（含真实1133回放和金标）退出0，Python PDF表格测试64项通过。
- 气味为重复判断：将TextPrimaryEvidenceBinder中仅使用一次的unscopedRule布尔表达式内联到拒绝条件，删除重复的null及空状态检查；保留短路顺序和来源范围语义，不改正则或测试预期。
- 清理后复跑同一Java定向集合及diff空白检查；最终独立审查由主执行器继续执行。

### 本轮结果

- 仅清理TextPrimaryEvidenceBinder.java的重复条件，并追加本记录；没有修改测试、正则或其他业务文件。
- 清理前、后六类Java测试均退出0；清理后140项，失败/错误/跳过均0，包含真实PDF12项100分回放。命令沿用上方六类测试及真实输入参数，覆盖率destFile/dataFile分别使用`/tmp/scoreparse-1133-slop-protect.exec`和`/tmp/scoreparse-1133-slop-after.exec`；日志为同名前缀的`.log`。JaCoCo报告阶段未报错。
- `/Users/user/xiyu/worktrees/trae/document-converter-sidecar/.venv/bin/python -m pytest -q document-converter-sidecar/tests/test_pdf_table_artifact.py`退出0，64项通过，5条依赖弃用告警；`git diff --check`通过。
- 需要监听端口的测试、全仓回归及生产验收未在本阶段执行；没有生成审查凭证，后续verify及独立审查仍由主执行器负责。

## 真实PDF离线贯通验收

用户要求的补证完成，未继续修改业务实现。新增Project1133OfflineAcceptanceMysqlTest及专用进程/原文辅助Project1133OfflineFixture，使用同一真实PDF（SHA见上文）。流程为：真实本地存储上传→真实ProjectDocument记录→实际Sidecar /convert HTTP→InitiationTenderTextResolver→ScoreParseAppService.executeParseAsync→实际Sidecar /extract-tables HTTP→既有路由与来源核验→MySQL 8 Testcontainers→实际review Service/Controller经MockMvc DispatcherServlet响应。没有注入评分候选、没有Mockito替代解析/持久化/读取。任务创建与Worker同步调度用于确定性验收，未验收异步执行器调度。

采用任务248已观察的非版本化EVIDENCE_PIPELINE_V1路径，未装配VersionedScoreParseCompletion；Redis自动配置隔离，使用既有DB回退。没有使用百度增强器，历史百度原始响应仍为UNVERIFIED。访问身份采用现有ROLE_EXTERNAL_API授权路径；MockMvc只验证review端点真实映射和序列化，不验收浏览器、网关或OSS权限。数据库按既有MySQL测试模式由Hibernate建表，本用例不声明生产Flyway全迁移验证。

2026-11-17实际执行：1项、0失败、0错误、0跳过、Maven exit0。解析任务COMPLETED、AUTO_APPROVED；review仅「第七章 评分标准」一个方案、12项100分、六主观六客观、12项candidateEligible=true；同一任务的fact与正式score_item各12条。每个名称/权重/类型与用户确认清单匹配；接口sourceText/sourceCellIds与数据库一致；每项完整规则（只忽略排版空白及表格分隔符）覆盖由独立pdfplumber读取物理55—57页说明列及续行组成的完整原文，且引用单元格实际存在并覆盖该原文。材料占位和合计均未混入；商品覆盖与售后续文由全文对照覆盖。

初次运行的唯一错误为测试请求未指定Accept，Spring默认协商XML导致JSON读取失败；补JSON请求头后贯通通过，未修改业务代码。修复撤销对照：在独立classpath以基线92d522f8的五个Java类覆盖当前实现，保持新测试、PDF、Sidecar及其余代码相同；同一用例因期望AUTO_APPROVED、实际REVIEW_REQUIRED而失败（exit1），不是依赖/编译失败。

复现：`env SIDECAR_PYTHON=<已有完整Sidecar venv>/bin/python mvn -q -f backend/pom.xml test -Dtest=Project1133OfflineAcceptanceMysqlTest -Dscoreparse.realPdf=<本地真实PDF路径> -Djacoco.destFile=/tmp/1133-offline.exec -Djacoco.dataFile=/tmp/1133-offline.exec`。必须显式提供realPdf才能执行；不提供时按JUnit条件跳过，不可把跳过作为验收通过。客户文件、原始规则和完整review响应不入Git：日志、输入/输出哈希及反证保存在任务Git私有scoreparse-1133-evidence/offline-acceptance-20261117；最新运行输出在backend/target/project1133-offline-evidence（Git忽略）。

停止条件已达到：本轮只补测试及证据到PR，不修改解析业务代码，不处理11项无关基线失败、不部署、不新建或采纳生产任务。最终审查与推送门禁结果仍由Git私有review-gate记录。

主干集成：补证期间main新增案例推荐修复，PR出现Spec指针、Wiki日志与教训文档三处冲突。保留双方知识记录，Spec指针保持1133任务；合并主干26e0c1da3后重新验收。六个既有生产修改文件逐字节与PR提交68e19583e一致，本轮未添加业务修改。先前启动的审查因基线将改变而主动终止，不复用其未完成结果。

集成提交dd9f62d8d已完成，最终审查merge-base为26e0c1da3。未完成合并提交时工具曾把主干案例改动纳入清理，已停止该次流程，并将其一处未提交的冗余条件清理恢复为主干原字节；未提交或保留任何案例业务修改。最终验证增加六个原生产文件对68e19583e的字节一致性断言，约束本轮仅补测试和证据。
