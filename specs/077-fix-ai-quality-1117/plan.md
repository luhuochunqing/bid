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
