import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { tendersApi } from '@/api'
import {
  applyTenderDetailPrefill,
  buildProjectPrefillFromTender,
  hasGlobalHttpErrorMessage
} from '../../createTenderPrefill.js'
import { buildTaskCreatePayloadsFromRows } from './projectCreateTaskPayloads.js'
import { useProjectCreateCustomFields } from './useProjectCreateCustomFields.js'
import { decodeQueryValue, decodeNumericQuery, splitTags } from './queryDecode.js'

const formatDateTime = (value, fallbackTime = '00:00:00') => {
  if (!value) return ''
  if (String(value).includes('T')) return String(value)
  return `${value}T${fallbackTime}`
}

export function useProjectCreateModel({ route, userStore, projectStore, router }) {
  const basicForm = reactive({
    name: '',
    customer: '',
    budget: null,
    industry: '',
    region: '',
    platform: '',
    deadline: '',
    manager: '',
    competitors: []
  })

  const detailForm = reactive({
    description: '',
    tags: [],
    startDate: '',
    endDate: '',
    remark: '',
    projectLeaderName: '',
    leaderDepartment: ''
  })

  const taskForm = reactive({
    tasks: [
      { name: '', owner: '', deadline: '', priority: 'medium', status: 'TODO' }
    ]
  })

  const sourceInfo = reactive({
    module: '',
    customerId: '',
    customerName: '',
    opportunityId: '',
    reasoningSummary: ''
  })

  const competitorAnalysis = ref([])
  const availableTenders = ref([])
  const selectedTenderId = ref(null)
  const isEditMode = ref(false)
  const editProjectId = ref(null)

  // CO-601: 自定义字段注册表（schema 登记 → payload 收集 → 编辑回显），实现见 useProjectCreateCustomFields.js
  const { setCustomFieldsSchema, collectAll: collectCustomFieldsAll, mergeAll: mergeCustomFieldsAll } = useProjectCreateCustomFields()

  function addTask() {
    taskForm.tasks.push({
      name: '',
      owner: '',
      deadline: '',
      priority: 'medium',
      status: 'TODO'
    })
  }

  function removeTask(index) {
    taskForm.tasks.splice(index, 1)
  }

  function handleCompetitorsChange(value) {
    value.forEach((name) => {
      const existing = competitorAnalysis.value.find((c) => c.name === name)
      if (!existing) {
        competitorAnalysis.value.push({
          name,
          strength: '',
          weakness: '',
          winRate: 0,
          history: ''
        })
      }
    })
    competitorAnalysis.value = competitorAnalysis.value.filter((c) => value.includes(c.name))
  }

  function applyOpportunityPrefill() {
    const projectName = decodeQueryValue(route.query.projectName)
    const customerName = decodeQueryValue(route.query.customerName)
    const industry = decodeQueryValue(route.query.industry)
    const region = decodeQueryValue(route.query.region)
    const predictedBudget = decodeNumericQuery(route.query.budget)
    const deadline = decodeQueryValue(route.query.deadline)
    const description = decodeQueryValue(route.query.description)
    const remark = decodeQueryValue(route.query.remark)
    const tags = splitTags(route.query.tags)

    if (projectName) basicForm.name = projectName
    if (customerName) basicForm.customer = customerName
    if (industry) basicForm.industry = industry
    if (region) basicForm.region = region
    if (predictedBudget !== null) basicForm.budget = predictedBudget
    if (deadline) basicForm.deadline = deadline
    if (description) detailForm.description = description
    if (tags.length > 0) detailForm.tags = tags
    if (remark) detailForm.remark = remark

    sourceInfo.module = decodeQueryValue(route.query.sourceModule)
    sourceInfo.customerId = decodeQueryValue(route.query.sourceCustomerId)
    sourceInfo.customerName = decodeQueryValue(route.query.sourceCustomerName || customerName)
    sourceInfo.opportunityId = decodeQueryValue(route.query.sourceOpportunityId)
    sourceInfo.reasoningSummary = decodeQueryValue(route.query.sourceReasoningSummary)
  }

  async function loadTenderDetailPrefill() {
    const tenderId = selectedTenderId.value
    if (!tenderId) return
    try {
      const result = await tendersApi.getDetail(tenderId)
      if (result?.success && result.data) {
        applyTenderDetailPrefill({ basicForm, detailForm }, buildProjectPrefillFromTender(result.data))
      }
    } catch (error) {
      if (!hasGlobalHttpErrorMessage(error)) {
        ElMessage.warning(error?.message || '标讯信息带入失败，可继续手工填写')
      }
    }
  }

  async function loadAvailableTenders() {
    try {
      const tenderResult = await tendersApi.getList()
      if (tenderResult?.success) {
        availableTenders.value = Array.isArray(tenderResult.data) ? tenderResult.data : []
      }
    } catch (error) {
      availableTenders.value = []
      if (!hasGlobalHttpErrorMessage(error)) {
        ElMessage.warning('标讯列表加载失败，可继续手动填写项目信息')
      }
    }

    if (/^\d+$/.test(String(route.query.tenderId || ''))) {
      selectedTenderId.value = Number(route.query.tenderId)
    } else if (availableTenders.value.length > 0) {
      selectedTenderId.value = Number(availableTenders.value[0].id)
    }
  }

  function applyCrmData(payload) {
    if (!payload || typeof payload !== 'object') return
    if (payload.name != null) basicForm.name = payload.name
    if (payload.customer != null) basicForm.customer = payload.customer
    if (payload.budget != null) basicForm.budget = payload.budget
    if (payload.industry != null) basicForm.industry = payload.industry
    if (payload.region != null) basicForm.region = payload.region
  }

  async function loadProjectData(id) {
    try {
      await projectStore.getProjects()
      const project = projectStore.projects.find((p) => p.id === id)

      if (project) {
        basicForm.name = project.name || ''
        basicForm.customer = project.customer || ''
        basicForm.budget = project.budget || null
        basicForm.industry = project.industry || ''
        basicForm.region = project.region || ''
        basicForm.platform = project.platform || ''
        basicForm.deadline = project.deadline || ''
        basicForm.manager = project.manager || ''
        basicForm.competitors = project.competitors || []

        detailForm.description = project.description || ''
        detailForm.tags = project.tags || []
        detailForm.startDate = project.startDate || ''
        detailForm.endDate = project.endDate || ''
        detailForm.remark = project.remark || ''

        // CO-601: 自定义字段按 scope 摊平回显（预置 key 以 DTO 权威值为准，撞 key 脏数据忽略）
        mergeCustomFieldsAll(basicForm, detailForm, project.customFields)

        if (project.tasks && project.tasks.length > 0) {
          taskForm.tasks = project.tasks
        }

        ElMessage.success('项目数据加载成功')
      } else {
        ElMessage.error('未找到该项目')
        router.push('/project')
      }
    } catch (error) {
      ElMessage.error('加载项目数据失败')
    }
  }

  function resolveApiTenderId() {
    const routeTenderId = route.query.tenderId
    if (routeTenderId && /^\d+$/.test(String(routeTenderId))) {
      return Number(routeTenderId)
    }
    return selectedTenderId.value || Number(availableTenders.value[0]?.id || 0) || null
  }

  function buildApiProjectPayload() {
    const managerId = Number(userStore.currentUser?.id || 0)
    const tenderId = resolveApiTenderId()
    const startDate = formatDateTime(detailForm.startDate || new Date().toISOString().slice(0, 10))
    const endDate = formatDateTime(detailForm.endDate || basicForm.deadline, '23:59:59')

    if (!managerId) {
      throw new Error('当前登录用户无有效ID，无法创建项目')
    }
    if (!tenderId) {
      throw new Error('当前没有可关联的真实标讯，请先从标讯中心进入或确认 demo tenders 已加载')
    }
    if (!endDate) {
      throw new Error('请填写投标截止日期或预计完工日期')
    }

    // CO-601: 按 scope 收集自定义字段（schema 减预置清单）；无自定义值时省略该键
    const customFields = collectCustomFieldsAll(basicForm, detailForm)

    return {
      name: basicForm.name,
      tenderId,
      managerId,
      teamMembers: [managerId],
      startDate,
      endDate,
      customer: basicForm.customer,
      budget: basicForm.budget,
      industry: basicForm.industry,
      region: basicForm.region,
      platform: basicForm.platform,
      deadline: basicForm.deadline || null,
      description: detailForm.description,
      remark: detailForm.remark,
      tagsJson: JSON.stringify(detailForm.tags || []),
      status: 'INITIATED',
      projectLeaderName: detailForm.projectLeaderName || null,
      leaderDepartment: detailForm.leaderDepartment || null,
      sourceModule: sourceInfo.module || '',
      sourceCustomerId: sourceInfo.customerId || '',
      sourceCustomer: sourceInfo.customerName || '',
      sourceOpportunityId: sourceInfo.opportunityId || '',
      sourceReasoningSummary: sourceInfo.reasoningSummary || '',
      ...(Object.keys(customFields).length > 0 ? { customFields } : {})
    }
  }

  function buildTaskCreatePayloads() {
    return buildTaskCreatePayloadsFromRows(taskForm.tasks)
  }

  return {
    basicForm,
    detailForm,
    taskForm,
    sourceInfo,
    competitorAnalysis,
    availableTenders,
    selectedTenderId,
    isEditMode,
    editProjectId,
    setCustomFieldsSchema,
    addTask,
    removeTask,
    handleCompetitorsChange,
    applyOpportunityPrefill,
    applyCrmData,
    loadTenderDetailPrefill,
    loadAvailableTenders,
    loadProjectData,
    resolveApiTenderId,
    buildApiProjectPayload,
    buildTaskCreatePayloads
  }
}
