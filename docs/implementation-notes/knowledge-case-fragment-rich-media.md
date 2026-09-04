# Implementation Notes: 案例应答片段 markdown 安全渲染（Phase 1）

## 需求口径

- `/knowledge/case` 详情弹窗「应答片段摘要」把还留着 markdown 表语法的 `responseText` 渲染成 HTML 表格，而不是 `pre-wrap` 纯文本。
- 不改卡片摘要、不改复用剪贴板、不改沉淀链路、不恢复真图。

## 不在方案里、落地时补的决策

1. **用 `renderSafeMarkdown`，不用 `marked.parse` + `safeHtml`**  
   Phase 1 不恢复真图。`safeHtml` 允许 `img`/`src`，会把 `![x](url)` 和裸 `<img>` 渲出来。`renderSafeMarkdown` 已允许 `table`，且 allowlist 不含 `img`，与 DualPane 因 H14 收口的图片面一致。真图另开任务。

2. **空值走 `hasResponseText`（非空字符串且 trim 后有内容）**  
   `''` / 空白 / `null` / 非字符串都不进 marked，直接「暂无应答内容」，避免空 `<p>` 或 `marked.parse` 对非字符串抛错。

3. **只给 `table` 设 `white-space: normal`，盒子本身继续继承 `pre-wrap`**  
   全文 `white-space: normal` 会把「联系人：\\n电话：」折成一行。评分项原文仍走 `.content-box` 的 `pre-wrap`；表格单独 normal，避免单元格被撑乱。

4. **字数仍按原始 `responseText.length`**  
   渲染成 HTML 后字符数会变，提示应对用户看到的入库原文，不是 DOM。

5. **复用仍 emit 原始 `caseData`**  
   复制路径在 `CaseGrid` / `AiRecommendDrawer`，本次不改。测试锁的是 emit 载荷不是 HTML。

## 验证

- 前端：`npx vitest run src/views/Knowledge/views/components/__tests__/CaseDetailDialog.spec.js` — 14 项通过。
- 本 worktree 不是开发宿主，未在浏览器打开 `/knowledge/case` 联调。
