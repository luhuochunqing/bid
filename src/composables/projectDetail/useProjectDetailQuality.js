import { computed, ref } from 'vue'
import { projectQualityApi } from '@/api/modules/ai/quality.js'

function createDemoQualityResult() {
  return {
    id: 'demo-quality-check',
    projectId: 'demo-project',
    documentId: 'demo-document',
    documentName: '投标文书初稿.docx',
    status: 'COMPLETED',
    checkedAt: new Date().toISOString(),
    summary: '已完成质量检查，发现少量可优化项',
    empty: false,
    issues: [
      {
        id: 'demo-quality-issue-1',
        type: 'grammar',
        original: '投标文件已按要求提交。',
        suggestion: '投标文件已按要求提交并归档。',
        location: '摘要第1段',
        ignored: false,
        adopted: false,
      },
    ],
    errors: [
      {
        id: 'demo-quality-issue-1',
        type: 'grammar',
        original: '投标文件已按要求提交。',
        suggestion: '投标文件已按要求提交并归档。',
        location: '摘要第1段',
        ignored: false,
        adopted: false,
      },
    ],
    suggestions: [
      {
        id: 'demo-quality-issue-1',
        type: 'grammar',
        original: '投标文件已按要求提交。',
        suggestion: '投标文件已按要求提交并归档。',
        location: '摘要第1段',
        ignored: false,
        adopted: false,
      },
    ],
  }
}

export function useProjectDetailQuality(context) {
  const { route, isDemoMode, isApiProject, message } = context
  const demoMode = Boolean(isDemoMode?.value ?? isDemoMode)
  const apiMode = Boolean(isApiProject?.value ?? isApiProject)
  const qualityChecking = ref(false)
  const qualityResult = ref(null)

  const hasQualityCheckResult = computed(() => Boolean(
    qualityResult.value || context.project?.value?.aiCheck?.quality,
  ))
  const showQualityCheckCard = computed(() => true)

  const loadQualityResult = async () => {
    if (!apiMode) {
      return null
    }
    const qualityResponse = await projectQualityApi.getProjectQualityResult(route.params.id)
    return qualityResponse?.data || null
  }

  const refreshQualityResult = async () => {
    const quality = await loadQualityResult()
    if (quality) {
      qualityResult.value = quality
    }
    return quality
  }

  const handleAdoptSuggestion = async (issueOrIndex) => {
    const quality = qualityResult.value
    if (!quality?.id) {
      return
    }
    const issue = typeof issueOrIndex === 'number'
      ? quality.errors?.[issueOrIndex]
      : issueOrIndex
    if (!issue?.id) {
      return
    }
    const response = await projectQualityApi.adoptQualitySuggestion(route.params.id, quality.id, issue.id)
    qualityResult.value = response.data
    message.success('建议已采纳')
    return response.data
  }

  const handleIgnoreSuggestion = async (issueOrIndex) => {
    const quality = qualityResult.value
    if (!quality?.id) {
      return
    }
    const issue = typeof issueOrIndex === 'number'
      ? quality.errors?.[issueOrIndex]
      : issueOrIndex
    if (!issue?.id) {
      return
    }
    const response = await projectQualityApi.ignoreQualitySuggestion(route.params.id, quality.id, issue.id)
    qualityResult.value = response.data
    message.success('问题已忽略')
    return response.data
  }

  const runQualityCheck = async ({ silent = false } = {}) => {
    qualityChecking.value = true
    if (demoMode) {
      qualityResult.value = createDemoQualityResult()
      qualityChecking.value = false
      if (!silent) {
        message.success('文书质量检查完成')
      }
      return qualityResult.value
    }

    if (!apiMode) {
      qualityResult.value = null
      if (!silent) {
        message.warning('当前项目ID不是后端真实ID，无法执行文书质量检查')
      }
      qualityChecking.value = false
      return null
    }

    try {
      const response = await projectQualityApi.runProjectQualityCheck(route.params.id)
      qualityResult.value = response?.data || null
      if (!silent) {
        message.success(qualityResult.value?.empty ? '已完成检查，当前无可检查文档' : '文书质量检查完成')
      }
      return qualityResult.value
    } catch (error) {
      qualityResult.value = null
      message.error(error?.response?.data?.msg || error?.message || '文书质量检查失败')
      return null
    } finally {
      qualityChecking.value = false
    }
  }

  refreshQualityResult().catch(() => {})

  return {
    qualityChecking,
    qualityResult,
    hasQualityCheckResult,
    showQualityCheckCard,
    loadQualityResult,
    refreshQualityResult,
    handleAdoptSuggestion,
    handleIgnoreSuggestion,
    runQualityCheck,
  }
}
