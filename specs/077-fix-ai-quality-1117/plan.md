# Implementation Plan: 项目 1117 AI 标书质量核查纠偏

**Branch**: `agent/claude/fix-ai-quality-1117` | **Date**: 2026-10-23 | **Spec**: [spec.md](./spec.md)

## Summary

以项目 1117 的生产证据构造脱敏回归样本，修复确定性规则中的空格日期、CA 正反证据短路和保证金假失败；修复 ISO 组合要求的原子化关联；确认并保护评分项主客观分类与激活版本边界。仅交付代码与验证工件，不操作生产。

## Technical Context

**Language/Version**: Java 21
**Framework**: Spring Boot 3.2，纯领域策略类
**Storage**: MySQL 8（本次测试不依赖生产库）
**Testing**: JUnit 5、AssertJ
**Constraints**: 真实 API、无生产写入、FP-Java 纯核心、最小 diff

## Constitution Check

- [x] 真实 API 唯一源：无新增 Mock 业务路径。
- [x] 复杂任务走 Spec Kit：spec/plan/tasks 完整。
- [x] CoW 沙盒隔离：不在 main 基准区实施。
- [x] FP-Java：规则优先纯函数测试。
- [x] 原子变更与每变必测：三条根因链 RED→GREEN。
- [x] 生产安全：不重算、不激活、不部署。

## Project Structure

```text
specs/077-fix-ai-quality-1117/
backend/src/main/java/com/xiyu/bid/compliance/{domain/policy,service}/
backend/src/main/java/com/xiyu/bid/scoreparse/domain/
backend/src/test/java/com/xiyu/bid/{compliance,scoreparse}/
```

## Design

1. 加入项目 1117 的最小文本特征，先证明失败。
2. 日期扩展格式容忍度；CA 收集正反证据后决策，具体电子证据优先于泛化纸质模板词。
3. 保证金规则把“只有声明、图片不可读”映射为 MANUAL_REVIEW。
4. ISO 规范化并提取标准代码，再逐项匹配知识库记录。
5. 分类策略若当前已满足样本，仅补回归保护；激活/候选边界只核验。

## Verification Gates

- 目标 JUnit RED/GREEN；相关模块回归；ArchitectureTest。
- `git diff --check`、范围审计、AI slop 清理 Mode B。
- xiyu-code-review、code-review、production-risk-review。
- working_tree 交付，不 commit/push/PR。

## Complexity Tracking

无宪法例外。已有正确行为只补测试，不制造实现 diff。

## 2026-10-25 追加计划：CA 平台名称与台账匹配
隔离目录 `/Users/user/.herdr-controller/clones/xiyu-ca-platform-1025`，分支 agent/codex/fix-ca-platform-1025，基线3e82ffde。设计已在对话批准；本轮为既有Spec Kit后续修复，不新建feature。可选自动commit hooks跳过，delivery_target=working_tree。
1. RED：新增真实故障形态的纯核心测试（泛称、招商全称/简称、冲突、查询失败）。
2. 实现：CaPlatformNamePolicy集中筛选候选和规范别名，提取器不再首命中；CaCertificateRequirement携带来源证据，确定性融合不恢复无效LLM平台；CaCertificateEvidence复用别名规则；CA策略对未确认平台与失败查询转人工。
3. Mode B清理：移除被替代的首命中正则与重复规范逻辑，不扩展通用OCR或改保证金。
4. GREEN：相关JUnit、compliance/资源适配器、架构测试、前端构建、diff检查；独立三透镜评审与规格一致性分析。生产复验属于后续发布门禁。

## 人工裁决后最小方案
在CaPlatformNamePolicy新增正向身份判定，与既有identity别名共享边界；CaCertificateCheckPolicy在台账匹配/过期/通过之前检查身份，未确认者保留原候选并MANUAL_REVIEW。不继续添加泛称黑名单，不新增数据库或外部调用。新增纯核心回归覆盖未知名及matched标志不等于身份，重跑上轮关联/架构回归和冻结PDF回放。

## PR交付追加授权（2026-10-25）
用户要求创建Gitee PR。保留用户已批准的实现与保守身份边界，原子提交、同步origin/main、通过本地ci:pre-pr及真实pre-push门禁后创建PR；不合并、不部署。先前working_tree描述是实现阶段的交付上限。
