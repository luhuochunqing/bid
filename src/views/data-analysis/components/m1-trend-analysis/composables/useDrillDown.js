import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { dashboardApi } from '@/api'
import { notifyErrorUnlessRateLimit } from '@/api/error-utils.js'
import { navigateToProject } from '@/utils/projectNavigation.js'
import { X_AXIS_LABELS } from '../filterConstants.js'

export function useDrillDown() {
  const router = useRouter()

  const drillVisible = ref(false)
  const drillLoading = ref(false)
  const drillTitle = ref('')
  const drillItems = ref([])
  const drillSummary = ref(null)
  const drillPagination = ref({ page: 1, size: 10, total: 0, totalPages: 0 })

  // 保存当前下钻上下文（用于分页查询）
  const drillContext = ref(null)

  const buildTitle = (xAxisType, axisValue, seriesName, total) => {
    const dimLabel = X_AXIS_LABELS[xAxisType] || xAxisType || '时间'
    const countSuffix = total != null ? `（共 ${total} 项）` : ''
    return `${dimLabel}「${axisValue || ''}」${seriesName || ''}明细${countSuffix}`
  }

  const buildDrillParams = (ctx, page) => {
    const f = ctx?.filters || {}
    return {
      dimension: ctx?.xAxisType || 'time',
      key: ctx?.axisValue || '',
      seriesName: ctx?.seriesName || '',
      page,
      size: 10,
      ...(f.departments?.length ? { departmentIds: f.departments.join(',') } : {}),
      ...(f.persons?.length ? { userIds: f.persons.join(',') } : {}),
      ...(f.regions?.length ? { regionIds: f.regions.join(',') } : {}),
      ...(f.customerTypes?.length ? { customerTypes: f.customerTypes.join(',') } : {}),
      ...(f.projectTypes?.length ? { projectTypes: f.projectTypes.join(',') } : {}),
      ...(f.projectStatuses?.length ? { statuses: f.projectStatuses.join(',') } : {}),
      ...(f.tenderSubjects?.length ? { tenderEntities: f.tenderSubjects.join(',') } : {}),
      ...(f.competitors?.length ? { competitorNames: f.competitors.join(',') } : {})
    }
  }

  const openDrill = async (data, xAxisType, seriesName, filters) => {
    drillVisible.value = true
    drillLoading.value = true
    const axisValue = data?.label || data?.key || data?.value || ''
    drillTitle.value = buildTitle(xAxisType, axisValue, seriesName, null)

    drillContext.value = { xAxisType, axisValue, seriesName, filters }

    try {
      const drillParams = buildDrillParams(drillContext.value, 1)
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
      // PRD 6.6 标题含总数
      drillTitle.value = buildTitle(xAxisType, axisValue, seriesName, drillPagination.value.total)
    } catch (error) {
      drillItems.value = []
      drillSummary.value = null
      notifyErrorUnlessRateLimit(error, '明细数据加载失败')
    } finally {
      drillLoading.value = false
    }
  }

  const changePage = async (page) => {
    if (!drillContext.value) return
    drillLoading.value = true
    try {
      const drillParams = buildDrillParams(drillContext.value, page)
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
