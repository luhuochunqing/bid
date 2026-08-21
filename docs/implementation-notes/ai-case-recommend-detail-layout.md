# Implementation Notes: AI 智能案例推荐详情布局

## 需求口径

- 案例卡片、案例详情不再展示「复用次数」。
- 详情弹窗固定为三模块：顶部评分项；中部三层（评分项详细要素原文 / 应答片段原文 / 案例元信息 6 字段）；底部「复用」按钮。
- 元信息 6 字段：项目名称 / 招标主体 / 创建时间 / 项目状态 / 客户类型 / 项目类型。

## 不在 spec 中、落地时补的决策

1. **只改 AI 推荐入口，不改案例库**  
   知识库 `CaseCard` / `CaseDetailDrawer` 仍保留复用次数（案例库页本身按热度统计）。本次只覆盖标书制作里的 AI 智能推荐。

2. **招标主体、项目状态不落案例表**  
   `knowledge_case` 没有招标主体/项目状态列。推荐接口按 `sourceProjectId` 批量查来源项目和标讯：招标主体优先 `Tender.purchaserName`，空则回退 `tenderAgency`；项目状态取 `Project.status` 枚举名，前端用 `getProjectStatusText` 显示中文。创建时间用案例 `createdAt`，格式 `yyyy-MM-dd`。

3. **双套详情组件一起改**  
   线上抽屉走 `AiRecommendDrawer` + `AiRecommendDualPaneDialog`。`AICaseRecommendCard` / `AICaseDetailModal` 当前未挂到 DraftingStage，但同属 AI 推荐 UI，一并去掉复用次数并对齐三模块，避免后续接线再回退。

4. **详情不再用双栏原文阅读器**  
   原 DualPane 左侧元数据 + 右侧 HTML 高亮阅读器被三模块竖排替换。应答片段改为纯文本 `pre-wrap`，不再 `v-html`。

5. **底部只留「复用」**  
   关闭走弹窗右上角 X。复用行为未改：仍复制应答原文并调用 `reuseCase`。后端 `reuse_count` 继续累加，只是 UI 不再展示。

6. **元信息空值统一为 `-`**  
   客户类型/项目类型不再走案例库的「通用客户」「常规项目」兜底，避免空字段看起来像真实分类。

## 验证

- 前端：`npx vitest run` 覆盖 DualPaneDialog / Drawer / AICaseRecommendDrawer / AICaseDetailModal，12 项通过。
- 后端：`ScoreItemCaseRecommendAppServiceTest`、`ScoreItemCaseSourceMetaLoaderTest`、`ScoreItemCaseRecommendControllerTest` 通过。
- 本 worktree 不是主工作区，未在浏览器联调。
