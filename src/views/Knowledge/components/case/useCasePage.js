import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { bidResultsApi, getFeaturePlaceholder, knowledgeApi, projectsApi } from '@/api'
import { notifyFeatureUnavailable } from '@/utils/featureFeedback'
import {
  buildCaseListQuery,
  caseAmountRanges,
  caseCommonTags,
  caseIndustryOptions,
  createCaseFormRules,
  createEmptyCaseForm,
  getCaseYearOptions
} from './caseMeta.js'

export function useCasePage() {
  const router = useRouter()

  const searchForm = reactive({
    keyword: '',
    industry: '',
    year: '',
    amount: ''
  })

  const selectedTags = ref([])

  const pagination = reactive({
    page: 1,
    pageSize: 12,
    total: 0
  })

  const loading = ref(false)
  const cases = ref([])
  const featurePlaceholder = ref(null)

  const addCaseDialogVisible = ref(false)
  const addCaseTab = ref('fromProject')
  const saving = ref(false)
  const projectOptionsLoading = ref(false)
  const sourceProjectOptions = ref([])

  const caseForm = reactive(createEmptyCaseForm())
  const manualCaseForm = reactive(createEmptyCaseForm())
  const caseFormRules = createCaseFormRules()

  const years = computed(() => getCaseYearOptions())

  const loadCases = async () => {
    loading.value = true
    try {
      const result = await knowledgeApi.cases.getList(buildCaseListQuery(searchForm, selectedTags.value, pagination))

      if (result?.success) {
        cases.value = Array.isArray(result.data) ? result.data : []
        pagination.total = Number(result.total ?? result.data?.length ?? 0)
        featurePlaceholder.value = null
        return
      }

      cases.value = []
      pagination.total = 0
      featurePlaceholder.value = notifyFeatureUnavailable(result, {
        fallback: {
          title: '案例库当前不可用',
          hint: '当前无法加载案例列表，请稍后重试或联系管理员检查知识库服务.'
        }
      })

      if (!featurePlaceholder.value && result?.msg) {
        ElMessage.error(result.msg)
      }
    } catch (error) {
      cases.value = []
      pagination.total = 0
      featurePlaceholder.value = getFeaturePlaceholder('cases')
      ElMessage.error(error?.message || '加载案例列表失败')
    } finally {
      loading.value = false
    }
  }

  const loadSourceProjectOptions = async () => {
    projectOptionsLoading.value = true
    try {
      const result = await bidResultsApi.getFetchResults()
      if (!result?.success || !Array.isArray(result.data)) {
        sourceProjectOptions.value = []
        return
      }

      sourceProjectOptions.value = result.data
        .filter(item => String(item.result || '').toLowerCase() === 'won')
        .map(item => ({
          id: item.id,
          projectId: item.projectId,
          projectName: item.projectName || '未命名项目',
          amount: Number(item.amount || 0),
          source: item.source || '',
          status: item.status || ''
        }))
    } catch (error) {
      sourceProjectOptions.value = []
    } finally {
      projectOptionsLoading.value = false
    }
  }

  const resetCaseForm = () => {
    Object.assign(caseForm, createEmptyCaseForm())
    Object.assign(manualCaseForm, createEmptyCaseForm())
    addCaseTab.value = 'fromProject'
  }

  const fillProjectCaseForm = async (projectFetchId) => {
    const option = sourceProjectOptions.value.find(item => String(item.id) === String(projectFetchId))
    if (!option) return

    caseForm.sourceProjectId = option.id
    caseForm.title = `${option.projectName} - 成功案例`
    caseForm.amount = Number(option.amount || 0)
    caseForm.description = option.projectName ? `${option.projectName} 项目成功沉淀` : caseForm.description
    caseForm.techHighlights = option.projectName ? `${option.projectName} 的实施经验\n${option.source || '来自投标结果同步'}`
      : caseForm.techHighlights

    if (!option.projectId) return

    try {
      const projectResult = await projectsApi.getDetail(option.projectId)
      if (!projectResult?.success || !projectResult?.data) {
        caseForm.customer = caseForm.customer || option.projectName
        return
      }

      const project = projectResult.data
      caseForm.customer = project.sourceCustomer || caseForm.customer || option.projectName || ''
      caseForm.description = project.sourceReasoningSummary || caseForm.description
      if (!caseForm.techHighlights && project.sourceReasoningSummary) {
        caseForm.techHighlights = project.sourceReasoningSummary
      }
    } catch (error) {
      caseForm.customer = caseForm.customer || option.projectName
      console.warn('Failed to load source project detail:', error)
    }
  }

  const toggleTag = (tag) => {
    const index = selectedTags.value.indexOf(tag)
    if (index > -1) {
      selectedTags.value.splice(index, 1)
    } else {
      selectedTags.value.push(tag)
    }
    pagination.page = 1
    loadCases()
  }

  const handleSearch = async () => {
    pagination.page = 1
    await loadCases()
  }

  const handleReset = async () => {
    searchForm.keyword = ''
    searchForm.industry = ''
    searchForm.year = ''
    searchForm.amount = ''
    selectedTags.value = []
    pagination.page = 1
    await loadCases()
  }

  const handlePageChange = async (page) => {
    pagination.page = page
    await loadCases()
  }

  const handleSizeChange = async (size) => {
    pagination.pageSize = size
    pagination.page = 1
    await loadCases()
  }

  const handleAdd = async () => {
    resetCaseForm()
    addCaseDialogVisible.value = true
    if (sourceProjectOptions.value.length === 0 && !projectOptionsLoading.value) {
      await loadSourceProjectOptions()
    }
  }

  const handleView = (item) => {
    router.push({ path: '/knowledge/case/detail', query: { id: item.id } })
  }

  const handleSaveCase = async (activeTab) => {
    const formData = activeTab === 'fromProject' ? caseForm : manualCaseForm

    saving.value = true
    try {
      const result = await knowledgeApi.cases.create({
        ...formData,
        highlights: String(formData.techHighlights || '')
          .split('\n')
          .map(item => item.trim())
          .filter(Boolean)
      })

      if (!result?.success) {
        throw new Error(result?.msg || '保存失败')
      }

      ElMessage.success('案例保存成功')
      addCaseDialogVisible.value = false
      resetCaseForm()
      pagination.page = 1
      await loadCases()
    } catch (error) {
      ElMessage.error(error?.message || '保存失败，请重试')
    } finally {
      saving.value = false
    }
  }

  onMounted(async () => {
    await Promise.all([loadCases(), loadSourceProjectOptions()])
  })

  return {
    addCaseDialogVisible,
    addCaseTab,
    amountRanges: caseAmountRanges,
    caseForm,
    caseFormRules,
    cases,
    commonTags: caseCommonTags,
    featurePlaceholder,
    handleAdd,
    handlePageChange,
    handleReset,
    handleSaveCase,
    handleSearch,
    handleSizeChange,
    handleView,
    industries: caseIndustryOptions,
    loading,
    manualCaseForm,
    pagination,
    projectOptionsLoading,
    resetCaseForm,
    saving,
    searchForm,
    selectedTags,
    sourceProjectOptions,
    toggleTag,
    years,
    fillProjectCaseForm
  }
}
