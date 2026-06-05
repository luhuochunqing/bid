一旦我所属的文件夹有所变化，请更新我。

# 项目级招标文件解析与任务拆解流程

本文说明项目详情页中“解析招标文件”“拆解任务”“AI 生成初稿”三类能力的边界和使用路径。
这条链路只走真实后端 API，不新增 mock/demo 分支。

## 1. 产品路径

| 页面入口 | 用户动作 | 后端结果 | 后续复用 |
|----------|----------|----------|----------|
| 项目详情页 `解析招标文件` | 上传项目对应的招标文件 | 解析并写入招标文件快照与需求项 | 任务拆解、AI 生成初稿 |
| 项目详情页 `拆解任务` | 基于已解析结果生成协作任务 | 创建项目任务并刷新任务看板 | 项目执行跟踪 |
| 项目详情页 `评分标准拆解` | 拆解评分标准或评分文件 | 更新评分相关草稿或评分项 | 评分覆盖检查 |
| AI 生成初稿 | 基于招标要求和项目上下文生成标书初稿 | 写入标书生成 Agent 的草稿和章节计划 | 文档编辑器、合规检查 |

## 2. 设计决策

1. 标讯导入不强制同步解析整份招标文件。
   标讯不一定都会转项目，导入阶段做完整解析会拖慢标讯录入。
2. 项目级解析是独立能力。
   它不是 AI 生成初稿的附属步骤，而是项目进入执行后对招标文件做结构化拆解的入口。
3. 任务拆解与 AI 初稿复用同一份解析结果。
   解析结果优先落到 `bid_tender_document_snapshots` 和 `bid_requirement_items`，后续能力按需读取。
4. “评分标准拆解”保留为独立入口。
   它服务评分覆盖和评分草稿，不再冒充主任务看板的“拆解任务”按钮。

## 3. 后端链路

### 3.1 解析招标文件

| 层次 | 文件/模块 | 职责 |
|------|-----------|------|
| Controller | `projecttenderbreakdown/controller/ProjectTenderBreakdownController.java` | 暴露 `/api/projects/{projectId}/tender-breakdown` 与 `/readiness` |
| Application | `ProjectTenderBreakdownReadinessService` | 做项目权限守卫和解析配置就绪检查 |
| 复用服务 | `BidTenderDocumentImportAppService` | 保存上传文件、抽取正文、调用招标文件解析器、写入快照和需求项 |
| 配置检查 | `TenderIntakeConfigurationReadiness` | 检查 DeepSeek provider key 或 `DEEPSEEK_API_KEY` 是否可用 |

API：

```http
GET  /api/projects/{projectId}/tender-breakdown/readiness
POST /api/projects/{projectId}/tender-breakdown
Content-Type: multipart/form-data
```

### 3.2 拆解任务

| 层次 | 文件/模块 | 职责 |
|------|-----------|------|
| Controller | `projectworkflow/controller/ProjectWorkflowController.java` | 暴露 `/api/projects/{projectId}/tasks/decompose` |
| Application | `ProjectTaskBreakdownService` | 权限守卫、读取来源、调用纯核心、保存任务 |
| Source Reader | `ProjectTaskBreakdownSourceReader` | 优先读取 `bid_requirement_items`，为空时回退到 `document_sections` 顶层/二级章节 |
| Pure Core | `TaskBreakdownPolicy` | 根据来源快照和已有任务快照输出待创建任务决策 |
| Creator | `ProjectTaskBreakdownTaskCreator` | 将决策落成 `Task` 并转换为任务看板 DTO |

API：

```http
POST /api/projects/{projectId}/tasks/decompose
```

返回：

```json
{
  "success": true,
  "data": [
    {
      "title": "商务响应文件编制",
      "status": "TODO",
      "priority": "HIGH"
    }
  ]
}
```

无可用解析来源时返回业务错误：

```text
未找到可用于拆解任务的标书拆解结果
```

## 4. 任务生成规则

`TaskBreakdownPolicy` 是纯核心：不访问数据库、不读时间、不写日志、不修改入参。

| 来源类别 | 生成任务方向 |
|----------|--------------|
| 商务类 | 商务响应、资质、报价任务 |
| 技术类 | 技术方案、实施方案任务 |
| 材料/资格类 | 资料收集、证明材料准备任务 |
| 评分类 | 评分点复核任务 |
| 其他类 | 通用复核和补充确认任务 |

去重规则：已有同标题任务时默认跳过，防止快速重复点击或重复执行产生重复任务。

## 5. 前端链路

| 文件 | 职责 |
|------|------|
| `src/api/modules/projectTenderBreakdown.js` | 项目级招标文件解析和 readiness API |
| `src/api/modules/projects.js` | 项目任务拆解、任务状态、项目详情 API |
| `src/views/Project/detail/composables/useProjectDetailTaskActions.js` | 任务看板按钮动作，负责调用解析结果生成任务并刷新看板 |
| `src/views/Project/detail/components/ProjectDetailMainColumn.vue` | 项目详情主列，承载任务看板、解析入口和评分入口 |

前端交互口径：

1. 点击“解析招标文件”前先检查 readiness。
2. DeepSeek key 未配置时，展示可理解的配置提示，不发起无效上传。
3. 解析中禁用上传入口，防止重复上传和重复解析。
4. 点击“拆解任务”只调用真实任务拆解接口，不打开评分弹窗。
5. 无来源业务错误直接展示后端友好文案。

## 6. 验证覆盖

| 风险 | 覆盖方式 |
|------|----------|
| 快速重复点击重复创建任务 | 后端并发锁 + 同标题去重测试 |
| 保存中途失败留下半成品 | 事务测试 |
| 最新 snapshot 有/无需求项来源优先级 | Source Reader / Service 测试 |
| 旧详情 composable 误开评分弹窗 | 前端 composable 回归测试 |
| 无来源错误变成通用错误 | 前端错误展示测试 |
| 状态变更事件链路断开 | MainColumn/MainContent 回归测试 |
| 解析招标文件到任务看板 | E2E fixture 链路测试 |

## 7. 维护边界

- 保持 API 路径稳定：`/api/projects/{projectId}/tender-breakdown` 与 `/api/projects/{projectId}/tasks/decompose`。
- 内部可逐步把历史 `biddraftagent` 解析复用点迁到更中性的模块，但不应在同一 PR 混入用户可见行为。
- 新增规则时优先扩展 `TaskBreakdownPolicy` 的输入/输出 record 和单测，不把规则写进 Controller 或 Repository。
