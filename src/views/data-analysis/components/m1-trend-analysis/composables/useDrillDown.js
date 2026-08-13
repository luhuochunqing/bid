import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { dashboardApi } from '@/api'
import { notifyErrorUnlessRateLimit } from '@/api/error-utils.js'
import { navigateToProject } from '@/utils/projectNavigation.js'

export function useDrillDown() {
  const router = useRouter()

  const drillVisible = ref(false)
  const drillLoading = ref(false)
  const drillTitle = ref('')
  const drillItems = ref([])
  const drillSummary = ref(null)
  const drillPagination = ref({ page: 1, size: 10, total: 0, totalPages: 0 })

  const openDrill = async (data, xAxisType) => {
    drillVisible.value = true
    drillLoading.value = true
    drillTitle.value = `${data?.label || ''} - 明细数据`

    try {
      const drillParams = {
        dimension: xAxisType,
        key: data?.key || data?.value || data?.label || '',
        page: 1, size: 10
      }
      const response = await dashboardApi.getDrillDown('trends', drillParams)
      if (!response?.success) throw new Error(response?.msg || '加载明细数据失败')

      const result = response.data || {}
      drillItems.value = Array.isArray(result.items) ? result.items : []
      drillSummary.value = result.summary || null
      drillPagination.value = {
        page: result.pagination?.page || 1,
        size: result.pagination?.size || 10,
        total: result.pagination?.total || 0,
        totalPages: result.pagination?.totalPages || 0
      }
    } catch (error) {
      drillItems.value = []
      drillSummary.value = null
      notifyErrorUnlessRateLimit(error, '明细数据加载失败')
    } finally {
      drillLoading.value = false
    }
  }

  const changePage = async (page) => {
    drillLoading.value = true
    try {
      const drillParams = { dimension: 'time', page, size: 10 }
      const response = await dashboardApi.getDrillDown('trends', drillParams)
      if (!response?.success) throw new Error(response?.msg || '加载明细数据失败')

      const result = response.data || {}
      drillItems.value = Array.isArray(result.items) ? result.items : []
      drillPagination.value = {
        page: result.pagination?.page || page,
        size: result.pagination?.size || 10,
        total: result.pagination?.total || 0,
        totalPages: result.pagination?.totalPages || 0
      }
    } catch (error) {
      notifyErrorUnlessRateLimit(error, '明细数据加载失败')
    } finally {
      drillLoading.value = false
    }
  }

  const navigateProject = (projectId) => {
    navigateToProject(router, projectId)
  }

  return {
    drillVisible, drillLoading, drillTitle,
    drillItems, drillSummary, drillPagination,
    openDrill, changePage, navigateProject
  }
}