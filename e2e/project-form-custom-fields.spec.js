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
 */

import { test, expect } from '@playwright/test'
import { apiBaseUrl, ensureApiSession, injectSession } from './auth-helpers.js'

// ==================== Helpers ====================

async function loginAsAdmin(page) {
  const session = await ensureApiSession({
    username: `co601_admin_${Date.now()}`,
    role: '/bidAdmin',
    fullName: 'CO-601 测试管理员',
  })
  await injectSession(page, session)
  return session
}

async function loginAsStaff(page) {
  const session = await ensureApiSession({
    username: `co601_staff_${Date.now()}`,
    role: 'bid-Team',
    fullName: 'CO-601 测试员工',
  })
  await injectSession(page, session)
  return session
}

/**
 * 通过 API 创建表单定义（包含自定义字段）
 */
async function createFormDefinitionWithCustomFields(session, scope, customFields) {
  const schema = {
    fields: customFields.map(field => ({
      key: field.key,
      label: field.label,
      type: field.type || 'text',
      required: field.required || false,
      enabled: field.enabled !== false,
      ...(field.options ? { options: field.options } : {}),
      ...(field.optionsText ? { optionsText: field.optionsText } : {})
    }))
  }

  const response = await fetch(`${apiBaseUrl}/api/admin/form-definitions`, {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${session.token}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      scope,
      scopeLabel: `CO-601 ${scope} 测试`,
      schema
    })
  })

  if (!response.ok) {
    const error = await response.json()
    throw new Error(`创建表单定义失败: ${JSON.stringify(error)}`)
  }

  const result = await response.json()
  return result.data.id
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
    const error = await response.json()
    throw new Error(`发布表单定义失败: ${JSON.stringify(error)}`)
  }

  return await response.json()
}

/**
 * 通过 API 获取表单定义
 */
async function getFormDefinition(session, scope) {
  const response = await fetch(`${apiBaseUrl}/api/form-definitions/${scope}/active`, {
    headers: {
      'Authorization': `Bearer ${session.token}`
    }
  })

  if (!response.ok) {
    return null
  }

  const result = await response.json()
  return result.data
}

/**
 * 等待元素出现并可交互
 */
async function waitForElement(page, selector, timeout = 10000) {
  await page.waitForSelector(selector, { state: 'visible', timeout })
}

// ==================== US1: 自定义字段落库与回显主链路 ====================

test.describe('US1: 自定义字段落库与回显主链路', () => {
  let adminSession
  let staffSession

  test.beforeEach(async ({ page }) => {
    adminSession = await loginAsAdmin(page)
    staffSession = await ensureApiSession({
      username: `co601_staff_${Date.now()}`,
      role: 'bid-Team',
      fullName: 'CO-601 测试员工',
    })
  })

  test('project.basic 自定义文本字段：创建向导填写提交 → 落库 → 回显一致', async ({ page }) => {
    // 1. 准备：为 project.basic 添加自定义字段
    const customFieldKey = `custom_text_${Date.now()}`
    const customFieldLabel = '自定义文本字段'
    const customFieldValue = '这是自定义文本内容'

    // 检查是否已有激活的表单定义
    const existingDef = await getFormDefinition(adminSession, 'project.basic')
    let definitionId

    if (!existingDef) {
      // 创建新的表单定义
      definitionId = await createFormDefinitionWithCustomFields(adminSession, 'project.basic', [
        { key: customFieldKey, label: customFieldLabel, type: 'text', required: false }
      ])
    } else {
      // 使用现有定义，添加自定义字段
      definitionId = existingDef.id
      const updatedFields = [...existingDef.fields, {
        key: customFieldKey,
        label: customFieldLabel,
        type: 'text',
        required: false,
        enabled: true
      }]

      const response = await fetch(`${apiBaseUrl}/api/admin/form-definitions/${definitionId}`, {
        method: 'PUT',
        headers: {
          'Authorization': `Bearer ${adminSession.token}`,
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({
          scopeLabel: existingDef.scopeLabel,
          schema: { fields: updatedFields },
          enabled: true
        })
      })

      if (!response.ok) {
        throw new Error('更新表单定义失败')
      }
    }

    // 发布表单定义
    await publishFormDefinition(adminSession, definitionId)

    // 2. 员工登录并创建项目
    await injectSession(page, staffSession)
    await page.goto('/project/create')

    // 等待基本信息表单加载
    await waitForElement(page, 'text=项目名称')

    // 填写基本信息
    await page.getByLabel('项目名称').fill('CO-601 测试项目')

    // 填写自定义字段
    const customFieldInput = page.getByLabel(customFieldLabel)
    await expect(customFieldInput).toBeVisible()
    await customFieldInput.fill(customFieldValue)

    // 点击下一步到详情页
    await page.getByRole('button', { name: '下一步' }).click()

    // 等待详情页加载
    await waitForElement(page, 'text=项目描述')

    // 填写项目描述（必填）
    await page.getByLabel('项目描述').fill('这是 CO-601 测试项目的描述')

    // 点击下一步到任务分解
    await page.getByRole('button', { name: '下一步' }).click()

    // 等待任务分解页加载
    await waitForElement(page, 'text=任务分解')

    // 添加一个任务
    await page.getByRole('button', { name: '添加任务' }).click()
    await page.getByPlaceholder('任务名称').fill('测试任务')
    await page.getByPlaceholder('任务描述').fill('测试任务描述')

    // 点击下一步到智能辅助
    await page.getByRole('button', { name: '下一步' }).click()

    // 等待智能辅助页加载
    await waitForElement(page, 'text=智能辅助')

    // 提交创建项目
    await page.getByRole('button', { name: '确认并创建项目' }).click()

    // 等待创建成功提示
    await expect(page.getByText('创建成功').first()).toBeVisible({ timeout: 10000 })

    // 3. 验证数据落库
    // 等待跳转到项目详情页
    await page.waitForURL(/\/project\/\d+/, { timeout: 10000 })

    // 获取项目 ID
    const url = page.url()
    const projectId = url.match(/\/project\/(\d+)/)[1]

    // 通过 API 验证 customFields 已保存
    const projectResponse = await fetch(`${apiBaseUrl}/api/projects/${projectId}`, {
      headers: {
        'Authorization': `Bearer ${adminSession.token}`
      }
    })

    expect(projectResponse.ok()).toBeTruthy()
    const projectData = await projectResponse.json()
    expect(projectData.data.customFields).toBeDefined()
    expect(projectData.data.customFields['project.basic']).toBeDefined()
    expect(projectData.data.customFields['project.basic'][customFieldKey]).toBe(customFieldValue)

    // 4. 验证回显
    // 刷新页面
    await page.reload()

    // 等待页面加载
    await waitForElement(page, 'text=项目名称')

    // 验证自定义字段值正确回显
    const customFieldInputAfterReload = page.getByLabel(customFieldLabel)
    await expect(customFieldInputAfterReload).toHaveValue(customFieldValue)
  })

  test('project.initiation 自定义下拉字段：立项填写提交 → 落库 → 回显一致', async ({ page }) => {
    // 1. 准备：为 project.initiation 添加自定义字段
    const customFieldKey = `custom_select_${Date.now()}`
    const customFieldLabel = '自定义下拉字段'
    const customFieldValue = 'option1'

    // 检查是否已有激活的表单定义
    const existingDef = await getFormDefinition(adminSession, 'project.initiation')
    let definitionId

    if (!existingDef) {
      // 创建新的表单定义
      definitionId = await createFormDefinitionWithCustomFields(adminSession, 'project.initiation', [
        {
          key: customFieldKey,
          label: customFieldLabel,
          type: 'select',
          required: false,
          optionsText: '选项1=option1\n选项2=option2\n选项3=option3'
        }
      ])
    } else {
      // 使用现有定义，添加自定义字段
      definitionId = existingDef.id
      const updatedFields = [...existingDef.fields, {
        key: customFieldKey,
        label: customFieldLabel,
        type: 'select',
        required: false,
        enabled: true,
        optionsText: '选项1=option1\n选项2=option2\n选项3=option3'
      }]

      const response = await fetch(`${apiBaseUrl}/api/admin/form-definitions/${definitionId}`, {
        method: 'PUT',
        headers: {
          'Authorization': `Bearer ${adminSession.token}`,
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({
          scopeLabel: existingDef.scopeLabel,
          schema: { fields: updatedFields },
          enabled: true
        })
      })

      if (!response.ok) {
        throw new Error('更新表单定义失败')
      }
    }

    // 发布表单定义
    await publishFormDefinition(adminSession, definitionId)

    // 2. 创建一个项目（用于立项测试）
    await injectSession(page, staffSession)
    await page.goto('/project/create')

    // 等待基本信息表单加载
    await waitForElement(page, 'text=项目名称')

    // 填写基本信息
    await page.getByLabel('项目名称').fill('CO-601 立项测试项目')

    // 点击下一步到详情页
    await page.getByRole('button', { name: '下一步' }).click()

    // 等待详情页加载
    await waitForElement(page, 'text=项目描述')

    // 填写项目描述（必填）
    await page.getByLabel('项目描述').fill('这是 CO-601 立项测试项目的描述')

    // 点击下一步到任务分解
    await page.getByRole('button', { name: '下一步' }).click()

    // 等待任务分解页加载
    await waitForElement(page, 'text=任务分解')

    // 添加一个任务
    await page.getByRole('button', { name: '添加任务' }).click()
    await page.getByPlaceholder('任务名称').fill('测试任务')
    await page.getByPlaceholder('任务描述').fill('测试任务描述')

    // 点击下一步到智能辅助
    await page.getByRole('button', { name: '下一步' }).click()

    // 等待智能辅助页加载
    await waitForElement(page, 'text=智能辅助')

    // 提交创建项目
    await page.getByRole('button', { name: '确认并创建项目' }).click()

    // 等待创建成功提示
    await expect(page.getByText('创建成功').first()).toBeVisible({ timeout: 10000 })

    // 等待跳转到项目详情页
    await page.waitForURL(/\/project\/\d+/, { timeout: 10000 })

    // 获取项目 ID
    const url = page.url()
    const projectId = url.match(/\/project\/(\d+)/)[1]

    // 3. 进入立项阶段
    await page.goto(`/project/${projectId}/initiation`)

    // 等待立项页面加载
    await waitForElement(page, 'text=是否需要保证金')

    // 填写必填字段
    await page.getByLabel('是否需要保证金').selectOption('NO')

    // 填写自定义下拉字段
    const customFieldSelect = page.getByLabel(customFieldLabel)
    await expect(customFieldSelect).toBeVisible()
    await customFieldSelect.click()
    await page.getByText('选项1').first().click()

    // 提交立项
    await page.getByRole('button', { name: '提交立项' }).click()

    // 等待提交成功提示
    await expect(page.getByText('提交成功').first()).toBeVisible({ timeout: 10000 })

    // 4. 验证数据落库
    // 通过 API 验证 customFields 已保存
    const initiationResponse = await fetch(`${apiBaseUrl}/api/projects/${projectId}/initiation`, {
      headers: {
        'Authorization': `Bearer ${adminSession.token}`
      }
    })

    expect(initiationResponse.ok()).toBeTruthy()
    const initiationData = await initiationResponse.json()
    expect(initiationData.data.customFields).toBeDefined()
    expect(initiationData.data.customFields['project.initiation']).toBeDefined()
    expect(initiationData.data.customFields['project.initiation'][customFieldKey]).toBe(customFieldValue)

    // 5. 验证回显
    // 刷新页面
    await page.reload()

    // 等待页面加载
    await waitForElement(page, 'text=是否需要保证金')

    // 验证自定义字段值正确回显
    const customFieldSelectAfterReload = page.getByLabel(customFieldLabel)
    await expect(customFieldSelectAfterReload).toHaveValue(customFieldValue)
  })

  test('老项目（无 custom_fields）打开不报错', async ({ page }) => {
    // 1. 创建一个没有自定义字段的老项目
    await injectSession(page, staffSession)
    await page.goto('/project/create')

    // 等待基本信息表单加载
    await waitForElement(page, 'text=项目名称')

    // 填写基本信息
    await page.getByLabel('项目名称').fill('CO-601 老项目测试')

    // 点击下一步到详情页
    await page.getByRole('button', { name: '下一步' }).click()

    // 等待详情页加载
    await waitForElement(page, 'text=项目描述')

    // 填写项目描述（必填）
    await page.getByLabel('项目描述').fill('这是 CO-601 老项目测试的描述')

    // 点击下一步到任务分解
    await page.getByRole('button', { name: '下一步' }).click()

    // 等待任务分解页加载
    await waitForElement(page, 'text=任务分解')

    // 添加一个任务
    await page.getByRole('button', { name: '添加任务' }).click()
    await page.getByPlaceholder('任务名称').fill('测试任务')
    await page.getByPlaceholder('任务描述').fill('测试任务描述')

    // 点击下一步到智能辅助
    await page.getByRole('button', { name: '下一步' }).click()

    // 等待智能辅助页加载
    await waitForElement(page, 'text=智能辅助')

    // 提交创建项目
    await page.getByRole('button', { name: '确认并创建项目' }).click()

    // 等待创建成功提示
    await expect(page.getByText('创建成功').first()).toBeVisible({ timeout: 10000 })

    // 等待跳转到项目详情页
    await page.waitForURL(/\/project\/\d+/, { timeout: 10000 })

    // 获取项目 ID
    const url = page.url()
    const projectId = url.match(/\/project\/(\d+)/)[1]

    // 2. 直接访问项目详情页
    await page.goto(`/project/${projectId}`)

    // 等待页面加载
    await waitForElement(page, 'text=项目名称')

    // 3. 验证页面正常加载，没有报错
    await expect(page.getByText('项目名称').first()).toBeVisible()

    // 4. 进入立项阶段
    await page.goto(`/project/${projectId}/initiation`)

    // 等待立项页面加载
    await waitForElement(page, 'text=是否需要保证金')

    // 验证页面正常加载，没有报错
    await expect(page.getByText('是否需要保证金').first()).toBeVisible()
  })
})

// ==================== US2: 系统预置字段防误改保护 ====================

test.describe('US2: 系统预置字段防误改保护', () => {
  let adminSession

  test.beforeEach(async ({ page }) => {
    adminSession = await loginAsAdmin(page)
  })

  test('设计器中 project.* 预置字段锁定：key/type 不可修改、删除按钮隐藏', async ({ page }) => {
    // 1. 打开设计器页面
    await page.goto('/settings/workflow-forms')

    // 等待页面加载
    await waitForElement(page, 'text=流程表单配置')

    // 2. 选择 project.basic 表单
    await page.getByText('project.basic').first().click()

    // 等待表单加载
    await waitForElement(page, 'text=字段配置器')

    // 3. 验证预置字段锁定
    // 查找预置字段行（例如 name 字段）
    const fieldList = page.locator('div.field-list')
    const nameFieldRows = fieldList.locator('div.field-row', { hasText: 'name' })
    const nameFieldRow = nameFieldRows.first()
    await expect(nameFieldRow).toBeVisible()

    // 验证 key 输入框被禁用
    const keyInput = nameFieldRow.locator('input[placeholder="字段 key"]')
    await expect(keyInput).toBeDisabled()

    // 验证类型选择框被禁用
    const typeSelect = nameFieldRow.locator('div.el-select')
    await expect(typeSelect).toHaveClass(/is-disabled/)

    // 验证删除按钮被隐藏
    const deleteButton = nameFieldRow.getByRole('button', { name: '删' })
    await expect(deleteButton).toBeHidden()

    // 4. 验证自定义字段可编辑
    // 添加一个自定义字段
    await page.getByRole('button', { name: '添加字段' }).click()

    // 填写自定义字段信息
    const customFieldKey = `custom_field_${Date.now()}`
    const newFieldRows = fieldList.locator('div.field-row')
    const newFieldRow = newFieldRows.last()

    // 验证 key 输入框可编辑
    const newKeyInput = newFieldRow.locator('input[placeholder="字段 key"]')
    await expect(newKeyInput).toBeEditable()
    await newKeyInput.fill(customFieldKey)

    // 验证类型选择框可编辑
    const newTypeSelect = newFieldRow.locator('div.el-select')
    await expect(newTypeSelect).not.toHaveClass(/is-disabled/)

    // 验证删除按钮可见
    const newDeleteButton = newFieldRow.getByRole('button', { name: '删' })
    await expect(newDeleteButton).toBeVisible()
  })

  test('设计器中 tender.entry 行为不变：原有锁定逻辑保持', async ({ page }) => {
    // 1. 打开设计器页面
    await page.goto('/settings/workflow-forms')

    // 等待页面加载
    await waitForElement(page, 'text=流程表单配置')

    // 2. 选择 tender.entry 表单
    await page.getByText('tender.entry').first().click()

    // 等待表单加载
    await waitForElement(page, 'text=字段配置器')

    // 3. 验证原有锁定逻辑保持不变
    // 查找 LOCKED_FIELD_KEYS 中的字段（例如 title 字段）
    const fieldList = page.locator('div.field-list')
    const titleFieldRows = fieldList.locator('div.field-row', { hasText: 'title' })
    const titleFieldRow = titleFieldRows.first()

    // 如果存在 title 字段，验证其锁定状态
    if (await titleFieldRow.isVisible()) {
      // 验证 key 输入框被禁用
      const keyInput = titleFieldRow.locator('input[placeholder="字段 key"]')
      await expect(keyInput).toBeDisabled()

      // 验证类型选择框被禁用
      const typeSelect = titleFieldRow.locator('div.el-select')
      await expect(typeSelect).toHaveClass(/is-disabled/)

      // 验证删除按钮被隐藏
      const deleteButton = titleFieldRow.getByRole('button', { name: '删' })
      await expect(deleteButton).toBeHidden()
    }
  })

  test('设计器保存/发布前校验：自定义字段 key 冲突阻断', async ({ page }) => {
    // 1. 打开设计器页面
    await page.goto('/settings/workflow-forms')

    // 等待页面加载
    await waitForElement(page, 'text=流程表单配置')

    // 2. 选择 project.basic 表单
    await page.getByText('project.basic').first().click()

    // 等待表单加载
    await waitForElement(page, 'text=字段配置器')

    // 3. 添加一个与预置字段冲突的自定义字段
    await page.getByRole('button', { name: '添加字段' }).click()

    // 填写与预置字段冲突的 key（例如 name）
    const fieldList = page.locator('div.field-list')
    const allFieldRows = fieldList.locator('div.field-row')
    const newFieldRow = allFieldRows.last()
    const newKeyInput = newFieldRow.locator('input[placeholder="字段 key"]')
    await newKeyInput.fill('name')

    // 4. 尝试保存
    await page.getByRole('button', { name: '保存草稿' }).click()

    // 5. 验证错误提示
    await expect(page.getByText('为系统预置字段，不可自定义').first()).toBeVisible({ timeout: 5000 })
  })
})

// ==================== US3: 自定义字段全生命周期管理 ====================

test.describe('US3: 自定义字段全生命周期管理', () => {
  let adminSession
  let staffSession

  test.beforeEach(async ({ page }) => {
    adminSession = await loginAsAdmin(page)
    staffSession = await ensureApiSession({
      username: `co601_staff_${Date.now()}`,
      role: 'bid-Team',
      fullName: 'CO-601 测试员工',
    })
  })

  test('编辑字段 label 重发布 → 业务页新 label 旧值仍在', async ({ page }) => {
    // 1. 准备：为 project.basic 添加自定义字段
    const customFieldKey = `custom_edit_${Date.now()}`
    const originalLabel = '原始标签'
    const updatedLabel = '更新后的标签'
    const customFieldValue = '这是自定义字段值'

    // 创建表单定义
    const definitionId = await createFormDefinitionWithCustomFields(adminSession, 'project.basic', [
      { key: customFieldKey, label: originalLabel, type: 'text', required: false }
    ])

    // 发布表单定义
    await publishFormDefinition(adminSession, definitionId)

    // 2. 员工创建项目并填写自定义字段
    await injectSession(page, staffSession)
    await page.goto('/project/create')

    // 等待基本信息表单加载
    await waitForElement(page, 'text=项目名称')

    // 填写基本信息
    await page.getByLabel('项目名称').fill('CO-601 编辑测试项目')

    // 填写自定义字段
    const customFieldInput = page.getByLabel(originalLabel)
    await expect(customFieldInput).toBeVisible()
    await customFieldInput.fill(customFieldValue)

    // 点击下一步到详情页
    await page.getByRole('button', { name: '下一步' }).click()

    // 等待详情页加载
    await waitForElement(page, 'text=项目描述')

    // 填写项目描述（必填）
    await page.getByLabel('项目描述').fill('这是 CO-601 编辑测试项目的描述')

    // 点击下一步到任务分解
    await page.getByRole('button', { name: '下一步' }).click()

    // 等待任务分解页加载
    await waitForElement(page, 'text=任务分解')

    // 添加一个任务
    await page.getByRole('button', { name: '添加任务' }).click()
    await page.getByPlaceholder('任务名称').fill('测试任务')
    await page.getByPlaceholder('任务描述').fill('测试任务描述')

    // 点击下一步到智能辅助
    await page.getByRole('button', { name: '下一步' }).click()

    // 等待智能辅助页加载
    await waitForElement(page, 'text=智能辅助')

    // 提交创建项目
    await page.getByRole('button', { name: '确认并创建项目' }).click()

    // 等待创建成功提示
    await expect(page.getByText('创建成功').first()).toBeVisible({ timeout: 10000 })

    // 等待跳转到项目详情页
    await page.waitForURL(/\/project\/\d+/, { timeout: 10000 })

    // 获取项目 ID
    const url = page.url()
    const projectId = url.match(/\/project\/(\d+)/)[1]

    // 3. 管理员编辑字段 label 并重发布
    await injectSession(page, adminSession)

    // 获取当前表单定义
    const currentDef = await getFormDefinition(adminSession, 'project.basic')

    // 更新字段 label
    const updatedFields = currentDef.fields.map(field => {
      if (field.key === customFieldKey) {
        return { ...field, label: updatedLabel }
      }
      return field
    })

    const response = await fetch(`${apiBaseUrl}/api/admin/form-definitions/${currentDef.id}`, {
      method: 'PUT',
      headers: {
        'Authorization': `Bearer ${adminSession.token}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        scopeLabel: currentDef.scopeLabel,
        schema: { fields: updatedFields },
        enabled: true
      })
    })

    if (!response.ok) {
      throw new Error('更新表单定义失败')
    }

    // 重发布表单定义
    await publishFormDefinition(adminSession, currentDef.id)

    // 4. 验证业务页新 label 旧值仍在
    await page.goto(`/project/${projectId}`)

    // 等待页面加载
    await waitForElement(page, 'text=项目名称')

    // 验证字段 label 已更新
    await expect(page.getByText(updatedLabel)).toBeVisible()

    // 验证字段值仍然保持
    const updatedFieldInput = page.getByLabel(updatedLabel)
    await expect(updatedFieldInput).toHaveValue(customFieldValue)
  })

  test('删除字段不再渲染但历史值保留', async ({ page }) => {
    // 1. 准备：为 project.basic 添加自定义字段
    const customFieldKey = `custom_delete_${Date.now()}`
    const customFieldLabel = '待删除字段'
    const customFieldValue = '这个值应该被保留'

    // 创建表单定义
    const definitionId = await createFormDefinitionWithCustomFields(adminSession, 'project.basic', [
      { key: customFieldKey, label: customFieldLabel, type: 'text', required: false }
    ])

    // 发布表单定义
    await publishFormDefinition(adminSession, definitionId)

    // 2. 员工创建项目并填写自定义字段
    await injectSession(page, staffSession)
    await page.goto('/project/create')

    // 等待基本信息表单加载
    await waitForElement(page, 'text=项目名称')

    // 填写基本信息
    await page.getByLabel('项目名称').fill('CO-601 删除测试项目')

    // 填写自定义字段
    const customFieldInput = page.getByLabel(customFieldLabel)
    await expect(customFieldInput).toBeVisible()
    await customFieldInput.fill(customFieldValue)

    // 点击下一步到详情页
    await page.getByRole('button', { name: '下一步' }).click()

    // 等待详情页加载
    await waitForElement(page, 'text=项目描述')

    // 填写项目描述（必填）
    await page.getByLabel('项目描述').fill('这是 CO-601 删除测试项目的描述')

    // 点击下一步到任务分解
    await page.getByRole('button', { name: '下一步' }).click()

    // 等待任务分解页加载
    await waitForElement(page, 'text=任务分解')

    // 添加一个任务
    await page.getByRole('button', { name: '添加任务' }).click()
    await page.getByPlaceholder('任务名称').fill('测试任务')
    await page.getByPlaceholder('任务描述').fill('测试任务描述')

    // 点击下一步到智能辅助
    await page.getByRole('button', { name: '下一步' }).click()

    // 等待智能辅助页加载
    await waitForElement(page, 'text=智能辅助')

    // 提交创建项目
    await page.getByRole('button', { name: '确认并创建项目' }).click()

    // 等待创建成功提示
    await expect(page.getByText('创建成功').first()).toBeVisible({ timeout: 10000 })

    // 等待跳转到项目详情页
    await page.waitForURL(/\/project\/\d+/, { timeout: 10000 })

    // 获取项目 ID
    const url = page.url()
    const projectId = url.match(/\/project\/(\d+)/)[1]

    // 3. 管理员删除字段并重发布
    await injectSession(page, adminSession)

    // 获取当前表单定义
    const currentDef = await getFormDefinition(adminSession, 'project.basic')

    // 删除字段
    const updatedFields = currentDef.fields.filter(field => field.key !== customFieldKey)

    const response = await fetch(`${apiBaseUrl}/api/admin/form-definitions/${currentDef.id}`, {
      method: 'PUT',
      headers: {
        'Authorization': `Bearer ${adminSession.token}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        scopeLabel: currentDef.scopeLabel,
        schema: { fields: updatedFields },
        enabled: true
      })
    })

    if (!response.ok) {
      throw new Error('更新表单定义失败')
    }

    // 重发布表单定义
    await publishFormDefinition(adminSession, currentDef.id)

    // 4. 验证字段不再渲染但历史值保留
    await page.goto(`/project/${projectId}`)

    // 等待页面加载
    await waitForElement(page, 'text=项目名称')

    // 验证字段不再渲染
    await expect(page.getByText(customFieldLabel)).toBeHidden()

    // 5. 验证历史值保留
    // 通过 API 验证 customFields 中的值仍然存在
    const projectResponse = await fetch(`${apiBaseUrl}/api/projects/${projectId}`, {
      headers: {
        'Authorization': `Bearer ${adminSession.token}`
      }
    })

    expect(projectResponse.ok()).toBeTruthy()
    const projectData = await projectResponse.json()
    expect(projectData.data.customFields).toBeDefined()
    expect(projectData.data.customFields['project.basic']).toBeDefined()
    expect(projectData.data.customFields['project.basic'][customFieldKey]).toBe(customFieldValue)
  })

  test('类型变更后历史值不报错（文本改下拉）', async ({ page }) => {
    // 1. 准备：为 project.basic 添加自定义文本字段
    const customFieldKey = `custom_type_${Date.now()}`
    const customFieldLabel = '类型变更字段'
    const customFieldValue = '这是文本值'

    // 创建表单定义（文本类型）
    const definitionId = await createFormDefinitionWithCustomFields(adminSession, 'project.basic', [
      { key: customFieldKey, label: customFieldLabel, type: 'text', required: false }
    ])

    // 发布表单定义
    await publishFormDefinition(adminSession, definitionId)

    // 2. 员工创建项目并填写自定义字段
    await injectSession(page, staffSession)
    await page.goto('/project/create')

    // 等待基本信息表单加载
    await waitForElement(page, 'text=项目名称')

    // 填写基本信息
    await page.getByLabel('项目名称').fill('CO-601 类型变更测试项目')

    // 填写自定义字段
    const customFieldInput = page.getByLabel(customFieldLabel)
    await expect(customFieldInput).toBeVisible()
    await customFieldInput.fill(customFieldValue)

    // 点击下一步到详情页
    await page.getByRole('button', { name: '下一步' }).click()

    // 等待详情页加载
    await waitForElement(page, 'text=项目描述')

    // 填写项目描述（必填）
    await page.getByLabel('项目描述').fill('这是 CO-601 类型变更测试项目的描述')

    // 点击下一步到任务分解
    await page.getByRole('button', { name: '下一步' }).click()

    // 等待任务分解页加载
    await waitForElement(page, 'text=任务分解')

    // 添加一个任务
    await page.getByRole('button', { name: '添加任务' }).click()
    await page.getByPlaceholder('任务名称').fill('测试任务')
    await page.getByPlaceholder('任务描述').fill('测试任务描述')

    // 点击下一步到智能辅助
    await page.getByRole('button', { name: '下一步' }).click()

    // 等待智能辅助页加载
    await waitForElement(page, 'text=智能辅助')

    // 提交创建项目
    await page.getByRole('button', { name: '确认并创建项目' }).click()

    // 等待创建成功提示
    await expect(page.getByText('创建成功').first()).toBeVisible({ timeout: 10000 })

    // 等待跳转到项目详情页
    await page.waitForURL(/\/project\/\d+/, { timeout: 10000 })

    // 获取项目 ID
    const url = page.url()
    const projectId = url.match(/\/project\/(\d+)/)[1]

    // 3. 管理员将字段类型从文本改为下拉并重发布
    await injectSession(page, adminSession)

    // 获取当前表单定义
    const currentDef = await getFormDefinition(adminSession, 'project.basic')

    // 更新字段类型
    const updatedFields = currentDef.fields.map(field => {
      if (field.key === customFieldKey) {
        return {
          ...field,
          type: 'select',
          optionsText: '选项1=option1\n选项2=option2'
        }
      }
      return field
    })

    const response = await fetch(`${apiBaseUrl}/api/admin/form-definitions/${currentDef.id}`, {
      method: 'PUT',
      headers: {
        'Authorization': `Bearer ${adminSession.token}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        scopeLabel: currentDef.scopeLabel,
        schema: { fields: updatedFields },
        enabled: true
      })
    })

    if (!response.ok) {
      throw new Error('更新表单定义失败')
    }

    // 重发布表单定义
    await publishFormDefinition(adminSession, currentDef.id)

    // 4. 验证历史值不报错
    await page.goto(`/project/${projectId}`)

    // 等待页面加载
    await waitForElement(page, 'text=项目名称')

    // 验证字段仍然渲染（现在是一个下拉框）
    const updatedField = page.getByLabel(customFieldLabel)
    await expect(updatedField).toBeVisible()

    // 验证页面没有报错
    // 如果历史值无法匹配下拉选项，应该按文本兜底显示
    // 这里我们只验证页面正常加载，没有错误提示
    await expect(page.getByText('错误').first()).toBeHidden()
  })
})
