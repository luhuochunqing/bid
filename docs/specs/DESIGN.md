# Design System — 西域数智化投标管理平台

## Product Context
- **What this is:** 面向企业投标全生命周期管理的私有化 Web 平台，覆盖商机获取、项目协同、知识复用、资源管理、经营分析与系统治理。
- **Who it's for:** 销售人员、投标经理、项目经理、部门管理者、系统管理员。
- **Space/industry:** B2B 企业数字化管理 / 招投标管理系统。
- **Project type:** 数据密集型业务 Web App + Dashboard。

## Aesthetic Direction
- **Direction:** Industrial/Utilitarian（工业化执行中台风格）
- **Decoration level:** intentional（有节制的背景层次与状态强调，不做装饰性炫技）
- **Mood:** 专业、稳定、可信、执行导向；优先信息清晰度和决策效率。
- **Reference sites:** 本轮按你的要求优先结合现有页面现状建立标准，未启用外部竞品调研。

## Typography
- **Display/Hero:** Plus Jakarta Sans（沿用当前项目字体资产，降低迁移成本）
- **Body:** Plus Jakarta Sans（中文回退：`PingFang SC`、`Microsoft YaHei`）
- **UI/Labels:** Plus Jakarta Sans（与 Body 一致，保证系统一致性）
- **Data/Tables:** Plus Jakarta Sans + `font-variant-numeric: tabular-nums`（关键数字列）
- **Code:** JetBrains Mono
- **Loading:** `@import url('https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700&display=swap');`
- **Scale:**
  - `xs: 12px`
  - `sm: 14px`
  - `base: 16px`
  - `lg: 18px`
  - `xl: 20px`
  - `2xl: 24px`
  - `3xl: 32px`

## Color
- **Approach:** balanced（品牌主色 + 中性色阶 + 语义状态色）
- **Primary:** `#0066CC` — 主要操作、链接、核心高亮
- **Secondary:** `#0052A3` — 主色按下态与深层级强调
- **Xiyu Logo Green:** `#2E7659` / RGB `46, 118, 89` — 西域 Logo 标准色。用于品牌识别、工作台关键选中态、空态操作和需要体现西域品牌的低频强调；大面积背景必须使用浅色衍生层，避免压低文字对比度。
- **Neutrals:** `#F5F7FA` / `#E8E8E8` / `#D0D0D0` / `#999999` / `#666666` / `#1A1A1A`
- **Semantic:** success `#00AA44`, warning `#FF8800`, error `#DD2200`, info `#0066CC`
- **Dark mode:** 不是当前默认交付目标；若启用，采用 token 映射策略（降低饱和度 10-20%，重算表面层级），禁止页面级硬编码暗色值。

## Spacing
- **Base unit:** 4px（8px 为主节奏）
- **Density:** comfortable
- **Scale:** 2xs(2) xs(4) sm(8) md(16) lg(24) xl(32) 2xl(48) 3xl(64)

## Layout
- **Approach:** grid-disciplined
- **Grid:**
  - mobile `<768`: 4 列
  - tablet `>=768`: 8 列
  - desktop `>=1024`: 12 列
  - wide `>=1440`: 12 列
- **Max content width:** 1280px（数据页面）
- **Border radius:** sm 4px, md 8px, lg 12px, xl 16px, full 9999px

## Motion
- **Approach:** minimal-functional
- **Easing:** enter(ease-out) exit(ease-in) move(ease-in-out)
- **Duration:** micro(50-100ms) short(150-250ms) medium(250-400ms) long(400-700ms)

## SAFE / RISK Decisions
- **SAFE（保持行业可读性）**
  - 继续使用蓝色品牌主轴与浅背景中性色阶，符合企业后台认知。
  - 采用规则化栅格与统一圆角层级，保障跨模块一致性。
  - 动效维持功能型，避免影响高频业务操作效率。
- **RISK（建立产品识别度）**
  - 在关键驾驶舱模块引入更清晰的信息分区层级（而非装饰性视觉），提升“指挥中枢”辨识。
  - 标题与关键指标强化字重对比，塑造更明确的管理驾驶舱气质。
  - 限制语义色滥用，把色彩聚焦在状态与风险提示，牺牲部分“热闹感”换取决策清晰度。

## Current Baseline Diagnosis
- 全局样式基线已接入，但页面层存在较多局部样式自管。
- 快审数据（2026-04-22）：`scoped` 样式块约 109 处，硬编码颜色约 1470 处，`var(--token)` 引用约 139 处。
- 治理策略：先冻结标准，再按模块增量收敛，避免一次性大改造成回归风险。

## Implementation Guardrails
1. 新增 UI 必须优先使用 token（颜色/字号/间距/圆角），禁止新增硬编码视觉值。
2. `scoped` 仅处理局部布局，视觉基础规范必须回归全局 token。
3. 状态色只用于状态表达，不作为主视觉主题色。
4. 交互组件必须保留明确 focus 态，满足可访问性。
5. 非品牌活动页不新增高饱和渐变或独立视觉语言。

## Incremental Adoption Plan
1. 立即生效：从新需求开始执行 DESIGN.md，不再扩散样式债。
2. 第一批收敛：`MainLayout` / `Header` / `Sidebar`。
3. 第二批收敛：`Dashboard/Workbench`、`Bidding`、`Resource` 高频页面。
4. 第三批收敛：低频与历史模块按触发式修整。

## Decisions Log
| Date | Decision | Rationale |
|------|----------|-----------|
| 2026-04-22 | Initial design system created | Created by /design-consultation based on current product context and existing page baseline |
| 2026-04-22 | Set industrial utilitarian direction | Fits data-dense bid-management workflows and reduces visual drift across modules |
| 2026-04-25 | Added Xiyu Logo Green `#2E7659` | Aligns Workbench interaction details with the 西域 Logo color while preserving readable light surfaces |
