import { computed, ref } from 'vue'
import { complianceApi, scoreAnalysisApi } from '@/api/modules/ai.js'

function mapBidDocumentQualityIssues(issues = []) {
  return issues.map((issue) => ({
    category: issue?.ruleName || '质量核查',
    item: issue?.description || '检查项',
    status: issue?.passed === false
      ? 'fail'
      : issue?.severity === 'HIGH' || issue?.severity === 'MANUAL'
        ? 'manual'
        : issue?.passed === true && issue?.severity === 'MEDIUM'
          ? 'warn'
          : 'pass',
    suggestion: issue?.recommendation || '',
  }))
}

function mapComplianceIssues(issues = []) {
  return issues.map((issue) => ({
    category: issue?.ruleType || issue?.severity || '合规',
    item: issue?.ruleName || issue?.description || '检查项',
    status: issue?.passed === false ? 'fail' : 'pass',
    suggestion: issue?.recommendation || issue?.description || '',
  }))
}

function buildScorePanel(analysis = {}) {
  const dimensions = Array.isArray(analysis?.dimensions) ? analysis.dimensions : []
  const findScore = (candidates) => {
    const matched = dimensions.find((dimension) =>
      candidates.some((candidate) => String(dimension?.dimensionName || dimension?.name || '').includes(candidate)),
    )
    return Number(matched?.score || 0)
  }

  return {
    total: Number(analysis?.overallScore || 0),
    tech: findScore(['技术能力', '技术方案', '技术']),
    business: findScore(['商务响应', '商务', '团队经验']),
    price: findScore(['价格竞争力', '价格', '报价']),
    qualification: findScore(['企业资质', '资质', '合规性']),
    comment: analysis?.summary || '',
    suggestions: dimensions.map((dimension) => dimension?.comments).filter(Boolean),
  }
}

function parseProjectTags(project = {}) {
  if (Array.isArray(project.tags)) return project.tags
  if (typeof project.tagsJson === 'string' && project.tagsJson.trim()) {
    try {
      const parsed = JSON.parse(project.tagsJson)
      return Array.isArray(parsed) ? parsed : []
    } catch {
      return []
    }
  }
  return []
}

function buildPreviewScorePanel(preview = {}) {
  const categories = preview.scoreAnalysis?.scoreCategories || []
  const findCategory = (name) => Number(categories.find((item) => item?.name?.includes(name))?.percentage || 0)
  return {
    total: Number(preview.aiSummary?.winScore || 0),
    tech: findCategory('技术'),
    business: findCategory('商务'),
    price: findCategory('价格'),
    qualification: findCategory('资质'),
    comment: preview.aiSummary?.suggestions?.join('；') || '',
    suggestions: preview.aiSummary?.suggestions || [],
  }
}

function buildScorePreviewContext(project = {}, projectId) {
  return {
    projectId,
    tenderId: project.tenderId || null,
    projectName: project.name || '',
    industry: project.industry || '',
    budget: project.budget || 0,
    tags: parseProjectTags(project),
  }
}

export function useProjectDetailAI(context) {
  const { route, isDemoMode, isApiProject, project, message, state } = context
  const aiChecking = ref(false)
  const bidDocQualityChecking = ref(false)
  const activeAITab = ref('compliance')
  const aiResult = ref({ compliance: null, score: null })
  const bidDocQualityResult = ref(null)

  const hasAiCheckResult = computed(() => Boolean(
    aiResult.value.compliance || aiResult.value.score || project.value?.aiCheck?.compliance || project.value?.aiCheck?.score,
  ))
  const hasBidDocQualityResult = computed(() => Boolean(
    bidDocQualityResult.value || project.value?.aiCheck?.bidDocumentQuality,
  ))
  const bidDocQualityIssueCount = computed(() => {
    const issues = bidDocQualityResult.value?.issues || []
    return issues.filter((i) => i.passed === false).length
  })
  const canRunAICheck = computed(() => true)
  const showAICheckCard = computed(() => true)

  const toggleAssistantPanel = () => {
    state.assistantPanelVisible.value = !state.assistantPanelVisible.value
  }

  const openFlag = (target) => { target.value = true }
  const handleOpenScoreCoverage = () => message.info('评分点覆盖请查看项目创建页Step 4')

  const runAICheck = async () => {
    aiChecking.value = true
    if (isDemoMode) {
      const issues = [
        { category: '资质', item: '营业执照年检', status: 'pass', suggestion: '资质有效' },
        { category: '资质', item: 'ISO认证有效期', status: 'pass', suggestion: '认证在有效期内' },
        { category: '响应', item: '技术参数偏离表', status: 'fail', suggestion: '第3项技术参数未响应，建议补充说明' },
        { category: '响应', item: '商务条款应答', status: 'pass', suggestion: '响应完整' },
      ]
      aiResult.value = {
        compliance: { score: Math.round((issues.filter((item) => item.status === 'pass').length / issues.length) * 100), issues },
        score: { total: 87, tech: 90, business: 85, price: 82, qualification: 95, comment: '技术方案整体完整，商务应答较为充分。', suggestions: ['补充技术参数响应说明', '修正目录页码一致性'] },
      }
      aiChecking.value = false
      message.success('AI检查完成')
      return
    }

    if (!isApiProject.value) {
      aiResult.value = { compliance: null, score: null }
      message.warning('当前项目ID不是后端真实ID，无法执行AI检查')
      aiChecking.value = false
      return
    }

    try {
      const projectId = route.params.id
      const complianceResponse = await complianceApi.getCheckResult(projectId)
      let scoreResponse = await scoreAnalysisApi.getAnalysis(projectId)
      if (scoreResponse?.success === false) {
        scoreResponse = await scoreAnalysisApi.generatePreview(buildScorePreviewContext(project.value, projectId))
      }
      const complianceRecord = Array.isArray(complianceResponse?.data) ? complianceResponse.data[0] : complianceResponse?.data
      aiResult.value = {
        compliance: complianceRecord ? { score: Number(complianceRecord.overallScore || complianceRecord.riskScore || 0), issues: mapComplianceIssues(complianceRecord.issues || []) } : null,
        score: scoreResponse?.data?.scoreAnalysis ? buildPreviewScorePanel(scoreResponse.data) : buildScorePanel(scoreResponse?.data),
      }
      message.success('AI检查完成')
    } catch (error) {
      aiResult.value = { compliance: null, score: null }
      message.error(error?.response?.data?.msg || error?.message || 'AI检查失败')
    } finally {
      aiChecking.value = false
    }
  }

  const runBidDocumentQualityCheck = async () => {
    bidDocQualityChecking.value = true
    if (isDemoMode) {
      const issues = [
        { category: '基本信息', item: '封面信息完整性', status: 'pass', suggestion: '封面信息完整' },
        { category: '基本信息', item: '投标函格式规范', status: 'pass', suggestion: '格式规范' },
        { category: '分项检查', item: '目录页码一致性', status: 'fail', suggestion: '目录第3项页码与实际页码不一致' },
        { category: '分项检查', item: '页码连续性', status: 'warning', suggestion: '发现第15页与第17页之间缺页' },
        { category: '附件清单', item: '业绩证明材料', status: 'pass', suggestion: '业绩证明齐全' },
      ]
      bidDocQualityResult.value = {
        score: Math.round((issues.filter((item) => item.status === 'pass').length / issues.length) * 100),
        issues,
        overallStatus: 'WARNING',
      }
      bidDocQualityChecking.value = false
      message.success('标书质量核查完成')
      return
    }

    if (!isApiProject.value) {
      bidDocQualityResult.value = null
      message.warning('当前项目ID不是后端真实ID，无法执行标书质量核查')
      bidDocQualityChecking.value = false
      return
    }

    try {
      const projectId = route.params.id
      const response = await complianceApi.checkBidDocumentQuality(projectId)
      const record = response?.data
      bidDocQualityResult.value = record ? {
        score: Number(record.overallScore || record.riskScore || 0),
        issues: mapBidDocumentQualityIssues(record.issues || []),
        overallStatus: record.overallStatus || 'UNKNOWN',
      } : null
      message.success('标书质量核查完成')
    } catch (error) {
      bidDocQualityResult.value = null
      message.error(error?.response?.data?.msg || error?.message || '标书质量核查失败')
    } finally {
      bidDocQualityChecking.value = false
    }
  }

  const loadBidDocumentQualityResult = async () => {
    if (!isApiProject.value || isDemoMode) {
      return null
    }
    try {
      const projectId = route.params.id
      const response = await complianceApi.getBidDocumentQualityResult(projectId)
      const record = response?.data
      if (record) {
        bidDocQualityResult.value = {
          score: Number(record.overallScore || record.riskScore || 0),
          issues: mapBidDocumentQualityIssues(record.issues || []),
          overallStatus: record.overallStatus || 'UNKNOWN',
        }
      }
      return bidDocQualityResult.value
    } catch {
      return null
    }
  }

  loadBidDocumentQualityResult().catch(() => {})

  return {
    aiChecking,
    bidDocQualityChecking,
    activeAITab,
    aiResult,
    bidDocQualityResult,
    hasAiCheckResult,
    hasBidDocQualityResult,
    bidDocQualityIssueCount,
    canRunAICheck,
    showAICheckCard,
    toggleAssistantPanel,
    handleOpenCompetitionIntel: () => openFlag(state.showCompetitionIntel),
    handleOpenRoiAnalysis: () => openFlag(state.showROIAnalysis),
    handleOpenScoreCoverage,
    handleOpenComplianceCheck: () => openFlag(state.showComplianceCheck),
    handleOpenVersionControl: () => openFlag(state.showVersionControl),
    handleOpenCollaboration: () => openFlag(state.showCollaboration),
    handleOpenAutoTasks: () => openFlag(state.showAutoTasks),
    handleOpenMobileCard: () => openFlag(state.showMobileCard),
    runAICheck,
    runBidDocumentQualityCheck,
    loadBidDocumentQualityResult,
  }
}
