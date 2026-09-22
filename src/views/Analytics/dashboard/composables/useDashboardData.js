import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  dashboardApi,
  isFeatureUnavailableResponse,
  getFeaturePlaceholder
} from '@/api'
import { notifyFeatureUnavailable } from '@/utils/featureFeedback'

function buildEmptyDashboardData() {
  return {
    totalBids: 0,
    totalBidsChange: '--',
    inProgress: 0,
    wonThisYear: 0,
    winRate: 0,
    winRateChange: '--',
    totalAmount: 0,
    totalAmountChange: '--',
    totalCost: 0,
    totalCostChange: '--',
    trendData: [],
    competitors: [],
    productLines: [],
    regionData: [],
    statusDistribution: {},
    backendSummary: { activeProjects: 0, pendingTasks: 0 }
  }
}

export function useDashboardData() {
  const loading = ref(true)
  const dashboardData = ref(null)
  const pageFeaturePlaceholders = ref({})

  async function loadData() {
    loading.value = true
    pageFeaturePlaceholders.value = {}

    try {
      const response = await dashboardApi.getOverview()
      if (!response?.success) {
        throw new Error(response?.msg || '加载数据失败')
      }
      const nextData = { ...buildEmptyDashboardData(), ...(response.data || {}) }

      const productLinesResponse = await dashboardApi.getProductLines()
      if (productLinesResponse?.success) {
        nextData.productLines = Array.isArray(productLinesResponse.data) ? productLinesResponse.data : []
      } else if (isFeatureUnavailableResponse(productLinesResponse)) {
        nextData.productLines = []
        dashboardData.value = nextData
        const placeholder = notifyFeatureUnavailable(productLinesResponse, {
          fallback: {
            title: '产品线分析当前不可用',
            hint: '其余指标仍基于真实后端数据加载，可稍后重试或联系管理员检查分析服务。'
          },
          level: 'warning'
        })
        pageFeaturePlaceholders.value = {
          ...pageFeaturePlaceholders.value,
          productLines: placeholder || getFeaturePlaceholder(productLinesResponse)
        }
        return
      } else if (productLinesResponse?.message) {
        ElMessage.warning(productLinesResponse.message)
        nextData.productLines = []
      }

      dashboardData.value = nextData
    } catch (error) {
      dashboardData.value = buildEmptyDashboardData()
      ElMessage.error(error?.message || '加载数据失败')
    } finally {
      loading.value = false
    }
  }

  return {
    loading,
    dashboardData,
    pageFeaturePlaceholders,
    loadData
  }
}
