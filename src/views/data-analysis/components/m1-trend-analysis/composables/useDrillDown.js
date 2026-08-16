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
    // 后端 List<String> 参数：依赖 src/api/client.js 的 paramsSerializer（indexes: null）
    // 将数组序列化为 repeated query params（?key=a&key=b），Spring 才能正确绑定
    const toList = (arr) => (Array.isArray(arr) && arr.length ? arr : undefined)
    return {
      dimension: ctx?.xAxisType || 'time',
      axisValue: ctx?.axisValue || '',
      seriesName: ctx?.seriesName || '',
      page,
      size: 10,
      ...(ctx?.startDate ? { startDate: ctx.startDate } : {}),
      ...(ctx?.endDate ? { endDate: ctx.endDate } : {}),
      ...(toList(f.departments) ? { departments: toList(f.departments) } : {}),
      ...(toList(f.persons) ? { persons: toList(f.persons) } : {}),
      ...(toList(f.regions) ? { regions: toList(f.regions) } : {}),
      ...(toList(f.customerTypes) ? { customerTypes: toList(f.customerTypes) } : {}),
      ...(toList(f.projectTypes) ? { projectTypes: toList(f.projectTypes) } : {}),
      ...(toList(f.projectStatuses) ? { statuses: toList(f.projectStatuses) } : {}),
      ...(toList(f.tenderSubjects) ? { tenderEntities: toList(f.tenderSubjects) } : {}),
      ...(toList(f.competitors) ? { competitorNames: toList(f.competitors) } : {})
    }
  }

  const openDrill = async (data, xAxisType, seriesName, filters, dateRange) => {
    drillVisible.value = true
    drillLoading.value = true
    const axisValue = data?.label || data?.key || data?.value || ''
    drillTitle.value = buildTitle(xAxisType, axisValue, seriesName, null)

    // 日期格式化
    const fmt = (d) => {
      if (!d) return null
      if (typeof d === 'string') return d.slice(0, 10)
      const dt = new Date(d)
      return `${dt.getFullYear()}-${String(dt.getMonth() + 1).padStart(2, '0')}-${String(dt.getDate()).padStart(2, '0')}`
    }
    drillContext.value = {
      xAxisType, axisValue, seriesName, filters,
      startDate: fmt(dateRange?.[0]),
      endDate: fmt(dateRange?.[1])
    }

    try {
      const drillParams = buildDrillParams(drillContext.value, 1)
      const response = await dashboardApi.getTrendDrillDown(drillParams)
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
      const response = await dashboardApi.getTrendDrillDown(drillParams)
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
