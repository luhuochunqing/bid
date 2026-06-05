// Input: canManageTenders permission flag
// Output: canShowTransfer, dialog state, and transfer action handlers
// Pos: src/views/Bidding/list/ - Tender transfer composable

import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { tendersApi } from '@/api/modules/tenders'
import { batchTendersApi } from '@/api/modules/tenders/batch.js'
import { useBiddingStore } from '@/stores/bidding'
import { normalizeAssignmentCandidate } from './helpers.js'

export function useTenderTransfer({ canManageTenders }) {
  const canShowTransfer = computed(() => canManageTenders.value)

  const transferDialog = reactive({
    visible: false,
    tender: null,
    newOwnerId: null,
    loading: false,
  })
  const transferCandidates = ref([])

  async function handleTransfer(row) {
    if (transferCandidates.value.length === 0) {
      try {
        const response = await batchTendersApi.getAssignmentCandidates()
        transferCandidates.value = (response?.data || [])
          .map(normalizeAssignmentCandidate)
          .filter((item) => Number.isFinite(item.id))
      } catch {
        ElMessage.error('加载负责人列表失败')
        return
      }
    }
    transferDialog.tender = row
    transferDialog.newOwnerId = null
    transferDialog.visible = true
  }

  async function handleTransferConfirm() {
    if (!transferDialog.newOwnerId) return ElMessage.warning('请选择目标负责人')
    transferDialog.loading = true
    try {
      const result = await tendersApi.transferTender(transferDialog.tender.id, {
        newOwnerId: transferDialog.newOwnerId,
      })
      if (result?.success !== false) {
        ElMessage.success('转派成功')
        transferDialog.visible = false
        const biddingStore = useBiddingStore()
        await biddingStore.getTenders()
      } else {
        ElMessage.error(result?.msg || '转派失败')
      }
    } catch {
      ElMessage.error('转派失败，请重试')
    } finally {
      transferDialog.loading = false
    }
  }

  return {
    canShowTransfer,
    transferDialog,
    transferCandidates,
    handleTransfer,
    handleTransferConfirm,
  }
}
