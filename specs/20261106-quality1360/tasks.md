# 质量核查修复任务
- [x] T001 审查25项与现有退出校准，保存analysis.md及基线。
- [x] T002 [US1] Quality1360RegressionTest固定占位与原位证据RED。
- [ ] T003 [US1] ScoringResponsePolicy和QualificationVetoPolicy修复，保留真实必填缺陷对照。
- [x] T004 [US2] RuleCitationContext/SubItemAndAttachmentRules处罚和清单RED→GREEN。
- [x] T005 [US3] DeterministicPriceExtractor/PriceRequirement/PricingSubmissionPolicy及Engine系统报价RED→GREEN。
- [x] T006 [US4] IdAuthLetterCheckPolicy/SemanticEvidenceBoundary图片证据强度RED→GREEN。
- [x] T007 [US4] LlmAdjudicator完整输出示例与QualityCheckDetailDialog具体建议RED→GREEN。
- [x] T008 Mode B清理，完整合规和架构回归，构建，前端受影响测试。
- [ ] T009 独立审查、规格分析、Wiki及教训沉淀；生成verify/review工件。本地交付停止。
依赖：T001→T002→T003；T004到T007逐片执行；T008→T009。

## 第一轮审查后补证
- [ ] T010 [US1] 评分字段要求关联、跨行占位及原位证据反例RED→GREEN。
- [x] T011 [US2] 否定条目完整句范围与关键词后真实违规自述RED→GREEN。
- [x] T012 [US3] 保留混合表格义务、不同报价表别名与报价指令原位证据RED→GREEN。
- [x] T013 [US2] Report147已知缺陷默认启用，偏离表要求与真实招标原文绑定，不确定不PASS。
T010–T013须先于T008/T009最终验证；第一轮NEEDS_FIXES保留，第2轮从全量diff审查。

## 第三轮停止（2026-11-06）
T003/T010仍不完成：明确评分要求的服务协议被材料类型豁免；资格无真实分页时仍能合成第1页证据。T009最终验收不完成，独立审查已执行并留下NEEDS_FIXES凭据。已完成标记只对应有日志的局部行为与验证，不代表整个规格或线上验收通过。禁止自动第4轮；交接见handoff.md。

## 当前生效：用户裁定最小裁剪（2026-11-06）

用户已同意scope-reconciliation.md方案A：本次撤回自然文本评分留白自动FAIL，涉及所有项目此类检查，不做1360特判。评分留白仅召回人工核对及真实摘录；评分模型负结论也不单独判FAIL。无留白的评分保留原模型通道。资格缺陷、真实违规、负偏离、明确纸面提交义务的FAIL保护不变。

BA14取代旧BA05、BA10、BA13中评分直接必填自动FAIL及出现统计的承诺；保留这些输入作MANUAL回归，撤回ScoringRequirementRolePolicy，不重建统一字段解析器。其他BA条款仍有效。原25项6误判/8正确处理/11证据不足不变；不宣称全部25项准确或已线上生效。

历史三轮NEEDS_FIXES保留；人工裁定仅授权一次完整验收和独立审查窗口，再失败停止，不自动fix或重开。PR授权已有，不合并/部署/重算。health/codehealth不可用，本次显式以源码结构删减比较及三项既有架构测试替代，无工具健康评分声明。
