import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { aiApi, bidMatchScoringApi, tendersApi } from '@/api'
import { normalizeMatchScoreForView } from '../match-scoring/normalizers.js'

export function useAiAnalysisPage() {
  const router = useRouter()
  const route = useRoute()

  const tenderId = ref(route.params.id || 'T001')
  const analysisData = ref(null)
  const tenderInfo = ref(null)
  const expandAll = ref(false)
  const activeDimensions = ref([])
  const showParsingDialog = ref(false)
  const parseProgress = ref(0)
  const parseTimer = ref(null)
  const loadError = ref('')
  const latestMatchScore = ref(null)
  const matchScoreLoading = ref(false)
  const matchScoreError = ref('')

  const progressColors = [
    { color: '#f56c6c', percentage: 30 },
    { color: '#e6a23c', percentage: 60 },
    { color: '#409eff', percentage: 90 },
    { color: '#67c23a', percentage: 100 },
  ]

  const matchScoreForDisplay = computed(() => normalizeMatchScoreForView(latestMatchScore.value))

  const dimensionDetails = computed(() => (
    matchScoreForDisplay.value?.dimensionSummaries.map((dim) => ({
      name: dim.name,
      score: dim.score,
      description: dim.description || dim.evidence[0]?.content || '暂无维度说明',
      suggestion: dim.suggestion || dim.evidence[1]?.content || '暂无改进建议',
    })) || []
  ))

  const getScoreColor = (score) => {
    if (score >= 71) return '#67c23a'
    if (score >= 41) return '#e6a23c'
    return '#f56c6c'
  }

  const getScoreLevelClass = (score) => {
    if (score >= 80) return 'score-excellent'
    if (score >= 60) return 'score-good'
    return 'score-normal'
  }

  const getDimensionTagType = (score) => {
    if (score >= 71) return 'success'
    if (score >= 41) return 'warning'
    return 'danger'
  }

  const getPriorityTagType = (priority) => (priority === 'high' ? 'danger' : 'warning')

  const handleExport = () => {
    ElMessage.info('报告导出能力将在后续版本开放，本轮先提供在线查看结果。')
  }

  const handleSyncTasks = () => {
    ElMessage.info('任务同步能力将在后续版本开放，本轮先提供任务建议查看。')
  }

  const handleTaskCheck = (task) => {
    if (task.completed) {
      ElMessage.success(`任务"${task.title}"已完成`)
    }
  }

  const handleAddToPool = () => {
    ElMessageBox.confirm('确定要加入意向池吗？加入后可以在投标项目列表中查看。', '加入意向池', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'info',
    })
      .then(() => {
        ElMessage.success('已加入意向池')
        router.push('/bidding')
      })
      .catch(() => {})
  }

  const handleCreateProject = () => {
    ElMessageBox.confirm('确定要创建投标项目吗？创建后将进入项目立项流程。', '创建投标项目', {
      confirmButtonText: '确定创建',
      cancelButtonText: '取消',
      type: 'success',
    })
      .then(() => {
        ElMessage.success('正在跳转到项目创建页...')
        router.push({
          path: '/project/create',
          query: { tenderId: tenderId.value },
        })
      })
      .catch(() => {})
  }

  const stopParsingAnimation = () => {
    if (parseTimer.value) {
      clearInterval(parseTimer.value)
      parseTimer.value = null
    }
  }

  const startParsingAnimation = () => {
    stopParsingAnimation()
    showParsingDialog.value = true
    parseProgress.value = 0

    parseTimer.value = setInterval(() => {
      if (parseProgress.value < 100) {
        parseProgress.value += Math.random() * 15 + 5
        if (parseProgress.value > 100) {
          parseProgress.value = 100
        }
      } else {
        stopParsingAnimation()
        setTimeout(() => {
          showParsingDialog.value = false
        }, 500)
      }
    }, 800)
  }

  const showLoadError = (message) => {
    loadError.value = `加载失败：${message}`
    ElMessage.error({
      message: loadError.value,
      duration: 10000,
      showClose: true,
    })
  }

  const loadTenderInfo = async () => {
    try {
      const response = await tendersApi.getDetail(tenderId.value)
      if (response?.success && response.data) {
        tenderInfo.value = response.data
        return
      }
      tenderInfo.value = null
      showLoadError(response?.msg || '标讯信息加载失败')
    } catch (error) {
      tenderInfo.value = null
      showLoadError(error?.response?.data?.msg || error?.message || '标讯信息加载失败')
    }
  }

  const loadAnalysis = async () => {
    try {
      const response = await aiApi.bid.getAnalysis(tenderId.value)
      if (response?.success && response.data) {
        analysisData.value = response.data
        return
      }
      analysisData.value = null
      showLoadError(response?.msg || 'AI 分析数据加载失败')
    } catch (error) {
      analysisData.value = null
      showLoadError(error?.response?.data?.msg || error?.message || 'AI 分析数据加载失败')
    }
  }

  const loadLatestMatchScore = async () => {
    matchScoreLoading.value = true
    matchScoreError.value = ''
    try {
      const response = await bidMatchScoringApi.getLatestScore(tenderId.value)
      if (!response?.success) throw new Error(response?.msg || '匹配评分加载失败')
      latestMatchScore.value = response.data || null
    } catch (error) {
      latestMatchScore.value = null
      matchScoreError.value = error?.response?.data?.msg || error?.message || '匹配评分加载失败'
    } finally {
      matchScoreLoading.value = false
    }
  }

  const initializePage = async () => {
    loadError.value = ''
    const showParsing = !route.params.fromList
    if (showParsing) {
      startParsingAnimation()
    }

    await loadTenderInfo()

    if (showParsing) {
      await new Promise((resolve) => setTimeout(resolve, 1000))
    }

    await Promise.all([
      loadAnalysis(),
      loadLatestMatchScore(),
    ])
  }

  onMounted(() => {
    initializePage()
  })

  onBeforeUnmount(() => {
    stopParsingAnimation()
  })

  return {
    tenderInfo,
    analysisData,
    expandAll,
    activeDimensions,
    showParsingDialog,
    parseProgress,
    loadError,
    latestMatchScore,
    matchScoreForDisplay,
    matchScoreLoading,
    matchScoreError,
    progressColors,
    dimensionDetails,
    getScoreColor,
    getScoreLevelClass,
    getDimensionTagType,
    getPriorityTagType,
    handleExport,
    handleSyncTasks,
    handleTaskCheck,
    handleAddToPool,
    handleCreateProject,
  }
}
