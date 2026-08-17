# Research: PR !2292 验收缺陷修复

## R1 客观项空预计得分的展示口径（P1 根因）

- **Decision**: 归一化层保留 `null`，展示层复用既有空值分支渲染"待确认"
- **Rationale**: 全链路审查确认展示组件（ScoreParseTable / ScoreItemDetailModal）对非数字得分已有 `'待确认'` + subjective 样式的 fallback 分支，合计函数只对 `typeof number` 求和；唯一污染源是 `normalizeScoreItem` 的 `: 0` 缺省。单点修复把变更面与回归风险降到最低
- **Alternatives**: ①后端对"待确认"客观项返回 `"待确认"` 字符串——破坏 DTO 数值类型契约（estScore: number|null），被否决；②展示层增加 null 判断而归一化仍转 0——语义上 0 与 null 已不可区分，无法修复，被否决

## R2 详情弹窗 70vh 的实现路径

- **Decision**: 非 scoped 样式块控制 `.score-item-detail-dialog`（el-dialog 已 `append-to-body`，挂载于 body，scoped 样式不可达）
- **Rationale**: Element Plus el-dialog 无内建 max-height 参数；原型 V3 用原生 `.detail-modal { max-height: 70vh }` + body 滚动，等价迁移
- **Alternatives**: `el-scrollbar` 包裹 body——引入额外组件复杂度，无收益，被否决

## R3 待确认状态颜色

- **Decision**: 文字 `var(--text-muted)`（灰）+ 圆点 `var(--brand-primary)`（蓝）
- **Rationale**: PRD 6.5 明文"灰色文字，前面带蓝色圆点"；原型 CSS `.status-cell.neutral` 灰字、`::before` 圆点取 `--brand-primary`。当前实现把文字也染成 info 蓝，属对齐遗漏
- **Alternatives**: 无（PRD 与原型双源一致）

## R4 50MB 文案

- **Decision**: 后端 `validateFile` 消息改为 PRD 5.3 原文
- **Rationale**: 后端文案经 ApiResponse 直达前端 toast；PRD 5.3 给出了逐字文案。前端 DraftingStage 既有"投标文件不能超过 50MB"属通用上传区提示，语义一致，不在本 feature 强改（避免波及其它上传入口）
- **Alternatives**: 只改前端不改后端——后端才是 PRD 5.3"后端二次校验"的责任方，被否决

## R5 声明更正 vs 补自动打分行为

- **Decision**: 更正 specs/042 T03 与 handoff 声明为"手动触发"，不补自动打分
- **Rationale**: PRD 1.1 明文阶段 2 由用户点击触发；原型自动打分为演示行为且基线 !2298 刚刚收口了"打开抽屉不再自动重打"的门闩（方向一致）。改声明是让文档服从事实与 PRD
- **Alternatives**: 实现自动打分——与 PRD 冲突且与 !2298 门闩方向相反，被否决

## R6 无 NEEDS CLARIFICATION 遗留

范围类待决（超 PRD 工具按钮去留、仓库类空值 0/待确认口径）已在 spec Assumptions 显式排除，属产品决策，不阻塞实施。
