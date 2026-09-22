import { getProjectStatusText, getProjectStatusType } from '@/views/Project/project-utils.js'
import { getPriorityType, getPriorityLabel as getPriorityText } from '@/views/Dashboard/workbench-formatters.js'

export function useProjectDetailCore(context) {
  const { router, route, project, isDemoMode } = context

  const canSubmit = () => project.value?.status === 'drafting' || project.value?.status === 'reviewing'
  const canRecordResult = () => project.value?.status === 'bidding'
  const getStatusType = (status) => getProjectStatusType(status)
  const getStatusText = (status) => getProjectStatusText(status)
  const getTaskStatusType = (status) => ({ todo: 'info', review: 'warning', done: 'success' }[status] || 'info')
  const getTaskStatusText = (status) => ({ todo: '待办', review: '待审核', done: '已完成' }[status] || status)

  const toggleAssistantPanel = () => { context.assistantPanelVisible.value = !context.assistantPanelVisible.value }
  const handleOpenCompetitionIntel = () => { context.showCompetitionIntel.value = true }
  const handleOpenRoiAnalysis = () => { context.showROIAnalysis.value = true }
  const handleOpenScoreCoverage = () => context.message.info('评分点覆盖请查看项目创建页Step 4')
  const handleOpenComplianceCheck = () => { context.showComplianceCheck.value = true }
  const handleOpenVersionControl = () => { context.showVersionControl.value = true }
  const handleOpenCollaboration = () => { context.showCollaboration.value = true }
  const handleOpenAutoTasks = () => { context.showAutoTasks.value = true }
  const handleOpenMobileCard = () => { context.showMobileCard.value = true }

  const goBack = () => router.push('/project')

  return {
    isDemoMode,
    canSubmit,
    canRecordResult,
    getStatusType,
    getStatusText,
    getPriorityType,
    getPriorityText,
    getTaskStatusType,
    getTaskStatusText,
    toggleAssistantPanel,
    handleOpenCompetitionIntel,
    handleOpenRoiAnalysis,
    handleOpenScoreCoverage,
    handleOpenComplianceCheck,
    handleOpenVersionControl,
    handleOpenCollaboration,
    handleOpenAutoTasks,
    handleOpenMobileCard,
    goBack,
  }
}
