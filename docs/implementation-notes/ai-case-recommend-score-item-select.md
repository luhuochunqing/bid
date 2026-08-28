# AI 智能案例推荐 · 评分项下拉短标签

日期：2026-08-28  
分支：`agent/grok/score-item-select-short-label`

## 规格里没有写清、实现时拍板的决定

1. **「序号」= `code`（解析表「编号」），不是 `itemIndex`。**  
   `ScoreItemDTO` 不下发 `itemIndex`，解析表也展示 `code`。选项文案为 `{code} {dim}`。

2. **Tooltip 只展示 `detail`（标书原文），不再拼编号/名称。**  
   短标签已经在选项行里，tooltip 重复编号会把长原文挤出视口。

3. **取消按 `dim` 分组。**  
   `dim` 是评分项名称不是类目，分组会造成「组名=技术方案 / 选项=3.1 技术方案」。改为扁平列表。

4. **下拉内搜索同时匹配 `code` / `dim` / `detail`。**  
   缩短 `label` 后 Element Plus 默认 filterable 搜不到原文关键词，用 `filter-method` + 共享 `scoreItemMatchesQuery`。

5. **选中后在下拉下方常驻「详细要素（标书原文）」摘要。**  
   补齐无 hover 场景（触控、键盘、收起后核对）。不改后端契约。

6. **两处入口对齐。**  
   抽了共享组件 `src/components/common/ScoreItemSelect.vue`（短标签 + tooltip + 预览 + 原文过滤），项目详情抽屉与招标侧抽屉共用。显示规则在 `src/utils/scoreItemOptionDisplay.js`。  
   招标侧与「评分项：」标签并排时，用父级 `.item-score-select .score-item-select { flex: 1 }` 控制宽度，不在组件上开 `selectClass` 透传到内部 `el-select`（那一层不参与父 flex）。

7. **Tooltip 挂到 body，限制宽高可滚动。**  
   下拉 popper 内长原文会被裁切；`teleported` + `.score-item-option-tooltip` `max-width:400px; max-height:240px; overflow-y:auto`。

## 审查修复轮（2026-08-28，双 reviewer REQUEST CHANGES 后）

1. **删 `defineExpose({ filterScoreItems, onScoreSelectVisible })`（HIGH）。**  
   它只为单测 `wrapper.vm.*` 服务，属生产代码向测试妥协。测试改为：`el-select` stub 声明 `filterMethod` prop，模板内置 `.select-filter-input`（input 时回调 `filterMethod`）与 `.select-close-btn`（点击 emit `visible-change: false`）两个交互钩子，用例从 DOM 触发，顺带把「组件确实把 `filter-method` / `visible-change` 接到 el-select」纳入断言路径（之前戳 vm 方法时这条连线是没测到的）。

2. **招标侧 `.item-score-select { align-items: flex-start }`（MEDIUM）。**  
   `.filter-item` 默认 `center` 会让「评分项：」标签垂直居中到「下拉 + 最高 120px 预览」整块上，改顶部对齐。未动 `.filter-item` 本身，其他 filter 项仍居中。

3. **flex 宽度 DOM 锁（LOW）。**  
   宽度规则 `.item-score-select .score-item-select` 依赖子组件根 class，重构改名会让规则静默失效。招标抽屉 spec 挂载后断言该选择器存在。

## 审查遗留（非阻断，未做）

- 下拉缺 `for` / `aria-label`（抽屉里「评分项：」是光秃 `<span>`）。审查认可 aria-live 预览已覆盖无 hover 路径；补齐需要动两个抽屉的 label 结构，留待专项。

## 未做

- 选项旁带权重分值（加料，用户未要求）
- 后端 DTO 增补 `itemIndex`
