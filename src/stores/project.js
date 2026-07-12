// Input: projectsApi, resourcesApi, httpClient (from @/api), ProjectLoadError (from @/utils/projectErrors), taskBackendToCard (from @/views/Project/project-utils)
// Output: useProjectStore - project detail, workflow, task deletion, expense aggregation state, and project load error propagation
// Pos: src/stores/ - State management layer
// CO-468: getProjectById 新增 forceRefresh 选项，用于阶段切换后强制刷新项目数据（含 tasks）。
// CO-574: updateTask 用 taskBackendToCard 映射后端 DTO 为卡片字段并 Object.assign 到现有数组元素（保持引用稳定），修复保证金任务改执行人后看板不刷新。
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { defineStore } from 'pinia'
import { httpClient, projectsApi, resourcesApi } from '@/api'
import { taskStatusDictApi } from '@/api/modules/taskStatusDict.js'
import { taskExtendedFieldApi } from '@/api/modules/taskExtendedField.js'
import { createTaskDeliverable, deleteTaskDeliverable } from '@/api/modules/taskDeliverables.js'
import { tasksApi } from '@/api/modules/tasks.js'
import { ProjectLoadError, PROJECT_LOAD_ERROR_TYPE } from '@/utils/projectErrors.js'
import { taskBackendToCard } from '@/views/Project/project-utils.js'

function normalizeExpenseDate(value) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  return date.toISOString().split('T')[0]
}

function normalizeExpenseStatus(rawStatus, approvalStatus) {
  const backendStatus = String(rawStatus || '').toUpperCase()
  const normalizedApprovalStatus = String(approvalStatus || '').toUpperCase()

  if (backendStatus === 'RETURNED') return 'returned'
  if (backendStatus === 'PAID' || backendStatus === 'RETURN_REQUESTED') return 'paid'
  if (backendStatus === 'APPROVED' || backendStatus === 'PENDING_APPROVAL' || backendStatus === 'REJECTED') return 'pending'
  if (backendStatus === 'PENDING' || backendStatus === 'PENDING_PAYMENT') return 'pending'
  if (backendStatus === 'RETURNED_CONFIRMED') return 'returned'
  if (normalizedApprovalStatus === 'APPROVED') return 'pending'
  if (normalizedApprovalStatus === 'REJECTED') return 'pending'
  return rawStatus || 'pending'
}

function normalizeProjectExpense(item = {}) {
  const payments = Array.isArray(item.paymentRecords)
    ? item.paymentRecords
    : Array.isArray(item.payments)
      ? item.payments
      : []
  const latestPayment = item.latestPayment || payments[0] || null

  return {
    id: item.id,
    projectId: item.projectId ?? item.project?.id ?? null,
    project: item.project || item.projectName || item.projectTitle || (item.projectId ? `项目#${item.projectId}` : '未关联项目'),
    type: item.type || item.expenseType || item.categoryName || item.category || '其他',
    amount: Number(item.amount || 0),
    status: normalizeExpenseStatus(item.status, item.approvalStatus),
    backendStatus: String(item.status || item.backendStatus || ''),
    approvalStatus: String(item.approvalStatus || ''),
    date: item.date || item.applyDate || normalizeExpenseDate(item.createdAt),
    remark: item.remark || item.description || item.approvalComment || '',
    description: item.description || '',
    paidAt: latestPayment?.paidAt || latestPayment?.paymentDate || item.paidAt || item.paymentDate || '',
    paidBy: latestPayment?.paidBy || item.paidBy || '',
    paymentMethod: latestPayment?.paymentMethod || latestPayment?.method || item.paymentMethod || '',
    paymentReference: latestPayment?.paymentReference || latestPayment?.reference || item.paymentReference || '',
    paymentRecords: payments,
    raw: item,
  }
}

function buildDocumentFormData(file, data, taskId, documentCategory) {
  const formData = new FormData()
  formData.set('file', file)
  formData.set('name', data.name || file.name || '文件')
  formData.set('size', data.size || formatFileSize(file))
  formData.set('fileType', data.fileType || file.type || '')
  formData.set('documentCategory', documentCategory)
  formData.set('linkedEntityType', 'TASK')
  formData.set('linkedEntityId', String(taskId))
  if (data.uploaderId != null) {
    formData.set('uploaderId', String(data.uploaderId))
  }
  if (data.uploaderName) {
    formData.set('uploaderName', data.uploaderName)
  }
  return formData
}

function extractExpenseItems(payload) {
  if (Array.isArray(payload)) return payload
  if (Array.isArray(payload?.content)) return payload.content
  if (Array.isArray(payload?.records)) return payload.records
  if (Array.isArray(payload?.items)) return payload.items
  if (Array.isArray(payload?.data)) return payload.data
  return []
}

function buildExpenseSummary(expenses = []) {
  return expenses.reduce((summary, item) => {
    const amount = Number(item.amount || 0)
    summary.totalAmount += amount
    if (item.status === 'paid') summary.paidAmount += amount
    else if (item.status === 'returned') summary.returnedAmount += amount
    else summary.pendingAmount += amount
    return summary
  }, {
    totalAmount: 0,
    paidAmount: 0,
    pendingAmount: 0,
    returnedAmount: 0,
  })
}

function formatFileSize(file) {
  const bytes = Number(file?.size || 0)
  const kb = Math.max(1, Math.round(bytes / 1024))
  return kb < 1024 ? `${kb}KB` : `${Math.round(kb / 1024)}MB`
}

function findTaskInProject(project, taskId) {
  if (!project || !Array.isArray(project.tasks)) return null
  return project.tasks.find((task) => String(task.id) === String(taskId)) || null
}

export const useProjectStore = defineStore('project', {
  state: () => ({
    projects: [],
    currentProject: null,
    currentProjectExpenses: [],
    currentProjectExpenseSummary: {
      totalAmount: 0,
      paidAmount: 0,
      pendingAmount: 0,
      returnedAmount: 0,
    },
    expenseLoading: false,
    expenseError: '',
    taskStatuses: [],
    taskStatusesLoaded: false,
    taskExtendedFields: [],
    taskExtendedFieldsLoaded: false,
  }),

  getters: {
    inProgressProjects: (state) => state.projects.filter(p => p.status !== 'won' && p.status !== 'lost'),
    wonProjects: (state) => state.projects.filter(p => p.status === 'won'),
    findProjectById: (state) => (id) => state.projects.find(p => p.id === id),
    terminalStatusCodes: (state) => new Set(
      (state.taskStatuses || []).filter((s) => s && s.terminal).map((s) => s.code)
    ),
    isTerminalStatus() {
      return (code) => {
        if (!code) return false
        return this.terminalStatusCodes.has(String(code).toUpperCase())
      }
    }
  },

  actions: {
    async getProjects(filters = {}) {
      try {
        const result = await projectsApi.getList(filters)
        this.projects = result?.success ? (result.data || []) : []
      } catch (error) {
        console.warn('API 调用失败，返回空项目列表:', error.message)
        this.projects = []
      }
      return this.projects
    },

    // CO-468: 新增 forceRefresh 选项。阶段切换（如立项审批通过转入标书制作）
    // 后端会同步创建保证金任务，但默认走缓存会返回 stale 的 tasks，
    // 导致任务看板渲染不出新建任务。forceRefresh=true 时跳过缓存，
    // 强制走 API 拉取最新数据，并回写 this.projects 缓存。
    async getProjectById(id, { forceRefresh = false } = {}) {
      if (!forceRefresh) {
        const existingProject = this.projects.find(p => String(p.id) === String(id))
        if (existingProject) {
          this.currentProject = existingProject
          return existingProject
        }
      }

      try {
        const result = await projectsApi.getDetail(id)
        const project = result?.success ? result.data : null
        if (project) {
          this.currentProject = project
          // forceRefresh 路径下同步更新 this.projects 缓存，
          // 避免下次普通调用再次拿到旧数据。
          if (forceRefresh) {
            const idx = this.projects.findIndex(p => String(p.id) === String(id))
            if (idx >= 0) {
              this.projects[idx] = project
            } else {
              this.projects.push(project)
            }
          }
          return project
        }
        // API 返回成功但无数据（success=false 或 data=null），视为项目不存在
        this.currentProject = null
        throw new ProjectLoadError(PROJECT_LOAD_ERROR_TYPE.NOT_FOUND, '项目不存在', null)
      } catch (error) {
        // 已经是 ProjectLoadError 直接抛出（避免双重包装）
        if (error instanceof ProjectLoadError) {
          throw error
        }
        // 根据 HTTP 状态码分类抛出对应错误类型
        const status = error?.response?.status
        this.currentProject = null
        if (status === 403) {
          throw new ProjectLoadError(PROJECT_LOAD_ERROR_TYPE.NO_PERMISSION, '无权限访问该项目', error)
        }
        if (status === 404) {
          throw new ProjectLoadError(PROJECT_LOAD_ERROR_TYPE.NOT_FOUND, '项目不存在', error)
        }
        throw new ProjectLoadError(PROJECT_LOAD_ERROR_TYPE.NETWORK_ERROR, '加载失败', error)
      }
    },

    async getProjectExpenses(projectId, options = {}) {
      const { projectName = '' } = options
      this.expenseLoading = true
      this.expenseError = ''

      try {
        const projectResponse = await httpClient.get(`/api/resources/expenses/project/${projectId}`)
        const expenses = extractExpenseItems(projectResponse?.data).map(normalizeProjectExpense)
        this.currentProjectExpenses = expenses
        this.currentProjectExpenseSummary = buildExpenseSummary(expenses)
        return expenses
      } catch (projectEndpointError) {
        try {
          const listResponse = await resourcesApi.expenses.getList(projectName ? { project: projectName } : {})
          const expenses = (Array.isArray(listResponse?.data) ? listResponse.data : [])
            .map(normalizeProjectExpense)
            .filter((item) => {
              if (String(item.projectId || '') === String(projectId || '')) return true
              if (projectName && item.project === projectName) return true
              return false
            })

          this.currentProjectExpenses = expenses
          this.currentProjectExpenseSummary = buildExpenseSummary(expenses)
          if (!listResponse?.success) {
            this.expenseError = listResponse?.message || '项目费用接口暂不可用，已回退到费用台账过滤结果'
          } else if (expenses.length === 0) {
            this.expenseError = ''
          } else {
            this.expenseError = '项目费用接口暂未就绪，当前展示来自费用台账过滤结果'
          }
          return expenses
        } catch (fallbackError) {
          this.currentProjectExpenses = []
          this.currentProjectExpenseSummary = buildExpenseSummary([])
          this.expenseError = fallbackError?.message || projectEndpointError?.message || '加载项目费用失败'
          return []
        }
      } finally {
        this.expenseLoading = false
      }
    },

    async createProject(data) {
      const result = await projectsApi.create(data)
      const newProject = result?.data
      if (newProject) {
        this.projects.unshift(newProject)
      }
      return newProject
    },

    async updateProject(id, data) {
      const result = await projectsApi.update(id, data)
      const updatedProject = result?.data
      const index = this.projects.findIndex(p => String(p.id) === String(id))
      if (index !== -1 && updatedProject) {
        this.projects[index] = { ...this.projects[index], ...updatedProject }
      }
      if (this.currentProject && String(this.currentProject.id) === String(id) && updatedProject) {
        this.currentProject = { ...this.currentProject, ...updatedProject }
      }
      return updatedProject
    },

    async updateTask(projectId, taskId, dto) {
      const result = await projectsApi.updateTask(taskId, dto)
      if (!result?.success) {
        throw new Error(result?.msg || '更新任务失败')
      }
      const project = this.currentProject
      if (project && String(project.id) === String(projectId) && Array.isArray(project.tasks)) {
        const idx = project.tasks.findIndex(t => t.id === taskId)
        if (idx >= 0) {
          // CO-574: 用 taskBackendToCard 把后端 DTO（assigneeName 等）映射成看板卡片字段（owner 等），
          // 并 Object.assign 到现有数组元素保持引用稳定。
          // 旧实现用 spread 创建新对象替换数组元素，导致 useProjectDetailTaskActions.handleSaveTask
          // 里的 target 引用失效（变成孤儿），看板执行人改了不刷新；且 spread 后端 DTO 字段名与卡片不一致，
          // owner 字段仍为旧值。两问题叠加 → 必须刷新浏览器才看到新执行人。
          Object.assign(project.tasks[idx], taskBackendToCard(result.data))
        }
      }
      return result.data
    },

    async updateTaskStatus(projectId, taskId, status) {
      const project = this.projects.find(p => p.id === projectId)
      if (project) {
        const task = project.tasks.find(t => t.id === taskId)
        if (task) {
          task.status = status
          const doneCount = project.tasks.filter(t => this.isTerminalStatus(t.status)).length
          project.progress = Math.round((doneCount / project.tasks.length) * 100)
        }
      }
    },

    async loadTaskStatuses() {
      if (this.taskStatusesLoaded) return this.taskStatuses
      try {
        const res = await taskStatusDictApi.list()
        this.taskStatuses = Array.isArray(res?.data) ? res.data : []
      } catch (err) {
        console.error('[projectStore] loadTaskStatuses failed', err)
        this.taskStatuses = []
      } finally {
        this.taskStatusesLoaded = true
      }
      return this.taskStatuses
    },

    invalidateTaskStatuses() {
      this.taskStatuses = []
      this.taskStatusesLoaded = false
    },

    async loadTaskExtendedFields() {
      if (this.taskExtendedFieldsLoaded) return this.taskExtendedFields
      try {
        const res = await taskExtendedFieldApi.list()
        this.taskExtendedFields = Array.isArray(res?.data) ? res.data : []
      } catch (err) {
        console.error('[projectStore] loadTaskExtendedFields failed', err)
        this.taskExtendedFields = []
      } finally {
        this.taskExtendedFieldsLoaded = true
      }
      return this.taskExtendedFields
    },

    invalidateTaskExtendedFields() {
      this.taskExtendedFields = []
      this.taskExtendedFieldsLoaded = false
    },

    async uploadTaskAttachment(projectId, taskId, data = {}) {
      if (!projectId) {
        throw new Error('项目ID不能为空')
      }
      if (!taskId) {
        throw new Error('任务ID不能为空')
      }
      const file = data.file || null
      if (!file) {
        throw new Error('任务附件文件不能为空')
      }
      // CO-519: 拦截空文件（size=0），后端 UploadValidationPolicy 会拒绝
      if (file.size === 0) {
        throw new Error(`文件「${file.name || ''}」为空（0 字节），请检查后重新选择`)
      }
      const formData = buildDocumentFormData(file, data, taskId, 'TASK_ATTACHMENT')
      const uploadResult = await projectsApi.uploadDocument(projectId, formData)
      if (!uploadResult?.success || !uploadResult?.data) {
        throw new Error(uploadResult?.message || '上传任务附件失败')
      }
      const uploadedDocument = uploadResult.data
      const task = findTaskInProject(this.currentProject, taskId)
      if (task) {
        task.attachments = Array.isArray(task.attachments) ? task.attachments : []
        const exists = task.attachments.some((item) => String(item.id) === String(uploadedDocument.id))
        if (!exists) {
          task.attachments.unshift(uploadedDocument)
        }
      }
      return uploadedDocument
    },

    async addDeliverable(projectId, taskId, data = {}) {
      if (!projectId) {
        throw new Error('项目ID不能为空')
      }
      if (!taskId) {
        throw new Error('任务ID不能为空')
      }
      const file = data.file || null
      if (!file) {
        throw new Error('任务交付物文件不能为空')
      }
      // CO-519: 拦截空文件（size=0），后端 UploadValidationPolicy 会拒绝
      if (file.size === 0) {
        throw new Error(`文件「${file.name || ''}」为空（0 字节），请检查后重新选择`)
      }
      let uploadedDocument = null

      const formData = buildDocumentFormData(file, data, taskId, 'TASK_DELIVERABLE')
      const uploadResult = await projectsApi.uploadDocument(projectId, formData)
      if (!uploadResult?.success || !uploadResult?.data) {
        throw new Error(uploadResult?.message || '上传任务交付物失败')
      }
      uploadedDocument = uploadResult.data

      const payload = {
        name: data.name || uploadedDocument?.name || file?.name || '任务交付物',
        deliverableType: data.deliverableType || 'DOCUMENT',
        size: uploadedDocument?.size || data.size || formatFileSize(file),
        fileType: uploadedDocument?.fileType || data.fileType || file?.type || null,
        url: uploadedDocument?.fileUrl || data.url || null,
      }
      const result = await createTaskDeliverable(projectId, taskId, payload)
      if (!result?.success || !result?.data) {
        throw new Error(result?.msg || '保存任务交付物失败')
      }

      const saved = result.data
      const task = findTaskInProject(this.currentProject, taskId)
      if (task) {
        task.deliverables = Array.isArray(task.deliverables) ? task.deliverables : []
        const exists = task.deliverables.some((item) => String(item.id) === String(saved.id))
        if (!exists) {
          task.deliverables.unshift(saved)
        }
        task.hasDeliverable = task.deliverables.length > 0
      }
      return saved
    },

    async removeDeliverable(projectId, taskId, deliverableId) {
      const result = await deleteTaskDeliverable(projectId, taskId, deliverableId)
      if (!result?.success) {
        throw new Error(result?.msg || '删除任务交付物失败')
      }
      const task = findTaskInProject(this.currentProject, taskId)
      if (task && Array.isArray(task.deliverables)) {
        task.deliverables = task.deliverables.filter((item) => String(item.id) !== String(deliverableId))
        task.hasDeliverable = task.deliverables.length > 0
      }
      return result
    },

    // CO-387: 删除任务。后端 DELETE /api/tasks/{id} 返回 204 无 body，
    // 权限由 TaskService.assertCanManageTask 校验（管理员/组长/项目负责人/辅助负责人）。
    // 失败时 httpClient 错误拦截器会抛错，调用方 catch 即可。
    async removeTask(taskId) {
      if (!taskId) {
        throw new Error('任务ID不能为空')
      }
      await tasksApi.deleteTask(taskId)
      const project = this.currentProject
      if (project && Array.isArray(project.tasks)) {
        project.tasks = project.tasks.filter((t) => String(t.id) !== String(taskId))
      }
    }
  }
})
