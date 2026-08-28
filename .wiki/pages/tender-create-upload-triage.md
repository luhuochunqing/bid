---
title: 标讯人工录入"未上传"假象排障与 UX 缺陷修复
space: engineering
category: pitfalls
tags: [标讯, 人工录入, el-upload, canSave, UX, 生产排障]
sources:
  - src/views/Bidding/list/composables/useTenderAiParse.js
  - src/views/Bidding/list/composables/useTenderCreateForm.js
  - src/views/Bidding/list/components/TenderActionBar.vue
  - src/views/Bidding/list/components/TenderBasicInfoTab.vue
backlinks:
  - .wiki/pages/_index.md
  - .wiki/pages/frontend-pitfalls.md
created: 2026-08-28
updated: 2026-08-28
health_checked: 2026-08-28
---

# 标讯人工录入"未上传"假象排障与 UX 缺陷修复

> 生产案例（2026-08-28，winbid-01 / release 8f568d5cc）：用户 03683 在 `/bidding/create`
> 上传 docx + AI 解析全部成功，但标讯从未创建。用户感知"没报错但没成功、附件一直显示未上传"。

## 排障方法论（三方证据交叉）

| 证据源 | 命令/位置 | 关键发现 |
|---|---|---|
| Nginx access.log | `/var/log/nginx/access.log` 过滤 userId | 全天仅 4 个 POST（read-all/parse/store/parse-existing），**无 POST /api/tenders** |
| 后端 journalctl | `journalctl -u xiyu-bid-backend` 按 traceId | store 200（187KB docx，1ms）→ parse-existing 200（53s，提取 64921 字符），后端全程正常 |
| MySQL winbid 库 | `users` / `tenders` 表 | 用户 8 月创建记录 = 0，当天全平台人工录入 = 0 |

**结论**：标讯从未创建的根因不在后端——浏览器从未发出创建请求。
生产 DB 名是 `winbid`（非 xiyu_bid_main），用户表是 `users`（非 sys_user）。

## 根因链（3 个叠加 UX 缺陷）

1. **保存按钮静默禁用**：`TenderActionBar.vue` 保存按钮 `:disabled="!canSave || parsingDocument"`，
   禁用无任何提示。`canSave` 原要求 7 个必填字段全非空（title/purchaser/region/deadline/
   bidOpeningTime/customerType/priority）。注意 `MANUAL_FORM_RULES` 实际有 8 个 required 字段
   （多 projectType 项目类型）——修复时口径已对齐 8 项，否则 tooltip/AI 提示漏报、用户点保存
   仍被表单红字拦截（PR !2450 审查发现，属"平行定义漂移"陷阱）。
2. **公开谈判采购无开标时间**：后端 `TenderDocumentPrompts` 口径是"只有投标截止时间没有开标时间则
   bidOpeningTime 留空"→ AI 回填后 canSave 恒 false，用户点保存无反馈。
3. **el-upload auto-upload=false 状态假象**：`:auto-upload="false"` 时上传由自定义
   `handleFileChange` 完成（store 成功即文件已保存），但 el-upload 文件对象 `status` 恒为
   `ready`，永远不显示绿色 ✓，用户误以为"未上传"。

## 副作用（Cross-Module Impact）

- **el-upload status 机制**：`file.status = 'success'` 会让列表项加 `is-success` class 显示
  CircleCheck 图标（Element Plus upload-list 源码确认）；附件对象与 `form.attachments` 是同一
  引用，回填 fileUrl 时顺手设 status 即可。
- **tooltip on disabled button**：Element Plus 中 disabled 按钮不触发鼠标事件，el-tooltip 必须
  包一层 `<span>` 才能生效。
- **两条录入链路**：create 页（useTenderAiParse + TenderCreatePage）与列表页弹窗
  （useManualTenderCreate + ManualTenderDialog）附件回填逻辑重复实现（applyAttachmentFileUrl vs
  applySourceDocumentMetadata），改一处必须检查另一处。
- **弹窗版保存不受此 bug 影响**：ManualTenderDialog 保存按钮不禁用，点击时才校验并出红字。

## 修复方案（本次 PR）

| 缺陷 | 修复 |
|---|---|
| 保存禁用无提示 | TenderCreatePage 计算 `saveDisabledReason` 传给 TenderActionBar，el-tooltip 显示"还差必填项：开标时间、客户类型" |
| 附件无成功标识 | 回填 fileUrl 时设 `target.status = 'success'`（两条链路都改） |
| AI 回填后不知道缺啥 | 解析成功后用 `getMissingTenderRequiredFields` 计算（8 项口径，与 MANUAL_FORM_RULES 对齐），ElMessage.warning 列出缺失必填项 |

共享工具：`src/views/Bidding/list/tenderRequiredFields.js`（必填字段 key+label 表 + 缺失计算函数）。

## 变更日志

- **2026-08-28**：初版，源自生产排障案例（traceId 6d0a8d2749ed4aafbb55ac9248c1a9fb）。
