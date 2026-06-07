import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { performanceApi } from '@/api/modules/performance.js'

export function usePerformanceSimilarSearch(searchForm) {
  const similarVisible = ref(false)
  const similarRecords = ref([])
  const similarLoading = ref(false)

  const openSimilarSearch = async () => {
    similarLoading.value = true
    similarVisible.value = true
    try {
      const similarForm = { ...searchForm, keyword: '' }
      const { data } = await performanceApi.getList(similarForm)
      const scored = (data || []).map(r => {
        let score = 0
        if (searchForm.customerTypes?.length > 0 && searchForm.customerTypes.includes(r.customerType)) score += 3
        if (searchForm.projectTypes?.length > 0 && searchForm.projectTypes.includes(r.projectType)) score += 2
        if (searchForm.customerLevels?.length > 0 && searchForm.customerLevels.includes(r.customerLevel)) score += 1
        if (searchForm.territory && r.territory?.includes(searchForm.territory)) score += 2
        return { ...r, _similarScore: score }
      })
      similarRecords.value = scored.sort((a, b) => b._similarScore - a._similarScore).slice(0, 20)
    } catch {
      ElMessage.error('相似业绩搜索失败')
    } finally {
      similarLoading.value = false
    }
  }

  return {
    similarVisible,
    similarRecords,
    similarLoading,
    openSimilarSearch
  }
}
