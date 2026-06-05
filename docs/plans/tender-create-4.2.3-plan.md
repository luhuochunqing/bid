# §4.2.3 标讯创建 — 实现计划

> 来源：飞书蓝图 block-id `YKm1di8B0oHSlVxQEJOcZb42nnf`
> 仓库：cursor-revert-pr-429-tender-entry（PR #434 回退后基线）
> 生成时间：2026-05-26

---

## 1. 蓝图要求 vs 当前状态 差距分析

| # | 蓝图要求 | 当前状态 | 差距 | 优先级 |
|---|---|---|---|---|
| 1 | **创建入口**：人工录入 → 打开标讯详情页（标题"新建标讯"，无状态标签） | 人工录入 → 打开 `ManualTenderDialog` 弹窗 | 根本性差异：蓝图要求打开 DetailPage 的 create 模式，而非 Dialog | P0 |
| 2 | **基本信息 Tab**：标题为"基本信息"，字段见下方"表单字段差距表" | 当前 ManualTenderDialog 有 fallback 硬编码表单，字段不一致 | 表单字段需全部按蓝图重构，DetailPage 需在 create 模式下渲染可编辑表单 | P0 |
| 3 | **联系人字段**：联系人1/2 各4个字段，联系人1必填但联系人2可选，但不能两行同时为空 | ManualTenderDialog fallback 表单只有"联系人""联系方式"两个字段 | 联系人字段缺失，需新增9个字段 | P0 |
| 4 | **必填校验**：`报名截止`>当前时间，`开标时间`>`报名截止` | 当前无时间校验逻辑 | 需要新增两个时间校验规则 | P0 |
| 5 | **保存流程**：保存基本信息 → 展示【下一步】按钮 → 点击进入评估表 Tab | 当前 ManualTenderDialog 保存入库一步完成（跳过评估表） | 需改为两步：先保存基本信息，再引导到评估表 | P0 |
| 6 | **创建后状态**：保存后详情页标题变"编辑标讯"，状态显示【待分配】 | 创建后 Dialog 关闭，列表刷新，无状态标签 | 创建后需导航到新标讯的 DetailPage（编辑模式） | P0 |
| 7 | **去重字段**：三字段（招标主体+报名截止时间+开标时间）完全匹配 | 当前 4-field（+标题）完全匹配 | 后端去重策略需改为 3-field | P0 |
| 8 | **重复弹窗**：⚠️检测到重复标讯弹窗，两分区+两按钮（取消/通知管理员复核） | 当前去重直接 throw BusinessException，无前端弹窗 | 需新增 DuplicateWarningDialog 组件 | P0 |
| 9 | **来源平台**：默认"人工录入"，下拉非必填 | ManualTenderDialog 无此字段 | 需新增字段 | P1 |
| 10 | **商机id**：系统自动匹配，表单不展示 | 无此逻辑 | 后端自动匹配，前端无需改动 | P1 |
| 11 | **标讯文件上传**：PDF/Word，≤50MB，非必填 | ManualTenderDialog 无文件上传字段 | 需新增文件上传组件 | P1 |
| 12 | **AI 解析权限**：投标管理员、投标组长、项目负责人、投标专员均可用 | 当前 AI 解析区域始终显示，不按权限控制 | 需新增权限控制 | P2 |
| 13 | **BottomActionBar**：创建模式下显示【保存】按钮（必填未完成置灰） | BottomActionBar 已有，但按钮与蓝图不一致 | 需更新 BottomActionBar 在 create 模式的按钮 | P1 |

---

### 1.1 表单字段差距详细表

| 字段名 | 蓝图必/非必 | ManualTenderDialog 当前字段 | 是否存在差距 |
|---|---|---|---|
| 标讯标题 | 必填，≤200字符 | 标题 | ✅ 存在（无字符限制） |
| 招标主体 | 必填 | 招标机构 | ✅ 存在（字段名不一致） |
| 总部所在地 | 必填，下拉34省 | 总部所在地 | ✅ 存在（无省下拉） |
| 报名截止时间 | 必填，>当前时间 | 报名截止时间 | ✅ 存在（无时间校验） |
| 开标时间 | 必填，>报名截止 | 开标时间 | ✅ 存在（无时间校验） |
| 联系人1 | 非必填，但不能同时为空 | 联系人（无联系人1/2区分） | ✅ 缺失联系人2及9个联系人字段 |
| 联系人1手机号 | 非必填 | 无 | ✅ 缺失 |
| 联系人1座机 | 非必填 | 无 | ✅ 缺失 |
| 联系人1邮箱 | 非必填 | 无 | ✅ 缺失 |
| 联系人2 | 非必填，但不能同时为空 | 无 | ✅ 缺失 |
| 联系人2手机号 | 非必填 | 无 | ✅ 缺失 |
| 联系人2座机 | 非必填 | 无 | ✅ 缺失 |
| 联系人2邮箱 | 非必填 | 无 | ✅ 缺失 |
| 客户类型 | 必填，下拉 | 客户类型 | ✅ 存在（无完整选项） |
| 优先级 | 必填，下拉 S/A/B/C | 优先级 | ✅ 存在（无完整选项） |
| 项目类型 | 非必填，下拉 | 无 | ✅ 缺失 |
| 来源平台 | 非必填，默认"人工录入" | 无 | ✅ 缺失 |
| 标讯描述 | 非必填，≤5000字符 | 项目描述 | ✅ 存在（无字符限制） |
| 标讯信息 | 非必填，≤5000字符 | 无 | ✅ 缺失 |
| 标讯文件 | 非必填，PDF/Word，≤50MB | 无 | ✅ 缺失 |
| 商机id | 系统自动匹配，表单不展示 | 无 | ✅ 前端无感知即可 |

---

## 2. 架构设计

### 2.1 前端架构

```
人工录入按钮
  ↓ route change
DetailPage.vue (mode=create)
  ├── Tab 1: 基本信息（可编辑表单）  ← 替代 ManualTenderDialog
  │    ├── BasicInfoForm.vue (new) — 表单字段组件
  │    ├── ContactFields.vue (new) — 联系人1/2 区块
  │    ├── TenderFileUpload.vue (new) — 标讯文件上传
  │    └── AIDocumentParser.vue (复用现有) — AI解析区域
  ├── Tab 2: 项目评估表（空白待填写）  ← 创建后可见
  └── BottomActionBar.vue
       └── create 模式按钮: [保存] [下一步→] [取消]
```

**新文件：**
- `src/views/Bidding/detail/BasicInfoForm.vue` — 基本信息可编辑表单
- `src/views/Bidding/detail/ContactFields.vue` — 联系人1/2字段组件
- `src/views/Bidding/detail/TenderFileUpload.vue` — 标讯文件上传
- `src/views/Bidding/detail/CreateTenderPage.vue` (可选路由层) — 创建模式包装
- `src/views/Bidding/detail/DuplicateWarningDialog.vue` — ⚠️重复标讯警告弹窗

**修改文件：**
- `src/views/Bidding/detail/DetailPage.vue` — 新增 create 模式渲染分支
- `src/views/Bidding/detail/BottomActionBar.vue` — 新增 create 模式按钮
- `src/views/Bidding/detail/actionMatrix.js` — 新增 create 模式 action 配置
- `src/views/Bidding/list/BiddingPageHeader.vue` — 人工录入改为路由跳转
- `src/views/Bidding/list/components/ManualTenderDialog.vue` — 标记为 DEPRECATED 或删除
- `src/views/Bidding/List.vue` — 移除 ManualTenderDialog 引用

### 2.2 后端架构（FP-Java Profile）

```
Pure Core（无副作用，可单测）:
  TenderDeduplicationPolicy
    isDuplicate(purchaser, regDeadline, bidOpenTime,
                purchaser2, regDeadline2, bidOpenTime2)  ← 3-field
  TenderCreateValidator
    validateFields(TenderDTO) → ValidationResult  ← 必填/格式/时间关系校验
  TenderTimeRangePolicy
    isValidTimeRange(regDeadline, bidOpenTime, now) → boolean

Application Service（事务边界）:
  TenderCommandService
    createTender(TenderDTO) → TenderDTO
      1. validateFields()  ← Pure Core 校验
      2. withCommandDefaults()
      3. deduplicationService.checkDuplicate3Field()  ← 3-field 查询
      4. repository.save()
      5. tryAutoAssign()

Infrastructure:
  TenderDeduplicationService
    checkDuplicate3Field(Tender) → void（throws BusinessException）
      findByPurchaserNameAndRegDeadlineAndBidOpenTime()  ← 3-field 查询
  TenderRepository
    findByPurchaserNameAndRegDeadlineAndBidOpenTime()  ← 新增 JPA 方法
```

---

## 3. 任务拆分

### Wave 1：后端去重策略（无前端依赖，可先行）

#### 任务 1.1：后端去重策略改为 3-field

**涉及文件：**
- `backend/src/main/java/com/xiyu/bid/tender/core/TenderDeduplicationPolicy.java`
- `backend/src/main/java/com/xiyu/bid/tender/service/TenderDeduplicationService.java`
- `backend/src/main/java/com/xiyu/bid/repository/TenderRepository.java`

**改动描述：**
1. `TenderDeduplicationPolicy.isDuplicate` 参数从 8 个（4×2）改为 6 个（3×2）：移除 `title1/title2`
2. `TenderDeduplicationService.checkDuplicate` → `checkDuplicate3Field`，查询方法改为 `findByPurchaserNameAndRegDeadlineAndBidOpenTime`
3. `TenderRepository` 新增 `findByPurchaserNameAndRegDeadlineAndBidOpenTime` JPA 方法
4. 删除旧的 4-field 查询方法 `findByTitleAndPurchaserNameAllIgnoreCase`

**依赖关系：** 无
**工作量：** 中（3个文件，策略接口变更）

#### 任务 1.2：后端新增时间校验 Pure Core

**涉及文件：**
- `backend/src/main/java/com/xiyu/bid/tender/core/TenderCreateValidator.java` (new)
- `backend/src/main/java/com/xiyu/bid/tender/service/TenderCommandService.java`

**改动描述：**
1. 新建 `TenderCreateValidator`（纯核心）：`validate(TenderDTO, Instant now)` 返回 `ValidationResult`
2. 校验规则：
   - 标讯标题：非空，≤200字符
   - 招标主体：非空
   - 总部所在地：非空
   - 报名截止时间：非空，必须 > now
   - 开标时间：非空，必须 > 报名截止时间
   - 客户类型：非空
   - 优先级：非空（S/A/B/C）
   - 联系人1/2：至少填一行
3. `TenderCommandService.createTender` 调用 `TenderCreateValidator.validate()`
4. 校验失败返回 400 + 具体字段错误

**依赖关系：** 任务 1.1 完成
**工作量：** 中（新建验证器 + 更新 CommandService）

---

### Wave 2：前端核心（依赖 Wave 1 API 不变）

#### 任务 2.1：前端 DuplicateWarningDialog 组件

**涉及文件：**
- `src/views/Bidding/detail/DuplicateWarningDialog.vue` (new)
- `src/views/Bidding/detail/DuplicateWarningDialog.spec.js` (new)

**改动描述：**
1. 组件 props：`duplicateList: Array<{id, title, purchaserName, regDeadline, bidOpenTime, sourceType}>`、`visible: boolean`
2. 弹窗结构：
   - 标题：⚠️ 检测到重复标讯
   - 分区一（虚线框）：「您正在录入」— 显示当前录入标讯的招标主体+截止时间
   - 分区二：系统已存在重复列表（el-table）
     - 列：项目名称、招标主体、报名截止、开标时间、来源平台、操作（查看详情链接）
   - 底部按钮：取消（关闭弹窗，返回新建页）、通知管理员复核（调 API → 发待办+企微通知）
3. 暴露 emit：`close`、`notify-admin`

**依赖关系：** 无（纯 UI 组件）
**工作量：** 小

#### 任务 2.2：前端 BasicInfoForm 表单组件

**涉及文件：**
- `src/views/Bidding/detail/BasicInfoForm.vue` (new)
- `src/views/Bidding/detail/BasicInfoForm.spec.js` (new)

**改动描述：**
1. 表单字段（按蓝图顺序）：
   - 标讯标题（el-input，maxlength=200，show-word-limit）
   - 招标主体（el-input）
   - 总部所在地（el-select，option 来自后端或静态34省列表）
   - 报名截止时间（el-date-picker，type=datetime）
   - 开标时间（el-date-picker，type=datetime）
   - 联系人1区块（姓名/手机号/座机/邮箱，4个 el-input）
   - 联系人2区块（4个 el-input）
   - 客户类型（el-select，选项：政府机关/事业单位/高校/央企/地方国企/民企/港澳台及外企）
   - 优先级（el-select，选项：S/A/B/C）
   - 项目类型（el-select，选项：工业电商/办公/综合/集采/其他，非必填）
   - 来源平台（el-select，选项含"人工录入"，默认"人工录入"）
   - 标讯描述（el-input type=textarea，maxlength=5000）
   - 标讯信息（el-input type=textarea，maxlength=5000）
   - 标讯文件上传（el-upload，accept=".pdf,.doc,.docx"，max=50MB）
2. 校验规则：
   - el-form rules 绑定必填项
   - 联系人1/2：自定义 validator，至少填一行
   - 报名截止 > 当前时间
   - 开标时间 > 报名截止
3. 支持 v-model 绑定 `formData` 对象

**依赖关系：** 任务 1.2 完成（后端字段名对齐）
**工作量：** 大（核心表单）

#### 任务 2.3：DetailPage.vue 新增 create 模式

**涉及文件：**
- `src/views/Bidding/detail/DetailPage.vue`

**改动描述：**
1. 路由 query `?mode=create` → 渲染 create 模式分支
2. create 模式特征：
   - 页面标题：「新建标讯」（非面包屑）
   - 无状态标签
   - 无 AI 评分卡片
   - Tab 区域：只展示【基本信息】Tab（评估表 Tab 隐藏）
   - 基本信息区域渲染 BasicInfoForm.vue（而非只读 el-descriptions）
   - 底部渲染 BottomActionBar（create 模式按钮）
3. 保存逻辑：
   - 点击【保存】→ 校验 BasicInfoForm → 调用 `tendersApi.create(formData)` → 成功后：
     - 调用 `tendersApi.saveEvaluationDraft(id, {})`（空草稿）
     - **关键变更**：导航到 `/bidding/detail/{newId}?mode=edit`（编辑模式，状态 PENDING_ASSIGNMENT）
   - 【下一步】按钮：保存后自动显示，点击跳转评估表 Tab

**依赖关系：** 任务 2.1 + 2.2 完成
**工作量：** 大（核心页面改造）

#### 任务 2.4：BottomActionBar.vue 新增 create 模式按钮

**涉及文件：**
- `src/views/Bidding/detail/BottomActionBar.vue`
- `src/views/Bidding/detail/BottomActionBar.spec.js`
- `src/views/Bidding/detail/actionMatrix.js`

**改动描述：**
1. `actionMatrix.js` 新增 `CREATE_MODE_ACTIONS`：
   ```js
   { key: 'save', label: '保存', type: 'primary' },
   { key: 'nextStep', label: '下一步→', type: 'default', show: false }, // 保存后显示
   { key: 'cancel', label: '取消', type: 'default' }
   ```
2. `BottomActionBar.vue` 新增 `mode` prop：`'view' | 'edit' | 'create'`
3. create 模式渲染 CREATE_MODE_ACTIONS
4. 【保存】按钮：validate 完成后 enabled，否则 disabled（el-button 的 `disabled` prop）

**依赖关系：** 任务 2.2 完成
**工作量：** 小

#### 任务 2.5：BiddingPageHeader.vue 人工录入改为路由跳转

**涉及文件：**
- `src/views/Bidding/list/components/BiddingPageHeader.vue`
- `src/views/Bidding/List.vue`

**改动描述：**
1. BiddingPageHeader：「人工录入」按钮 emit `'open-manual-add'` → 改为 emit `'navigate-create'` 或直接 `$router.push('/bidding/detail?mode=create')`
2. List.vue：移除 `showManualAdd` 状态和 `ManualTenderDialog` 组件引用
3. 可选择保留 ManualTenderDialog 为 DEPRECATED 状态（注释掉但不删除，待后续清理）

**依赖关系：** 任务 2.3 完成
**工作量：** 小

---

### Wave 3：API 补充与集成

#### 任务 3.1：后端新增「通知管理员复核」API

**涉及文件：**
- `backend/src/main/java/com/xiyu/bid/tender/controller/TenderController.java` (or new)
- `backend/src/main/java/com/xiyu/bid/notification/service/NotificationService.java` (new or existing)

**改动描述：**
1. 新增 `POST /api/tenders/pending-review` — 暂不入库，发送待办+企微通知
2. 请求体：`{ purchaser, regDeadline, bidOpenTime, title?, submittedBy }`
3. 发送飞书待办任务给管理员角色用户（可配置）
4. 发送企微机器人消息（包含标讯关键信息）

**依赖关系：** 任务 1.1 完成
**工作量：** 中

#### 任务 3.2：后端「来源平台」字段支持

**涉及文件：**
- `backend/src/main/java/com/xiyu/bid/tender/domain/.../Tender.java` (entity)
- 可能需要 Flyway 迁移（如果 sourceType 字段尚未完整支持"人工录入"）

**改动描述：**
1. 确认 `Tender.sourceType` 枚举支持「人工录入」
2. 创建时默认值设为 MANUAL_ENTRY / MANUAL_SINGLE

**依赖关系：** 任务 1.1 完成
**工作量：** 小

#### 任务 3.3：后端「商机自动匹配」逻辑

**涉及文件：**
- `backend/src/main/java/com/xiyu/bid/tender/service/TenderCommandService.java`

**改动描述：**
1. 在 `createTender` 中，创建后调用商机匹配服务（如果存在商机模块）
2. 如果商机匹配成功，更新 tender 的商机id字段
3. 此逻辑为后端内部实现，前端无感知

**依赖关系：** 任务 1.1 完成
**工作量：** 小（取决于商机模块是否已存在）

---

### Wave 4：测试覆盖

#### 任务 4.1：后端单测

**涉及文件：**
- `backend/src/test/java/com/xiyu/bid/tender/core/TenderDeduplicationPolicyTest.java` (恢复)
- `backend/src/test/java/com/xiyu/bid/tender/service/TenderDeduplicationServiceTest.java` (恢复)
- `backend/src/test/java/com/xiyu/bid/tender/core/TenderCreateValidatorTest.java` (new)

**测试用例：**
- TenderDeduplicationPolicy：3-field 相同=重复，3-field 任一不同=不重复，null 处理
- TenderDeduplicationService：查询+策略组合
- TenderCreateValidator：必填项校验、时间关系校验、联系人至少填一行校验

#### 任务 4.2：前端单测

**涉及文件：**
- `src/views/Bidding/detail/BasicInfoForm.spec.js`
- `src/views/Bidding/detail/DuplicateWarningDialog.spec.js`
- `src/views/Bidding/detail/actionMatrix.spec.js` (更新：新增 create mode actions)

#### 任务 4.3：E2E 测试

**涉及文件：**
- `e2e/tender-create.spec.js` (new)
- 覆盖场景：
  1. 人工录入 → 填写基本信息 → 保存 → 跳转编辑页
  2. AI 解析 → 上传文件 → 自动回填 → 保存
  3. 重复检测 → 弹窗提示 → 通知管理员
  4. 必填项未填 → 保存按钮置灰
  5. 时间校验 → 报名截止<当前时间 → 提示错误

---

## 4. FP-Java Profile 检查

| 任务 | 纯核心 | 应用服务 | 基础设施 | Flyway | 前端测试 |
|---|---|---|---|---|---|
| 1.1 去重策略3-field | ✅ TenderDeduplicationPolicy | - | TenderDeduplicationService, TenderRepository | - | - |
| 1.2 时间校验器 | ✅ TenderCreateValidator | - | - | - | - |
| 1.3 通知API | - | TenderCommandService | NotificationService | - | - |
| 2.1 DuplicateWarningDialog | ✅ actionMatrix | - | - | - | ✅ |
| 2.2 BasicInfoForm | ✅ actionMatrix | - | - | - | ✅ |
| 2.3 DetailPage create模式 | - | useDetailActions | tendersApi | - | ✅ |
| 2.4 BottomActionBar | ✅ actionMatrix | - | - | - | ✅ |
| 3.1 来源平台字段 | - | - | Entity | 可选 | - |
| 4.1 后端单测 | ✅ | - | - | - | - |
| 4.2 前端单测 | - | - | - | - | ✅ |
| 4.3 E2E | - | - | - | - | ✅ |

---

## 5. 详细任务清单（TODO）

> 每个任务格式：`[ ] 任务名 — 验收标准`

### Wave 1：后端基础

```
[ ] 1.1 — TenderDeduplicationPolicy 改为3-field，单元测试通过
[ ] 1.2 — TenderCreateValidator 实现并通过单元测试
[ ] 1.3 — TenderCommandService 调用新校验器，去重服务改为3-field
```

### Wave 2：前端核心

```
[ ] 2.1 — DuplicateWarningDialog 组件渲染正确，两按钮 emit 正确事件
[ ] 2.2 — BasicInfoForm 21个字段全部渲染，校验规则全部生效
[ ] 2.3 — DetailPage create 模式：路由 ?mode=create → 可编辑表单 → 保存成功跳转编辑页
[ ] 2.4 — BottomActionBar create 模式：保存按钮根据校验状态启用/禁用
[ ] 2.5 — 人工录入按钮改为路由跳转，List.vue 移除 ManualTenderDialog
```

### Wave 3：API 集成

```
[ ] 3.1 — POST /api/tenders/pending-review API 实现（通知管理员）
[ ] 3.2 — 来源平台字段默认值正确
```

### Wave 4：测试

```
[ ] 4.1 — 后端单测：TenderDeduplicationPolicyTest + TenderCreateValidatorTest 全绿
[ ] 4.2 — 前端单测：BasicInfoForm.spec.js + DuplicateWarningDialog.spec.js 全绿
[ ] 4.3 — E2E：标讯创建全流程通过
```

---

## 6. 验收标准

1. **创建入口**：点击人工录入 → 打开 DetailPage（标题"新建标讯"，无状态标签）
2. **表单完整**：21个字段全部展示，必填项有 `*` 标记
3. **校验生效**：必填项未填 → 保存按钮 disabled；报名截止≤当前时间 → 报错
4. **保存成功**：填写必填项 → 保存 → 跳转 `/bidding/detail/{id}?mode=edit`，状态显示【待分配】
5. **下一步流程**：保存后显示【下一步→】按钮，点击跳转评估表 Tab
6. **重复检测**：三字段匹配 → DuplicateWarningDialog 弹窗正确显示
7. **通知管理员**：点击"通知管理员复核" → API 调用成功，弹窗关闭
8. **AI 解析**：文件上传 + 粘贴识别 → 字段正确回填
9. **后端测试**：`mvn test -Dtest=TenderDeduplicationPolicyTest,TenderCreateValidatorTest` 全绿
10. **前端构建**：`npm run build` 无错误
11. **前端测试**：`npm run test:unit` 全绿（涉及改动的 spec 文件）
12. **E2E**：`npm run test:e2e` tender-create.spec.js 全绿

---

## 7. 关键注意事项

1. **ManualTenderDialog 保留 vs 删除**：建议 Wave 2 完成后标记为 DEPRECATED（注释掉引用），Wave 5 再物理删除，避免影响其他功能
2. **AI 解析复用**：现有 `ManualTenderDialog` 中的 AI 解析逻辑（`tendersApi.parseTenderIntakeDocument` / `parseTenderIntakeText`）需迁移到 BasicInfoForm 中复用
3. **商机自动匹配**：如果商机模块尚未实现，可先留空接口，后续迭代
4. **来源平台默认值**：后端 `TenderCommandService.withCommandDefaults()` 中 `sourceType` 设为 MANUAL_ENTRY
5. **时间校验的时区处理**：后端校验时间时需统一使用服务器时区（或 UTC），前端 date-picker 传 UTC 时间字符串
