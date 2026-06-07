import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { projectLifecycleApi } from '@/api/modules/projectLifecycle.js'

/**
 * Composable for project export functionality.
 * @param {Ref} searchForm - Search form ref for export parameters
 * @param {Object} userStore - User store with token
 */
export function useProjectExport(searchForm, userStore) {
  const exporting = ref(false)

  const handleExport = async () => {
    exporting.value = true
    try {
      const params = {}
      if (searchForm.value.name) params.name = searchForm.value.name
      if (searchForm.value.ownerUnit) params.ownerUnit = searchForm.value.ownerUnit
      if (searchForm.value.projectType) params.projectType = searchForm.value.projectType
      if (searchForm.value.customerType) params.customerType = searchForm.value.customerType
      if (searchForm.value.priority) params.priority = searchForm.value.priority
      if (searchForm.value.sourceModule) params.sourceModule = searchForm.value.sourceModule
      if (searchForm.value.bidStatus) params.bidStatus = searchForm.value.bidStatus
      if (searchForm.value.stage) params.stage = searchForm.value.stage
      if (searchForm.value.projectLeaderName) params.projectLeaderName = searchForm.value.projectLeaderName
      if (searchForm.value.biddingLeaderName) params.biddingLeaderName = searchForm.value.biddingLeaderName
      if (searchForm.value.leaderDepartment) params.leaderDepartment = searchForm.value.leaderDepartment
      if (searchForm.value.region) params.region = searchForm.value.region
      if (searchForm.value.biddingPlatform) params.biddingPlatform = searchForm.value.biddingPlatform
      if (searchForm.value.bidMonth) params.bidMonth = searchForm.value.bidMonth
      if (searchForm.value.shortlistedCountMin != null) params.shortlistedCountMin = searchForm.value.shortlistedCountMin
      if (searchForm.value.shortlistedCountMax != null) params.shortlistedCountMax = searchForm.value.shortlistedCountMax
      if (searchForm.value.revenueMin != null) params.revenueMin = searchForm.value.revenueMin
      if (searchForm.value.revenueMax != null) params.revenueMax = searchForm.value.revenueMax
      if (searchForm.value.bidOpenTimeRange?.length === 2) {
        params.bidOpenTimeStart = searchForm.value.bidOpenTimeRange[0]
        params.bidOpenTimeEnd = searchForm.value.bidOpenTimeRange[1]
      }
      if (searchForm.value.createTimeRange?.length === 2) {
        params.createTimeStart = searchForm.value.createTimeRange[0]
        params.createTimeEnd = searchForm.value.createTimeRange[1]
      }

      const resp = await projectLifecycleApi.exportList(params)
      const blob = await resp.data
      const safeTimestamp = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19)
      const filename = `投标项目列表_${safeTimestamp}.xlsx`
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = filename
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
      URL.revokeObjectURL(url)
      ElMessage.success('导出成功')
    } catch (e) {
      if (e?.name === 'AbortError') {
        ElMessage.warning('导出超时，请减小筛选范围后重试')
      } else {
        ElMessage.error('导出失败：' + (e?.message || '未知错误'))
      }
    } finally {
      exporting.value = false
    }
  }

  return { exporting, handleExport }
}
