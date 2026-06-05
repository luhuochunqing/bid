import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { projectsApi } from '@/api/modules/projects.js'
import { customerOpportunityApi } from '@/api/modules/customerOpportunity.js'
import {
  buildBoardSummaries,
  buildCategoryStats,
  buildDrawerStats,
  buildOpportunityProjectPayload,
  confidenceColor,
  getOpportunityStatusLabel,
  getOpportunityStatusType,
  getScoreClass,
  getScoreColor,
  normalizeConfidence,
} from './customerOpportunityCenter.helpers.js'

export function useCustomerOpportunityCenter() {
  const router = useRouter()
  const userStore = useUserStore()

  const loading = ref(true)
  const isScanning = ref(false)
  const isConverting = ref(false)
  const customerOpportunityDemoEnabled = ref(true)
  const historyDrawer = ref(false)
  const activeCustomerId = ref('')
  const filters = ref({ status: '', keyword: '', sales: '', region: '', industry: '' })

  const customerInsights = ref([])
  const customerPurchases = ref([])
  const customerPredictions = ref([])

  const salesUsers = computed(() => {
    const unique = new Map()
    customerInsights.value.forEach((item) => {
      if (!item?.salesRep) {
        return
      }
      unique.set(item.salesRep, { id: item.salesRep, name: item.salesRep })
    })
    return [...unique.values()]
  })

  const regions = computed(() => [...new Set(customerInsights.value.map((customer) => customer.region))].filter(Boolean))
  const industries = computed(() => [...new Set(customerInsights.value.map((customer) => customer.industry))].filter(Boolean))

  const filteredCustomers = computed(() => customerInsights.value.filter((customer) => {
    if (filters.value.status && customer.status !== filters.value.status) {
      return false
    }
    if (filters.value.keyword && !customer.customerName.toLowerCase().includes(filters.value.keyword.toLowerCase())) {
      return false
    }
    if (filters.value.sales && customer.salesRep !== filters.value.sales) {
      return false
    }
    if (filters.value.region && customer.region !== filters.value.region) {
      return false
    }
    if (filters.value.industry && customer.industry !== filters.value.industry) {
      return false
    }
    return true
  }))

  const selectedCustomer = computed(() => {
    const baseCustomer = customerInsights.value.find((item) => item.customerId === activeCustomerId.value)
    if (!baseCustomer) {
      return null
    }

    const purchaseHistory = customerPurchases.value
      .filter((item) => item.customerId === baseCustomer.customerId)
      .sort((a, b) => new Date(b.publishDate) - new Date(a.publishDate))
    const prediction = customerPredictions.value.find((item) => item.customerId === baseCustomer.customerId)

    return {
      ...baseCustomer,
      purchaseHistory,
      prediction: prediction || {
        opportunityId: '',
        suggestedProjectName: '待智能研判',
        predictedCategory: '---',
        predictedBudgetMin: 0,
        predictedBudgetMax: 0,
        predictedWindow: '待判断',
        confidence: 0,
        reasoningSummary: '当前数据不足，暂无法生成高置信度预测。',
        evidenceRecords: [],
        convertedProjectId: '',
      },
      predictionSummary: prediction?.reasoningSummary || '当前数据不足，暂无法生成高置信度预测。',
    }
  })

  const customerHistory = computed(() => {
    if (!selectedCustomer.value) {
      return []
    }
    return selectedCustomer.value.purchaseHistory
  })

  const drawerStats = computed(() => buildDrawerStats(customerHistory.value))
  const categoryStats = computed(() => buildCategoryStats(customerHistory.value))
  const boardSummaries = computed(() => buildBoardSummaries({
    customerInsights: customerInsights.value,
    customerPredictions: customerPredictions.value,
    demoEnabled: customerOpportunityDemoEnabled.value,
  }))

  const rowClass = ({ row }) => (row.customerId === activeCustomerId.value ? 'row-active' : '')

  const loadInsights = async (params = {}) => {
    loading.value = true
    try {
      const response = await customerOpportunityApi.getCustomerInsights(params)
      customerInsights.value = response.data || []
      return response.data || []
    } finally {
      loading.value = false
    }
  }

  const loadCustomerDetail = async (customerId) => {
    const [purchasesResponse, predictionsResponse] = await Promise.all([
      customerOpportunityApi.getPurchaseHistory(customerId),
      customerOpportunityApi.getPredictions(customerId),
    ])

    customerPurchases.value = [
      ...customerPurchases.value.filter((item) => item.customerId !== customerId),
      ...(purchasesResponse.data || []),
    ]
    customerPredictions.value = [
      ...customerPredictions.value.filter((item) => item.customerId !== customerId),
      ...(predictionsResponse.data || []),
    ]

    return {
      purchases: purchasesResponse.data || [],
      predictions: predictionsResponse.data || [],
    }
  }

  const selectCustomer = async (row) => {
    activeCustomerId.value = row.customerId
    try {
      await loadCustomerDetail(row.customerId)
    } catch (error) {
      ElMessage.error(error?.message || '加载客户详情失败')
    }
  }

  const selectFirstHighValue = () => {
    const first = customerInsights.value.find((customer) => customer.opportunityScore >= 85)
    if (first) {
      selectCustomer(first)
    }
  }

  const refreshInsights = async () => {
    isScanning.value = true
    try {
      await customerOpportunityApi.refreshInsights()
      await loadInsights()
      if (activeCustomerId.value) {
        await loadCustomerDetail(activeCustomerId.value)
      }
      ElMessage.success('AI 智能洞察已同步至最新')
    } catch (error) {
      ElMessage.error(error?.message || '刷新洞察失败')
    } finally {
      isScanning.value = false
    }
  }

  const syncConvertedPrediction = (prediction) => {
    if (!prediction?.customerId) {
      return
    }
    customerPredictions.value = [
      ...customerPredictions.value.filter((item) => item.customerId !== prediction.customerId),
      prediction,
    ]
  }

  const convertSelectedCustomerToProject = async () => {
    if (!selectedCustomer.value) {
      return
    }

    if (selectedCustomer.value.prediction.convertedProjectId) {
      router.push(`/project/${selectedCustomer.value.prediction.convertedProjectId}`)
      return
    }

    const currentUser = userStore.currentUser || {}
    const payload = buildOpportunityProjectPayload(selectedCustomer.value, currentUser)
    isConverting.value = true

    try {
      const createResult = await projectsApi.create(payload)
      const projectId = createResult?.data?.id
      if (!projectId) {
        throw new Error('项目创建成功但未返回项目ID')
      }

      const convertResult = await customerOpportunityApi.convertToProject(
        selectedCustomer.value.prediction.opportunityId,
        projectId,
      )
      const convertedPrediction = convertResult?.data || {
        ...selectedCustomer.value.prediction,
        convertedProjectId: projectId,
      }
      syncConvertedPrediction(convertedPrediction)
      await loadInsights()
      if (activeCustomerId.value) {
        await loadCustomerDetail(activeCustomerId.value)
      }
      ElMessage.success('项目已创建并回写商机状态')
      router.push(`/project/${projectId}`)
    } catch (error) {
      ElMessage.error(error?.message || '转项目失败')
    } finally {
      isConverting.value = false
    }
  }

  const goBidding = () => {
    router.push('/bidding')
  }

  const setRecommendFilter = () => {
    filters.value.status = 'recommend'
  }

  onMounted(async () => {
    try {
      await loadInsights()
    } catch (error) {
      ElMessage.error(error?.message || '加载客户商机失败')
    }
  })

  return {
    loading,
    isScanning,
    isConverting,
    customerOpportunityDemoEnabled,
    historyDrawer,
    activeCustomerId,
    filters,
    customerInsights,
    customerPurchases,
    customerPredictions,
    salesUsers,
    regions,
    industries,
    filteredCustomers,
    selectedCustomer,
    customerHistory,
    drawerStats,
    categoryStats,
    boardSummaries,
    rowClass,
    loadInsights,
    loadCustomerDetail,
    selectCustomer,
    selectFirstHighValue,
    refreshInsights,
    convertSelectedCustomerToProject,
    goBidding,
    setRecommendFilter,
    confidenceColor,
    getOpportunityStatusLabel,
    getOpportunityStatusType,
    normalizeConfidence,
    getScoreColor,
    getScoreClass,
  }
}

export default useCustomerOpportunityCenter
