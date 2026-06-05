import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { knowledgeApi } from '@/api'
import { createEmptyCaseForm } from './caseMeta.js'

const getCurrentUser = () => {
  try {
    const raw = sessionStorage.getItem('user') || localStorage.getItem('user')
    return raw ? JSON.parse(raw) : null
  } catch (error) {
    return null
  }
}

export function useCaseDetailPage() {
  const router = useRouter()
  const route = useRoute()

  const caseData = ref(null)
  const loading = ref(false)
  const saving = ref(false)
  const editDialogVisible = ref(false)
  const editForm = reactive(createEmptyCaseForm())
  const relatedCasePool = ref([])
  const shareRecords = ref([])
  const referenceRecords = ref([])

  const relatedCases = computed(() => {
    if (!caseData.value) return []

    return relatedCasePool.value
      .filter(c => String(c.id) !== String(caseData.value.id) && c.industry === caseData.value.industry)
      .slice(0, 3)
      .map(c => ({
        id: c.id,
        title: c.title,
        customer: c.customer,
        amount: c.amount,
        year: c.year
      }))
  })

  const loadCaseDetail = async (caseId) => {
    if (!caseId) {
      caseData.value = null
      relatedCasePool.value = []
      shareRecords.value = []
      referenceRecords.value = []
      return
    }

    loading.value = true
    try {
      const [detailResult, listResult] = await Promise.all([
        knowledgeApi.cases.getDetail(caseId),
        knowledgeApi.cases.getList({ page: 1, pageSize: 1000 })
      ])

      if (!detailResult?.success || !detailResult?.data) {
        caseData.value = null
        relatedCasePool.value = []
        shareRecords.value = []
        referenceRecords.value = []
        return
      }

      const found = detailResult.data
      caseData.value = {
        ...found,
        description: found.description || '',
        customer: found.customer || found.customerName || '',
        location: found.location || found.locationName || '',
        period: found.period || found.projectPeriod || '',
        projectPeriod: found.projectPeriod || found.period || '',
        useCount: Number(found.useCount || 0),
        viewCount: Number(found.viewCount || 0),
        technologies: Array.isArray(found.technologies) ? found.technologies : []
      }
      Object.assign(editForm, {
        ...createEmptyCaseForm(),
        title: caseData.value.title,
        customer: caseData.value.customer,
        industry: caseData.value.industry,
        amount: caseData.value.amount,
        location: caseData.value.location,
        tags: [...(caseData.value.tags || [])],
        description: caseData.value.description || '',
        highlights: [...(caseData.value.highlights || [])]
      })

      relatedCasePool.value = listResult?.success && Array.isArray(listResult.data) ? listResult.data : []

      if (/^\d+$/.test(String(caseId))) {
        const [shareResult, referenceResult] = await Promise.all([
          knowledgeApi.cases.getShareRecords(caseId),
          knowledgeApi.cases.getReferenceRecords(caseId)
        ])

        shareRecords.value = shareResult?.success && Array.isArray(shareResult.data) ? shareResult.data : []
        referenceRecords.value = referenceResult?.success && Array.isArray(referenceResult.data) ? referenceResult.data : []
      } else {
        shareRecords.value = []
        referenceRecords.value = []
      }
    } catch (error) {
      console.error('Failed to load case detail:', error)
      ElMessage.error('加载案例详情失败')
      caseData.value = null
      relatedCasePool.value = []
      shareRecords.value = []
      referenceRecords.value = []
    } finally {
      loading.value = false
    }
  }

  const handleUseCase = () => {
    if (!caseData.value) return

    const currentUser = getCurrentUser()

    knowledgeApi.cases.createReferenceRecord(caseData.value.id, {
      referencedBy: currentUser?.id ?? null,
      referencedByName: currentUser?.name || currentUser?.username || '当前用户',
      referenceTarget: '案例详情页手动引用',
      referenceContext: '从案例详情页发起引用'
    }).then((result) => {
      if (!result?.success) {
        ElMessage.error(result?.msg || '案例引用失败')
        return
      }

      referenceRecords.value = [result.data, ...referenceRecords.value]
      caseData.value = {
        ...caseData.value,
        useCount: Number(caseData.value.useCount || 0) + 1
      }
      ElMessage.success('案例已添加到引用列表')
    }).catch(() => {
      ElMessage.error('案例引用失败')
    })
  }

  const handleShare = () => {
    if (!caseData.value) return

    const currentUser = getCurrentUser()

    knowledgeApi.cases.createShareRecord(caseData.value.id, {
      createdBy: currentUser?.id ?? null,
      createdByName: currentUser?.name || currentUser?.username || '当前用户',
      baseUrl: window.location.origin
    }).then((result) => {
      if (!result?.success || !result?.data?.url) {
        ElMessage.error(result?.msg || '分享失败')
        return
      }

      shareRecords.value = [result.data, ...shareRecords.value]
      navigator.clipboard.writeText(result.data.url).then(() => {
        ElMessage.success('分享链接已复制到剪贴板')
      }).catch(() => {
        ElMessage.success(result.data.url)
      })
    }).catch(() => {
      ElMessage.error('分享失败')
    })
  }

  const handleViewRelated = (relatedId) => {
    router.push({ path: '/knowledge/case/detail', query: { id: relatedId } })
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  const handleEdit = () => {
    if (!caseData.value) {
      return
    }
    editDialogVisible.value = true
  }

  const handleSaveEdit = async () => {
    if (!caseData.value?.id) {
      return
    }

    saving.value = true
    try {
      const result = await knowledgeApi.cases.update(caseData.value.id, {
        ...caseData.value,
        ...editForm,
        customerName: editForm.customer,
        locationName: editForm.location,
        projectPeriod: editForm.period || caseData.value.projectPeriod,
        description: editForm.description,
        highlights: [...(editForm.highlights || [])]
      })

      if (!result?.success || !result?.data) {
        throw new Error(result?.msg || '更新失败')
      }

      caseData.value = {
        ...caseData.value,
        ...result.data,
        description: result.data.description || editForm.description,
        customer: result.data.customer || editForm.customer,
        location: result.data.location || editForm.location,
        highlights: [...(result.data.highlights || editForm.highlights || [])]
      }
      editDialogVisible.value = false
      ElMessage.success('案例更新成功')
    } catch (error) {
      ElMessage.error(error?.message || '案例更新失败')
    } finally {
      saving.value = false
    }
  }

  const syncRouteCase = () => {
    const caseId = route.query.id || route.params.id
    if (!caseId) {
      ElMessage.warning('缺少案例ID参数')
      router.push('/knowledge/case')
      return
    }

    loadCaseDetail(caseId)
  }

  watch(
    () => [route.query.id, route.params.id],
    () => syncRouteCase(),
    { immediate: true }
  )

  return {
    caseData,
    editDialogVisible,
    editForm,
    handleEdit,
    handleSaveEdit,
    handleShare,
    handleUseCase,
    handleViewRelated,
    loading,
    referenceRecords,
    relatedCases,
    router,
    saving,
    shareRecords
  }
}
