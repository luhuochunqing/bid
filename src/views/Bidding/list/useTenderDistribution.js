// Input: batch tender API, selected tenders, permissions, and refresh callback
// Output: distribution and assignment dialog state/actions
// Pos: src/views/Bidding/list/ - Tender distribution composable
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { buildDistributionPreview, normalizeAssignmentCandidate } from './helpers.js'

function createDistributeForm() {
  return { type: 'auto', rule: 'average', assignees: [], deadline: null, remark: '' }
}

function createActiveTender() {
  return { id: null, title: '' }
}

export function useTenderDistribution({
  batchTendersApi,
  selectedTenders,
  selectSingleTender,
  clearSelection,
  refreshTenderList,
  showBatchOperationFeedback,
  canManageTenders,
}) {
  const showDistributeDialog = ref(false)
  const showAssignDialog = ref(false)
  const distributeLoading = ref(false)
  const assignLoading = ref(false)
  const loadingCandidates = ref(false)
  const candidates = ref([])
  const distributeForm = ref(createDistributeForm())
  const activeTender = ref(createActiveTender())

  const distributionPreview = computed(() => buildDistributionPreview({
    tenders: selectedTenders.value,
    candidates: candidates.value,
    form: distributeForm.value,
  }))

  const ensureCandidatesLoaded = async () => {
    if (candidates.value.length > 0 || loadingCandidates.value) return
    loadingCandidates.value = true
    try {
      const response = await batchTendersApi.getAssignmentCandidates()
      candidates.value = (response?.data || [])
        .map(normalizeAssignmentCandidate)
        .filter((item) => Number.isFinite(item.id))
    } catch {
      ElMessage.error('加载指派候选人失败')
    } finally {
      loadingCandidates.value = false
    }
  }

  const openDistributeDialog = async () => {
    if (!canManageTenders.value) return ElMessage.error('当前账号无权分发标讯')
    if (selectedTenders.value.length === 0) return ElMessage.warning('请先选择要分发的标讯')
    showDistributeDialog.value = true
    await ensureCandidatesLoaded()
  }

  const openSingleDistribute = async (row) => {
    selectSingleTender(row)
    await openDistributeDialog()
  }

  const openAssignDialog = async (row) => {
    if (!canManageTenders.value) return ElMessage.error('当前账号无权指派标讯')
    activeTender.value = { id: row.id, title: row.title }
    showAssignDialog.value = true
    await ensureCandidatesLoaded()
  }

  const resetDistributeForm = () => {
    distributeForm.value = createDistributeForm()
  }

  const resetAssignForm = () => {
    activeTender.value = createActiveTender()
  }

  const handleDistribute = async () => {
    if (distributionPreview.value.length === 0) return ElMessage.warning('请选择有效分发对象')
    distributeLoading.value = true
    try {
      const results = await Promise.all(distributionPreview.value.map((group) =>
        batchTendersApi.batchAssign(group.tenders.map((tender) => tender.id), group.id, distributeForm.value.remark),
      ))
      // TODO: 分发成功记录待后端 API 补全后实现持久化 (CO-221)
      const successCount = results.reduce((sum, item) => sum + Number(item?.data?.successCount || 0), 0)
      const failureCount = results.reduce((sum, item) => sum + Number(item?.data?.failureCount || 0), 0)
      if (failureCount === 0) ElMessage.success(`成功分发 ${successCount} 条标讯`)
      else if (successCount > 0) ElMessage.warning(`分发部分成功：${successCount} 条成功，${failureCount} 条失败`)
      else ElMessage.error('分发失败，请重试')
      if (successCount > 0) {
        showDistributeDialog.value = false
        clearSelection()
        await refreshTenderList()
      }
    } catch {
      ElMessage.error('分发失败，请重试')
    } finally {
      distributeLoading.value = false
    }
  }

  const handleAssign = async (payload) => {
    if (!payload?.assignee) return ElMessage.warning('请选择指派人员')
    assignLoading.value = true
    try {
      const result = await batchTendersApi.batchAssign(
        [payload.tenderId],
        payload.assignee,
        payload.remark,
      )
      showBatchOperationFeedback(result, '指派成功')
      if (result.success || result.partialSuccess) {
        showAssignDialog.value = false
        await refreshTenderList()
      }
    } catch {
      ElMessage.error('指派失败，请重试')
    } finally {
      assignLoading.value = false
    }
  }

  return {
    showDistributeDialog,
    showAssignDialog,
    distributeLoading,
    assignLoading,
    loadingCandidates,
    candidates,
    distributeForm,
    activeTender,
    distributionPreview,
    openDistributeDialog,
    openSingleDistribute,
    openAssignDialog,
    resetDistributeForm,
    resetAssignForm,
    handleDistribute,
    handleAssign,
  }
}
