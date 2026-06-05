import { ref, computed } from 'vue'
import { useProjectStore } from '@/stores/project'
import { useProjectPagination } from './useProjectPagination.js'

/**
 * Composable for project list filtering and pagination.
 * Extracted from List.vue to keep template under line budget.
 */
export function useProjectFilter(searchForm) {
  const projectStore = useProjectStore()
  const loading = ref(false)
  const error = ref(null)

  const matchedProjects = computed(() => {
    const f = searchForm.value
    return (projectStore.projects || []).filter((p) => {
      if (f.name && !(p.name || '').includes(f.name) && !(p.projectName || '').includes(f.name)) return false
      if (f.ownerUnit && !(p.ownerUnit || '').includes(f.ownerUnit)) return false
      if (f.projectType && p.projectType !== f.projectType) return false
      if (f.customerType && p.customerType !== f.customerType) return false
      if (f.sourceModule && p.sourceModule !== f.sourceModule) return false
      if (f.bidStatus && p.bidStatus !== f.bidStatus) return false
      if (f.stage && p.stage !== f.stage) return false
      if (f.projectLeaderName && !(p.projectLeaderName || '').includes(f.projectLeaderName)) return false
      if (f.biddingLeaderName && !(p.biddingLeaderName || '').includes(f.biddingLeaderName)) return false
      if (f.leaderDepartment && p.leaderDepartment !== f.leaderDepartment) return false
      if (f.region && !(p.region || '').includes(f.region)) return false
      if (f.biddingPlatform && !(p.biddingPlatform || '').includes(f.biddingPlatform)) return false
      if (f.bidMonth && p.bidMonth !== f.bidMonth) return false
      if (f.priority && p.priority !== f.priority) return false
      if (f.shortlistedCountMin != null && (p.shortlistedCount == null || p.shortlistedCount < f.shortlistedCountMin)) return false
      if (f.shortlistedCountMax != null && (p.shortlistedCount == null || p.shortlistedCount > f.shortlistedCountMax)) return false
      if (f.revenueMin != null) {
        const rev = Number(p.budget || 0)
        if (rev < f.revenueMin) return false
      }
      if (f.revenueMax != null) {
        const rev = Number(p.budget || 0)
        if (rev > f.revenueMax) return false
      }
      if (f.bidOpenTimeRange && f.bidOpenTimeRange.length === 2) {
        const bt = p.bidOpenTime ? new Date(p.bidOpenTime) : null
        if (bt) {
          const end = new Date(f.bidOpenTimeRange[1])
          end.setHours(23, 59, 59)
          if (bt < new Date(f.bidOpenTimeRange[0]) || bt > end) return false
        }
      }
      if (f.createTimeRange && f.createTimeRange.length === 2) {
        const ct = p.createdAt ? new Date(p.createdAt) : null
        if (ct) {
          const end = new Date(f.createTimeRange[1])
          end.setHours(23, 59, 59)
          if (ct < new Date(f.createTimeRange[0]) || ct > end) return false
        }
      }
      return true
    })
  })

  const { pagination, filteredProjects, handleSizeChange, handlePageChange, resetPage } =
    useProjectPagination(matchedProjects)

  return {
    loading,
    error,
    matchedProjects,
    pagination,
    filteredProjects,
    handleSizeChange,
    handlePageChange,
    resetPage,
  }
}
