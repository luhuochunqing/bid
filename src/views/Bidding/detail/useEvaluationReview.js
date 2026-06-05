import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { tendersApi } from '@/api'

/**
 * V130: Evaluation API orchestration composable.
 *
 * Separates the evaluation load / save / submit / review / abandon wiring
 * from the presentational <TenderEvaluationForm> component and keeps the
 * parent <DetailPage> under its line budget.
 *
 * Usage:
 *   const { tenderEvaluation, requiresReview, ... } = useEvaluationReview(tender)
 */
export function useEvaluationReview(tenderRef) {
  const userStore = useUserStore()
  const currentUserRole = computed(() => userStore?.userRole || 'STAFF')

  const tenderEvaluation = ref(null)
  const submitting = ref(false)
  const savingDraft = ref(false)
  const reviewing = ref(false)
  const hasUnsavedChanges = ref(false)
  const evaluationLoading = ref(true)

  function markDirty() {
    hasUnsavedChanges.value = true
  }

  // ---- requires_review flag + canReview gate ----
  const requiresReview = computed(() => Boolean(tenderEvaluation.value?.requiresReview))
  const canReview = computed(() => {
    const role = currentUserRole.value?.toLowerCase()
    return (role === 'bid_admin' || role === 'bid_lead') && requiresReview.value
  })

  // ---- load evaluation when tender resolves ----
  const evaluationStatus = computed(() => tenderEvaluation.value?.evaluationStatus || null)

  async function loadCurrentEvaluation() {
    const id = tenderRef.value?.id
    if (!id) { evaluationLoading.value = false; return }
    evaluationLoading.value = true
    try {
      const result = await tendersApi.loadEvaluation(id)
      if (result?.success !== false) {
        tenderEvaluation.value = result?.data || null
        hasUnsavedChanges.value = false
      }
    } catch (e) {
      console.warn('loadEvaluation failed:', e?.message || e)
    } finally {
      evaluationLoading.value = false
    }
  }

  watch(
    () => tenderRef.value?.id,
    () => { loadCurrentEvaluation() },
    { immediate: true }
  )

  // ---- handlers ----

  function addVersion(payload) {
    if (tenderEvaluation.value?.version != null) {
      return { ...payload, version: tenderEvaluation.value.version }
    }
    return payload
  }

  async function handleEvaluationSaveDraft(payload) {
    if (!tenderRef.value || savingDraft.value) return
    savingDraft.value = true
    try {
      const result = await tendersApi.saveEvaluationDraft(tenderRef.value.id, addVersion(payload))
      if (result?.success !== false) {
        tenderEvaluation.value = result?.data || { ...payload, evaluationStatus: 'DRAFT' }
        hasUnsavedChanges.value = false
        ElMessage.success('草稿已保存')
      } else {
        ElMessage.error(result?.msg || '草稿保存失败')
      }
    } catch (e) {
      if (e?.response?.status === 409) {
        ElMessage.warning('该评估表已被其他人修改，请刷新后重试')
      } else {
        ElMessage.error(e?.response?.data?.msg || '草稿保存失败')
      }
    } finally {
      savingDraft.value = false
    }
  }

  async function handleEvaluationSubmit(payload) {
    if (!tenderRef.value || submitting.value) return
    submitting.value = true
    try {
      const result = await tendersApi.submitEvaluationFinal(tenderRef.value.id, addVersion(payload))
      if (result?.success !== false) {
        tenderEvaluation.value = result?.data || { ...payload, evaluationStatus: 'SUBMITTED' }
        hasUnsavedChanges.value = false
        ElMessage.success('评估已提交')
      } else {
        ElMessage.error(result?.msg || '评估提交失败')
      }
    } catch (e) {
      if (e?.response?.status === 409) {
        ElMessage.warning('该评估表已被其他人修改，请刷新后重试')
      } else {
        ElMessage.error(e?.response?.data?.msg || '评估提交失败')
      }
    } finally {
      submitting.value = false
    }
  }

  async function handleReviewEvaluation() {
    if (!tenderRef.value || reviewing.value) return
    reviewing.value = true
    try {
      const result = await tendersApi.reviewEvaluation(tenderRef.value.id)
      if (result?.success !== false) {
        tenderEvaluation.value = {
          ...(tenderEvaluation.value || {}),
          requiresReview: false,
          evaluationStatus: 'SUBMITTED',
        }
        ElMessage.success('审核完成')
      } else {
        ElMessage.error(result?.msg || '审核操作失败')
      }
    } catch (e) {
      ElMessage.error(e?.response?.data?.msg || '审核操作失败')
    } finally {
      reviewing.value = false
    }
  }

  async function handleAbandonWithReason({ reason }) {
    if (!tenderRef.value) return
    try {
      const result = await tendersApi.abandon(tenderRef.value.id, { reason })
      if (result?.success && result?.data?.accepted) {
        ElMessage.success(result.data.msg || '已放弃该标讯')
        tenderRef.value.status = 'ABANDONED'
      } else {
        ElMessage.warning(result?.data?.msg || '弃标失败')
      }
    } catch (e) {
      ElMessage.error(e?.response?.data?.msg || '弃标失败')
    }
  }

  return {
    tenderEvaluation,
    evaluationStatus,
    submitting,
    savingDraft,
    reviewing,
    requiresReview,
    canReview,
    evaluationLoading,
    hasUnsavedChanges,
    markDirty,
    handleEvaluationSaveDraft,
    handleEvaluationSubmit,
    handleReviewEvaluation,
    handleAbandonWithReason,
  }
}
