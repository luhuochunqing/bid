import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { bidMatchScoringApi, tendersApi, batchTendersApi } from '@/api'
import { useRoute, useRouter } from 'vue-router'
import {
  formatTenderDisplayField, formatTenderIndustry, getTenderDateTimeParts,
} from '../bidding-utils.js'
import { getTenderStatusTagType, getTenderStatusText } from '../bidding-utils-status.js'
import { useMatchScoreState } from './useMatchScoreState.js'
import { useTenderActions } from './useTenderActions.js'

export function useBiddingDetailPage() {
  const route = useRoute()

  // @@DATA: tender ref + match score + field formatters
  const tender = ref(null)
  const matchScore = ref(null)
  const scoreLoading = ref(false)
  const scoreError = ref('')

  const { matchScoreState, scoreEmptyText, scoreEmptyDescription } =
    useMatchScoreState(matchScore, scoreLoading, ref(false), scoreError)

  const regionMeta = computed(() => formatTenderDisplayField(tender.value?.region))
  const industryMeta = computed(() => formatTenderIndustry(tender.value?.industry))
  const deadlineParts = computed(() => getTenderDateTimeParts(tender.value?.deadline))

  const getScoreClass = (score) => score >= 90 ? 'score-excellent' : score >= 80 ? 'score-good' : 'score-normal'
  const getStatusType = (status) => getTenderStatusTagType(status)
  const getStatusText = (status) => getTenderStatusText(status)
  const getDeadlineClass = (deadline) => {
    const diffDays = Math.ceil((new Date(deadline) - new Date()) / (1000 * 60 * 60 * 24))
    if (diffDays <= 3) return 'deadline-urgent'
    if (diffDays <= 7) return 'deadline-warning'
    return ''
  }

  const loadMatchScore = async (tenderId) => {
    scoreLoading.value = true
    scoreError.value = ''
    try {
      const result = await bidMatchScoringApi.getLatestScore(tenderId)
      if (!result?.success) throw new Error(result?.msg || '获取匹配评分失败')
      matchScore.value = result.data || null
    } catch (error) {
      scoreError.value = error?.response?.data?.msg || error?.message || '获取匹配评分失败'
    } finally {
      scoreLoading.value = false
    }
  }

  // CO-258: tender deleted → NOT_FOUND state, render empty state instead of white screen
  const tenderNotFound = ref(false)

  const loadTenderDetail = async () => {
    const tenderId = route.params.id
    try {
      const result = await tendersApi.getDetail(tenderId)
      if (!result?.success) throw new Error(result?.msg || '获取标讯详情失败')
      tender.value = result.data
      tenderNotFound.value = false
      await loadMatchScore(tenderId)
    } catch (error) {
      if (error?.response?.status === 404) {
        tender.value = null
        tenderNotFound.value = true
        return
      }
      ElMessage.error(error?.message || '网络请求失败，请稍后重试')
    }
  }

  // @@ACTIONS: participate / abandon / viewOriginal
  const { handleParticipate, handleAbandon, handleViewOriginal } =
    useTenderActions(tender, loadTenderDetail)

  // @@DIALOG: assign / transfer (TODO: extract to useTenderAssignment.js)
  const showAssignDialog = ref(false)
  const showTransferDialog = ref(false)
  const assignForm = ref({ tenderTitle: '', assignee: null, priority: 'medium', remark: '' })
  const assigning = ref(false)

  const openAssign = async () => {
    assignForm.value = { tenderTitle: tender.value?.title || '', assignee: null, priority: 'medium', remark: '' }
    showAssignDialog.value = true
  }

  const doAssign = async (payload) => {
    const assignee = payload?.assignee ?? assignForm.value.assignee
    const remark = payload?.remark ?? assignForm.value.remark
    if (!assignee) { ElMessage.warning('请选择项目负责人'); return }
    assigning.value = true
    try {
      const res = await batchTendersApi.batchAssign(
        [tender.value.id], assignee, remark)
      if (res?.success) {
        ElMessage.success('分配成功')
        showAssignDialog.value = false
        await loadTenderDetail()
      } else throw new Error(res?.message || '分配失败')
    } catch (error) { ElMessage.error(error?.message || '分配失败') }
    finally { assigning.value = false }
  }

  const transferTarget = ref(null)
  const transferring = ref(false)

  const openTransfer = async () => {
    transferTarget.value = null
    showTransferDialog.value = true
  }

  const doTransfer = async () => {
    if (!transferTarget.value) { ElMessage.warning('请选择新的项目负责人'); return }
    transferring.value = true
    try {
      const res = await tendersApi.transferTender(tender.value.id, { newOwnerId: transferTarget.value })
      if (res?.success) {
        ElMessage.success('转派成功')
        showTransferDialog.value = false
        await loadTenderDetail()
      } else throw new Error(res?.message || '转派失败')
    } catch (error) { ElMessage.error(error?.message || '转派失败') }
    finally { transferring.value = false }
  }

  const router = useRouter()

  const handleEdit = () => {
    if (!tender.value?.id) return
    router.push({ path: '/bidding/create', query: { edit: tender.value.id } })
  }

  // ---

  onMounted(async () => await loadTenderDetail())

  return {
    tender, matchScore,
    tenderNotFound,
    matchScoreState, scoreEmptyText, scoreEmptyDescription,
    regionMeta, industryMeta, deadlineParts,
    getScoreClass, getStatusType, getStatusText, getDeadlineClass,
    handleParticipate, handleViewOriginal, handleAbandon,
    loadMatchScore, loadTenderDetail,
    handleEdit,
    showAssignDialog, assignForm, assigning, openAssign, doAssign,
    showTransferDialog, transferTarget, transferring, openTransfer, doTransfer,
  }
}
