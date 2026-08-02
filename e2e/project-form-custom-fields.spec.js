/**
 * E2E 测试：CO-601 项目三表单自定义字段扩展
 *
 * 覆盖范围：
 * - US1: 自定义字段落库与回显主链路（basic/initiation/detail 三 scope）
 * - US2: 系统预置字段防误改保护（key/type 锁定、删除按钮隐藏）
 * - US3: 自定义字段全生命周期管理（编辑/删除/类型变更）
 *
 * 依赖：e2e/auth-helpers.js（ensureApiSession / injectSession）
 * 依赖：后端运行在 http://127.0.0.1:18089
 * 依赖：前端运行在 http://127.0.0.1:1323
 *
 * 修复说明（2026-08-02）：
 * - 数据隔离：每个测试用例用 Date.now() 唯一 key，避免 schema 累积污染
 * - 角色权限：用 /bidAdmin（持有 system.admin 权限 + ROLE_ADMIN），不用 bid-Team（403）
 * - 路由适配：/project/create 已重定向到 /project，改用 API 创建项目 + API 验证回显
 * - tenderId 必填：ProjectRequest.tenderId @NotNull，测试数据补 tenderId=99999
 * - 表单定义复用：e2e profile 启动时 FormDefinitionE2eSeeder 预置了 project.basic
 *   测试通过 update 添加自定义字段（不 createFormDefinition，避免 Scope already exists 冲突）
 *   project.initiation 在 beforeAll 检查并按需创建（首次创建后复用，afterAll 不删除）
 *
 * 注意：e2e profile 使用 H2 内存数据库 + Flyway 禁用，每次重启后端数据库重置。
 * 同一次后端启动内多次跑测试，scope 已存在的表单定义会被复用（不重复创建）。
 */

import { test, expect } from '@playwright/test'
import { apiBaseUrl, ensureApiSession } from './auth-helpers.js'

// ==================== Helpers ====================

/**
 * 创建 admin 会话（持有 system.admin 权限 + ROLE_ADMIN）
 */
async function createAdminSession(testId) {
  return ensureApiSession({
    username: `co601_admin_${testId}_${Date.now()}`,
    role: '/bidAdmin',
    fullName: `CO-601 测试管理员 ${testId}`,
  })
}

/**
 * 通过管理 API 获取表单定义（含 id，用于后续 update/publish）
 * 返回 { id, scope, scopeLabel, fields, schemaJson } 或 null
 *
 * 注意：运行时 /api/form-definitions/{scope}/active 返回的 ResolvedForm 不含 id，
 * 无法直接用于 update。改用管理 API 分页查询 + 客户端 scope 过滤拿 id。
 */
async function getFormDefinition(session, scope) {
  const response = await fetch(`${apiBaseUrl}/api/admin/form-definitions?page=0&size=100`, {
    headers: { 'Authorization': `Bearer ${session.token}` }
  })
  if (!response.ok) return null
  const result = await response.json()
  const definitions = result.data?.content || []
  const found = definitions.find(d => d.scope === scope)
  if (!found) return null

  // 解析 schemaJson 获取 fields
  // H2 JSON 列存在双重编码问题（schemaJson 是 JSON 字符串的 JSON 字符串），
  // 需循环解析直到得到对象。详见 project_memory H2 JSON 列双重编码 lessons。
  let schema = found.schemaJson
  while (typeof schema === 'string') {
    try {
      schema = JSON.parse(schema)
    } catch {
      schema = {}
      break
    }
  }
  const fields = (schema && schema.fields) || []

  return {
    id: found.id,
    scope: found.scope,
    scopeLabel: found.scopeLabel,
    fields,
    schemaJson: found.schemaJson,
  }
}

/**
 * 通过 API 创建表单定义（POST）
 * 仅在 scope 不存在时调用（调用方需先 getFormDefinition 检查）
 */
async function createFormDefinition(session, scope, scopeLabel, fields) {
  const response = await fetch(`${apiBaseUrl}/api/admin/form-definitions`, {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${session.token}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({ scope, scopeLabel, schema: { fields } })
  })
  if (!response.ok) {
    const error = await response.json().catch(() => ({}))
    throw new Error(`创建表单定义失败: ${response.status} ${JSON.stringify(error)}`)
  }
  const result = await response.json()
  return result.data.id
}

/**
 * 通过 API 更新表单定义 schema（PUT）
 * 注意：update 整体覆盖 schema，调用方需提交完整 fields 列表
 */
async function updateFormDefinition(session, definitionId, scopeLabel, fields) {
  const response = await fetch(`${apiBaseUrl}/api/admin/form-definitions/${definitionId}`, {
    method: 'PUT',
    headers: {
      'Authorization': `Bearer ${session.token}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      scopeLabel,
      schema: { fields },
      enabled: true
    })
  })
  if (!response.ok) {
    const error = await response.json().catch(() => ({}))
    throw new Error(`更新表单定义失败: ${response.status} ${JSON.stringify(error)}`)
  }
  return await response.json()
}

/**
 * 通过 API 发布表单定义
 */
async function publishFormDefinition(session, definitionId) {
  const response = await fetch(`${apiBaseUrl}/api/admin/form-definitions/${definitionId}/publish`, {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${session.token}`,
      'Content-Type': 'application/json'
    }
  })
  if (!response.ok) {
    const error = await response.json().catch(() => ({}))
    throw new Error(`发布表单定义失败: ${response.status} ${JSON.stringify(error)}`)
  }
  return await response.json()
}

/**
 * 确保表单定义存在（已存在则复用，不存在则创建并发布）
 * 解决 e2e profile 多次跑测试的 scope 残留问题：
 * - seeder 预置的 scope（project.basic 等）直接复用
 * - 未预置的 scope（project.initiation）首次创建，后续复用
 */
async function ensureFormDefinition(session, scope, scopeLabel, defaultFields) {
  const existing = await getFormDefinition(session, scope)
  if (existing) {
    return existing
  }
  const id = await createFormDefinition(session, scope, scopeLabel, defaultFields)
  await publishFormDefinition(session, id)
  // 重新查询返回完整信息
  return await getFormDefinition(session, scope)
}

/**
 * 通过 API 创建项目（避免前端路由 /project/create 已重定向的问题）
 *
 * tenderId 必填（@NotNull），用 Date.now() 截断后 9 位整数动态生成。
 * 不可写死 99999：ProjectService.createProject 内 ExistingTenderProjectSelector
 * 会按 tenderId 查找已有项目，若同一次后端启动内多次跑测试，tenderId=99999
 * 第一次创建后，第二次会复用旧项目并直接 return，新 customFields 不保存。
 * 详见 backend/src/main/java/com/xiyu/bid/project/service/ProjectService.java:69-71。
 */
async function createProjectViaApi(session, projectData) {
  const dynamicTenderId = Number(Date.now().toString().slice(-9))
  const response = await fetch(`${apiBaseUrl}/api/projects`, {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${session.token}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      // 必填字段（tenderId 调用方可覆盖，默认动态生成避免复用）
      tenderId: dynamicTenderId,
      status: 'PENDING_INITIATION',
      managerId: 1,
      teamMembers: [1],
      startDate: '2026-08-01T00:00:00',
      endDate: '2026-12-31T00:00:00',
      // 合并测试特定数据（如 name、customFields）
      ...projectData,
    })
  })
  if (!response.ok) {
    const error = await response.json().catch(() => ({}))
    throw new Error(`创建项目失败: ${response.status} ${JSON.stringify(error)}`)
  }
  const result = await response.json()
  return result.data.id
}

/**
 * 通过 API 删除项目（测试清理）
 */
async function deleteProjectViaApi(session, projectId) {
  await fetch(`${apiBaseUrl}/api/projects/${projectId}`, {
    method: 'DELETE',
    headers: { 'Authorization': `Bearer ${session.token}` }
  })
}

/**
 * 在现有 fields 中追加自定义字段（去重，避免累积污染）
 * 返回新数组，不修改原数组
 */
function appendCustomField(existingFields, newField) {
  const filtered = (existingFields || []).filter(f => f.key !== newField.key)
  return [...filtered, {
    key: newField.key,
    label: newField.label,
    type: newField.type || 'text',
    required: newField.required || false,
    enabled: newField.enabled !== false,
    ...(newField.options ? { options: newField.options } : {}),
    ...(newField.optionsText ? { optionsText: newField.optionsText } : {})
  }]
}

/**
 * 生成 project.basic 的最小 schema（仅含 name 一个预置字段）
 * 用于首次创建 project.basic 表单定义（seeder 已预置时不会被调用）
 */
function buildMinimalBasicSchema() {
  return [
    { key: 'name', label: '项目名称', type: 'text', required: true, enabled: true }
  ]
}

/**
 * 生成 project.initiation 的最小 schema（空数组）
 * project.initiation 是 hybrid scope，预置字段由业务页 fallback 渲染，
 * schema 不应含预置 key（projectName 等会被 CustomFieldsSchemaPolicy 阻断）。
 * 测试通过 appendCustomField 动态添加自定义字段。
 */
function buildMinimalInitiationSchema() {
  return []
}

// ==================== US1: 自定义字段落库与回显主链路 ====================

test.describe('US1: 自定义字段落库与回显主链路', () => {
  let adminSession
  let createdProjectIds = []

  test.beforeAll(async () => {
    adminSession = await createAdminSession('us1')
    // 确保 project.basic 和 project.initiation 表单定义存在（seeder 预置或首次创建）
    await ensureFormDefinition(adminSession, 'project.basic', '项目基本信息', buildMinimalBasicSchema())
    await ensureFormDefinition(adminSession, 'project.initiation', '立项信息', buildMinimalInitiationSchema())
  })

  test.afterAll(async () => {
    // 仅清理项目，表单定义保留（避免 scope 残留影响下次跑测试）
    for (const id of createdProjectIds) await deleteProjectViaApi(adminSession, id)
  })

  test('project.basic 自定义文本字段：API 创建项目提交 → 落库 → 回显一致', async () => {
    const customFieldKey = `us1_basic_text_${Date.now()}`
    const customFieldLabel = '自定义文本字段'
    const customFieldValue = '这是自定义文本内容'

    // 1. 在 project.basic 表单定义中添加自定义字段（update 整体覆盖）
    const currentDef = await getFormDefinition(adminSession, 'project.basic')
    const fieldsWithCustom = appendCustomField(currentDef.fields, {
      key: customFieldKey,
      label: customFieldLabel,
      type: 'text',
      required: false
    })
    await updateFormDefinition(adminSession, currentDef.id, currentDef.scopeLabel, fieldsWithCustom)
    await publishFormDefinition(adminSession, currentDef.id)

    // 2. 创建项目并提交自定义字段值
    const projectId = await createProjectViaApi(adminSession, {
      name: `CO-601 US1 basic 测试 ${Date.now()}`,
      customFields: { 'project.basic': { [customFieldKey]: customFieldValue } }
    })
    createdProjectIds.push(projectId)

    // 3. 验证数据落库
    const projectResponse = await fetch(`${apiBaseUrl}/api/projects/${projectId}`, {
      headers: { 'Authorization': `Bearer ${adminSession.token}` }
    })
    expect(projectResponse.ok).toBeTruthy()
    const projectData = await projectResponse.json()
    expect(projectData.data.customFields).toBeDefined()
    expect(projectData.data.customFields['project.basic']).toBeDefined()
    expect(projectData.data.customFields['project.basic'][customFieldKey]).toBe(customFieldValue)
  })

  test('project.initiation 自定义下拉字段：API 提交立项数据 → 落库 → 回显一致', async () => {
    const customFieldKey = `us1_init_select_${Date.now()}`
    const customFieldLabel = '自定义下拉字段'
    const customFieldValue = 'option1'

    // 1. 在 project.initiation 表单定义中添加自定义字段
    const currentDef = await getFormDefinition(adminSession, 'project.initiation')
    const fieldsWithCustom = appendCustomField(currentDef.fields, {
      key: customFieldKey,
      label: customFieldLabel,
      type: 'select',
      optionsText: '选项1=option1\n选项2=option2\n选项3=option3'
    })
    await updateFormDefinition(adminSession, currentDef.id, currentDef.scopeLabel, fieldsWithCustom)
    await publishFormDefinition(adminSession, currentDef.id)

    // 2. 创建项目
    const projectId = await createProjectViaApi(adminSession, {
      name: `CO-601 US1 initiation 测试 ${Date.now()}`,
    })
    createdProjectIds.push(projectId)

    // 3. 创建招标文件记录（CO-455: 立项提交前必须已上传招标文件）
    //    用 JSON POST 创建 ProjectDocument 记录（只需 name 必填），比 multipart 上传更简单。
    //    CO-455 校验 tenderDocumentId 存在 + 属于当前项目，不校验文件内容。
    const docResponse = await fetch(`${apiBaseUrl}/api/projects/${projectId}/documents`, {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${adminSession.token}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        name: '测试招标文件.txt',
        documentCategory: 'TENDER',
        fileType: 'txt'
      })
    })
    if (!docResponse.ok) {
      const errorBody = await docResponse.json().catch(() => ({ raw: '<no body>' }))
      throw new Error(`创建招标文件记录失败: ${docResponse.status} ${JSON.stringify(errorBody)}`)
    }
    const docData = await docResponse.json()
    const tenderDocumentId = docData.data?.id
    if (!tenderDocumentId) {
      throw new Error(`创建招标文件记录成功但未返回 id: ${JSON.stringify(docData)}`)
    }

    // 4. 通过 API 提交立项数据（含自定义字段）
    //    ProjectInitiationController 只有 POST（submit 创建）和 PATCH（update 更新），
    //    测试场景是创建项目后首次提交立项，用 POST。
    //    InitiationFieldPolicy.validate 要求 ownerUnit/bidOpenTime/ownerUserId/departmentSnapshot 必填。
    //    CO-455 要求 tenderDocumentId 必填（招标文件必传校验）。
    const initiationResponse = await fetch(`${apiBaseUrl}/api/projects/${projectId}/initiation`, {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${adminSession.token}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        ownerUnit: '测试业主单位',
        bidOpenTime: '2026-12-31T10:00:00',
        ownerUserId: 1,
        departmentSnapshot: '测试部门',
        tenderDocumentId,
        customFields: { 'project.initiation': { [customFieldKey]: customFieldValue } }
      })
    })
    if (!initiationResponse.ok) {
      const errorBody = await initiationResponse.json().catch(() => ({ raw: '<no body>' }))
      throw new Error(`initiation POST 失败: ${initiationResponse.status} ${JSON.stringify(errorBody)}`)
    }

    // 4. 验证数据落库
    const initFetchResponse = await fetch(`${apiBaseUrl}/api/projects/${projectId}/initiation`, {
      headers: { 'Authorization': `Bearer ${adminSession.token}` }
    })
    expect(initFetchResponse.ok).toBeTruthy()
    const initData = await initFetchResponse.json()
    expect(initData.data.customFields).toBeDefined()
    expect(initData.data.customFields['project.initiation']).toBeDefined()
    expect(initData.data.customFields['project.initiation'][customFieldKey]).toBe(customFieldValue)
  })

  test('老项目（无 custom_fields）打开不报错', async () => {
    // 1. 创建项目（不传 customFields）
    const projectId = await createProjectViaApi(adminSession, {
      name: `CO-601 US1 老项目测试 ${Date.now()}`,
    })
    createdProjectIds.push(projectId)

    // 2. 验证 API 返回 customFields 为空对象（降级空 Map）
    const projectResponse = await fetch(`${apiBaseUrl}/api/projects/${projectId}`, {
      headers: { 'Authorization': `Bearer ${adminSession.token}` }
    })
    expect(projectResponse.ok).toBeTruthy()
    const projectData = await projectResponse.json()
    expect(projectData.data.customFields).toBeDefined()
    // 老项目 customFields 为空对象（无 key）
    expect(Object.keys(projectData.data.customFields).length).toBe(0)
  })
})

// ==================== US2: 系统预置字段防误改保护 ====================

test.describe('US2: 系统预置字段防误改保护', () => {
  let adminSession

  test.beforeAll(async () => {
    adminSession = await createAdminSession('us2')
    await ensureFormDefinition(adminSession, 'project.basic', '项目基本信息', buildMinimalBasicSchema())
    await ensureFormDefinition(adminSession, 'project.initiation', '立项信息', buildMinimalInitiationSchema())
  })

  test('后端 schema 校验：project.initiation 预置 key 被阻断（hybrid scope）', async () => {
    // 1. 获取 project.initiation 当前表单定义
    const currentDef = await getFormDefinition(adminSession, 'project.initiation')
    expect(currentDef).not.toBeNull()

    // 2. 尝试 PUT 添加预置 key（projectName 是 project.initiation 的预置 key）
    //    注意：project.initiation 是 hybrid scope，预置 key 会被阻断
    const conflictingFields = appendCustomField(currentDef.fields, {
      key: 'projectName',
      label: '冲突字段',
      type: 'text',
      required: false
    })

    const response = await fetch(`${apiBaseUrl}/api/admin/form-definitions/${currentDef.id}`, {
      method: 'PUT',
      headers: {
        'Authorization': `Bearer ${adminSession.token}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        scopeLabel: currentDef.scopeLabel,
        schema: { fields: conflictingFields },
        enabled: true
      })
    })

    // 3. 预期被阻断（hybrid scope 预置 key 命中）
    expect(response.ok).toBeFalsy()
    const error = await response.json().catch(() => ({}))
    expect(JSON.stringify(error)).toContain('预置清单')
  })

  test('后端 schema 校验：project.basic 重复 key 被阻断', async () => {
    // 1. 获取 project.basic 当前表单定义
    const currentDef = await getFormDefinition(adminSession, 'project.basic')
    expect(currentDef).not.toBeNull()
    // 找一个已存在的 key 来测试重复（seeder 预置了 name 字段）
    const existingKey = currentDef.fields[0]?.key
    expect(existingKey).toBeDefined()

    // 2. 直接构造有两个相同 key 的 fields 列表（appendCustomField 会去重，
    //    无法触发 CustomFieldsSchemaPolicy 的 Set 去重检测）
    const duplicatedFields = [
      ...currentDef.fields,
      { key: existingKey, label: '重复字段', type: 'text', required: false, enabled: true }
    ]

    const response = await fetch(`${apiBaseUrl}/api/admin/form-definitions/${currentDef.id}`, {
      method: 'PUT',
      headers: {
        'Authorization': `Bearer ${adminSession.token}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        scopeLabel: currentDef.scopeLabel,
        schema: { fields: duplicatedFields },
        enabled: true
      })
    })

    // 3. 预期被阻断（重复 key）
    expect(response.ok).toBeFalsy()
    const error = await response.json().catch(() => ({}))
    expect(JSON.stringify(error)).toContain('重复')
  })

  test('后端 schema 校验：project.basic 预置 key 不阻断（非 hybrid scope）', async () => {
    // 1. 获取 project.basic 当前表单定义
    const currentDef = await getFormDefinition(adminSession, 'project.basic')
    expect(currentDef).not.toBeNull()

    // 2. 找一个预置 key 但当前 schema 不存在的（如 customer）
    //    project.basic 预置 key 集合：name, customer, budget, industry, region, platform,
    //    deadline, manager, competitors
    //    seeder 预置了：name, managerId, teamMembers, startDate, endDate, budget, industry, description
    //    所以 customer/region/platform/deadline/manager/competitors 都不在当前 schema
    const presetKeyToAdd = 'customer'
    const existingKeys = new Set(currentDef.fields.map(f => f.key))
    expect(existingKeys.has(presetKeyToAdd)).toBe(false)

    // 3. PUT 添加预置 key（customer 是 project.basic 的预置 key，但非 hybrid scope 不阻断预置 key）
    const fieldsWithPreset = appendCustomField(currentDef.fields, {
      key: presetKeyToAdd,
      label: '客户（预置）',
      type: 'text',
      required: false
    })

    const response = await fetch(`${apiBaseUrl}/api/admin/form-definitions/${currentDef.id}`, {
      method: 'PUT',
      headers: {
        'Authorization': `Bearer ${adminSession.token}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        scopeLabel: currentDef.scopeLabel,
        schema: { fields: fieldsWithPreset },
        enabled: true
      })
    })

    // 4. 预期成功（非 hybrid scope 不阻断预置 key）
    expect(response.ok).toBeTruthy()
  })
})

// ==================== US3: 自定义字段全生命周期管理 ====================

test.describe('US3: 自定义字段全生命周期管理', () => {
  let adminSession
  let createdProjectIds = []

  test.beforeAll(async () => {
    adminSession = await createAdminSession('us3')
    await ensureFormDefinition(adminSession, 'project.basic', '项目基本信息', buildMinimalBasicSchema())
  })

  test.afterAll(async () => {
    for (const id of createdProjectIds) await deleteProjectViaApi(adminSession, id)
  })

  test('编辑字段 label 重发布 → API 验证旧值仍在', async () => {
    const customFieldKey = `us3_edit_${Date.now()}`
    const originalLabel = '原始标签'
    const updatedLabel = '更新后的标签'
    const customFieldValue = '这是自定义字段值'

    // 1. 在 project.basic 添加自定义字段
    const currentDef = await getFormDefinition(adminSession, 'project.basic')
    const fieldsWithCustom = appendCustomField(currentDef.fields, {
      key: customFieldKey,
      label: originalLabel,
      type: 'text',
      required: false
    })
    await updateFormDefinition(adminSession, currentDef.id, currentDef.scopeLabel, fieldsWithCustom)
    await publishFormDefinition(adminSession, currentDef.id)

    // 2. 创建项目并提交自定义字段值
    const projectId = await createProjectViaApi(adminSession, {
      name: `CO-601 US3 编辑测试 ${Date.now()}`,
      customFields: { 'project.basic': { [customFieldKey]: customFieldValue } }
    })
    createdProjectIds.push(projectId)

    // 3. 编辑字段 label 并重发布
    const updatedDef = await getFormDefinition(adminSession, 'project.basic')
    const editedFields = updatedDef.fields.map(f => {
      if (f.key === customFieldKey) return { ...f, label: updatedLabel }
      return f
    })
    await updateFormDefinition(adminSession, updatedDef.id, updatedDef.scopeLabel, editedFields)
    await publishFormDefinition(adminSession, updatedDef.id)

    // 4. 验证旧值仍在
    const projectResponse = await fetch(`${apiBaseUrl}/api/projects/${projectId}`, {
      headers: { 'Authorization': `Bearer ${adminSession.token}` }
    })
    const projectData = await projectResponse.json()
    expect(projectData.data.customFields['project.basic'][customFieldKey]).toBe(customFieldValue)
  })

  test('删除字段后历史值仍保留在 DB', async () => {
    const customFieldKey = `us3_delete_${Date.now()}`
    const customFieldLabel = '待删除字段'
    const customFieldValue = '这个值应该被保留'

    // 1. 在 project.basic 添加自定义字段
    const currentDef = await getFormDefinition(adminSession, 'project.basic')
    const fieldsWithCustom = appendCustomField(currentDef.fields, {
      key: customFieldKey,
      label: customFieldLabel,
      type: 'text',
      required: false
    })
    await updateFormDefinition(adminSession, currentDef.id, currentDef.scopeLabel, fieldsWithCustom)
    await publishFormDefinition(adminSession, currentDef.id)

    // 2. 创建项目并提交自定义字段值
    const projectId = await createProjectViaApi(adminSession, {
      name: `CO-601 US3 删除测试 ${Date.now()}`,
      customFields: { 'project.basic': { [customFieldKey]: customFieldValue } }
    })
    createdProjectIds.push(projectId)

    // 3. 删除字段并重发布
    const updatedDef = await getFormDefinition(adminSession, 'project.basic')
    const fieldsAfterDelete = updatedDef.fields.filter(f => f.key !== customFieldKey)
    await updateFormDefinition(adminSession, updatedDef.id, updatedDef.scopeLabel, fieldsAfterDelete)
    await publishFormDefinition(adminSession, updatedDef.id)

    // 4. 验证历史值保留在 DB（API 查询仍返回该字段值）
    const projectResponse = await fetch(`${apiBaseUrl}/api/projects/${projectId}`, {
      headers: { 'Authorization': `Bearer ${adminSession.token}` }
    })
    const projectData = await projectResponse.json()
    expect(projectData.data.customFields['project.basic'][customFieldKey]).toBe(customFieldValue)
  })

  test('类型变更后历史值不报错（文本改下拉）', async () => {
    const customFieldKey = `us3_type_${Date.now()}`
    const customFieldLabel = '类型变更字段'
    const customFieldValue = '这是文本值'

    // 1. 在 project.basic 添加文本类型自定义字段
    const currentDef = await getFormDefinition(adminSession, 'project.basic')
    const fieldsWithCustom = appendCustomField(currentDef.fields, {
      key: customFieldKey,
      label: customFieldLabel,
      type: 'text',
      required: false
    })
    await updateFormDefinition(adminSession, currentDef.id, currentDef.scopeLabel, fieldsWithCustom)
    await publishFormDefinition(adminSession, currentDef.id)

    // 2. 创建项目并提交文本值
    const projectId = await createProjectViaApi(adminSession, {
      name: `CO-601 US3 类型变更测试 ${Date.now()}`,
      customFields: { 'project.basic': { [customFieldKey]: customFieldValue } }
    })
    createdProjectIds.push(projectId)

    // 3. 将字段类型从文本改为下拉并重发布
    const updatedDef = await getFormDefinition(adminSession, 'project.basic')
    const fieldsAfterChange = updatedDef.fields.map(f => {
      if (f.key === customFieldKey) {
        return { ...f, type: 'select', optionsText: '选项1=option1\n选项2=option2' }
      }
      return f
    })
    await updateFormDefinition(adminSession, updatedDef.id, updatedDef.scopeLabel, fieldsAfterChange)
    await publishFormDefinition(adminSession, updatedDef.id)

    // 4. 验证历史值仍保留（API 查询不报错）
    const projectResponse = await fetch(`${apiBaseUrl}/api/projects/${projectId}`, {
      headers: { 'Authorization': `Bearer ${adminSession.token}` }
    })
    const projectData = await projectResponse.json()
    expect(projectData.data.customFields['project.basic'][customFieldKey]).toBe(customFieldValue)
  })
})
