---
title: 验收导出——未开放功能代码级剔除方法论（隐藏菜单识别 + 共享符号白名单 + 删除型契约验证）
space: engineering
category: refactoring
tags: [refactoring, dead-code, feature-removal, acceptance-export, deletion-first, hidden-menu, shared-symbol-whitelist]
created: 2026-09-22
updated: 2026-09-22
health_checked: 2026-09-22
sources:
  - src/config/sidebar-menu.js
  - src/router/index.js
  - src/views/Dashboard/workbench-quick-start-core.js
  - src/views/Dashboard/useWorkbenchQuickStart.js
  - src/composables/projectDetail/useProjectDetailNavigation.js
  - specs/067-export-hide-features/spec.md
  - specs/067-export-hide-features/contracts/removal-contract.md
  - specs/067-export-hide-features/verify-removal.sh
backlinks:
  - _index
---

# 验收导出——未开放功能代码级剔除方法论

> 触发场景：向甲方/客户导出源代码做验收，需要把"未对客户开放的功能"从代码包里彻底移除。
> 本文沉淀 2026-09-22 spec 067 的完整方法（8 个隐藏功能 + 8 个死页面剔除，仅前端）。

## 1. 第一步：识别"隐藏但没删"的功能

**不要按功能名猜**（如"双屏预览"在代码里可能零命中）。按机制找：

| 机制 | 位置 | 含义 |
|---|---|---|
| `hiddenApiMenuNames` 集合 | `src/config/sidebar-menu.js` | 菜单硬隐藏名单，但路由与页面仍在 |
| `isApiDeliveryMode` 恒真开关 | `src/components/layout/Sidebar.vue` | 交付模式，激活上述隐藏 |
| 路由 redirect 到别处 | `src/router/index.js` | 功能被"软封死"（如 CustomerOpportunityCenter） |
| `HIDDEN_API_ROUTES` 守卫 | `src/router/index.js` | 直达 URL 被重定向 |
| 孤立页面（零 import 零路由） | 全局 | 从未接入系统的死代码 |

**判定孤立页面的正确方法**：用 basename 搜 `import`，不要用完整路径搜——相对路径 `./Xxx.vue` 会漏判。
且注意"同名不同物"：`views/Analytics/CompetitionIntel.vue`（死）vs `components/ai/CompetitionIntel.vue`（现役）。

## 2. 第二步：共享符号白名单（防误伤，最关键）

删除前对每个待删模块做"消费方审计"，任何有 ≥1 个现役消费方的符号必须保留。spec 067 实战白名单：

| 符号 | 看似属于被删功能 | 实际消费方（保留原因） |
|---|---|---|
| `AuditLogPanel.vue` | 审计日志页 | 日志说明页 `OperationLogPage.vue`（mode="operation"） |
| `customerOpportunity.js` | 客户商机中心 | 项目创建流程 `convertToProject` 商机转化 |
| `knowledgeApi.templates` | 模板库页 | 项目详情 Boot/Init、文档编辑器知识选择 |
| `components/ai/CompetitionIntel.vue` | 旧版分析页 | 项目详情 FeatureModals |
| `FavoriteButton.vue` + `tenderFavorites.js` | 收藏列表页 | 标讯列表收藏按钮 |
| `AI/MarketTiming.vue` | 市场预测死页 | 现役路由 `/ai-center/market-timing` |
| `useDocumentAssembly.js` | 文档组装死页 | 标书编辑器 `Editor.vue` |

**同对话框双模式拆分**（手术式）：工作台"借阅"对话框同时承载资质借阅（现役）与合同借阅（隐藏），
只摘 `mode === 'contract'` 分支（UI 字段/校验/payload/API 接线），`mode: 'qualification'` 完整保留。
**不要整体删除入口**。

## 3. 第三步：删除清单驱动 + 批次原子提交

按 `specs/<N>-<slug>/data-model.md` 的删除单元（Deletion Unit）表执行，分 3 批：

- **批次 A**：隐藏功能（路由 + 页面 + 专属辅助 + 联动入口）
- **批次 B**：死代码孤立页面（纯删除，零风险）
- **批次 C**：联动入口手术 + 机制收尾（hiddenApiMenuNames / isApiDeliveryMode / 死 API 模块）

每批 = `git rm` → 全量 `npm run test:unit` → 契约断言 → 原子提交。失败可单批回滚二分定位。

**联动入口必须全找**：删除页面后，其他页面里的跳转按钮/页签/快捷项都是死链接。spec 067 实战发现：
项目详情有 6 个跳转函数（goToResultPage/goToExpensePage/goToAssetManagement/goToSiteDetail/borrowUK/viewSOP）、
资产检查面板 4 个死按钮、费用汇总卡"管理"按钮、创建页"前往资产管理"、Settings 审计页签、工作台快速发起合同模式。
**用 grep 消费方反查，不要靠人眼**。

## 4. 第四步：删除型任务的"测试证据"范式

删除型任务没有新行为可写红测试，等价验证 = 三件套：

1. **专属测试随删**（页面专属 spec / e2e 整文件删除）
2. **共享测试修订为剔除断言**（如 `expect(wrapper.text()).not.toContain('费用管理')`）
3. **契约脚本断言**（`verify-removal.sh`：Part I 必须消失 / Part II 必须存在）

⚠️ 项目 TDD 门禁（`check-tdd-coverage.sh`）要求每个被改的业务文件有对应 spec。
删除型改动会触发误报（文件被改但 spec 不存在）——为被改组件补"剔除行为"断言 spec 即可合规：
```js
expect(wrapper.text()).not.toContain('已删入口文案')
expect(navigation.goToResultPage).toBeUndefined()
```

## 5. 陷阱清单

- **契约 grep 例外要精确**：`/api/customer-opportunities/*`、`/api/knowledge/templates` 等后端 API 路径字符串合法保留（后端接口不在剔除范围）；用 `grep -v "/api/"` 过滤
- **负向断言会被 grep 命中**：spec 里 `toBeUndefined()` 断言会命中符号 grep，契约脚本需 `grep -v "toBeUndefined"`
- **文档治理清单要同步**：`scripts/check-doc-governance.mjs` 硬编码受管目录列表，删目录后必须移除条目，否则 `npm run build` 失败
- **hot-path 锁**：改 `src/router/index.js` 前需 `npm run agent:lock-acquire`（否则 pre-commit 拒绝）
- **构建产物验证**：`npm run build` 后 grep `dist/assets/*.js` 确认零被删功能文案残留
- **`Analytics/dashboard/utils/chartOptions.js` 类假共享**：注释里提到同名文件 ≠ import，必须看 `import ... from` 实际路径
