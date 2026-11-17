# Spec Kit 一致性检查

- FR-001→T003/T004→Project1133SourceBoundaryTest，目录/方法说明排除，真实多方案保留。
- FR-002→T005→TextPrimaryEvidenceBinderTest，拒绝材料清单，保留范围内与无章节实际规则。
- FR-003→T006→LogicalRuleSourceUnitTest及真实PDF回放，汇总边界与跨页完整性。
- FR-004→T007→ScoreTypeClassificationPolicyTest及真实12项类型比对，保留数量门槛反例。
- FR-005/SC-001/SC-002→T008/T009→金标及真实12项100分/六主观六客观。
- FR-006→无生产写操作；全局来源门禁未放宽，数据库/生产验收单独记录。
- FR-007→五处基线替换均被断言捕获；原始RED及反证日志存任务私有证据目录。
- 所有需求均有任务和可执行验证；无宪法冲突。T009/T010的回归执行与基线归因已记录，全仓测试未完成；最终清理、验证和独立审查进行中，生产验收未执行。
- 本仓库check-prerequisites旧实现无条件检查编号分支；使用其支持的SPECIFY_FEATURE=20261117-scoreparse-source-boundaries选择规格，不修改既有受管任务分支。
