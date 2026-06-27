import { computed } from 'vue'
import { getProjectStatusText, getProjectStatusType } from '@/views/Project/project-utils.js'
import { getPriorityType, getPriorityLabel as getPriorityText } from '@/views/Dashboard/workbench-formatters.js'

export function useProjectDetailFormatting({ project }) {
  const canSubmit = computed(() => project.value?.status === 'drafting' || project.value?.status === 'reviewing')
  const canRecordResult = computed(() => project.value?.status === 'bidding')

  const getStatusType = (status) => getProjectStatusType(status)
  const getStatusText = (status) => getProjectStatusText(status)

  const getTaskStatusType = (status) => ({ todo: 'info', review: 'warning', done: 'success' }[status] || 'info')
  const getTaskStatusText = (status) => ({ todo: '待办', review: '待审核', done: '已完成' }[status] || status)
  const getBadgeType = (score) => (score >= 90 ? 'success' : score >= 75 ? 'warning' : 'danger')
  const getProgressColor = (score) => (score >= 90 ? '#67c23a' : score >= 75 ? '#e6a23c' : score >= 60 ? '#f56c6c' : '#909399')
  const getScoreLevel = (score) => (score >= 90 ? '优秀' : score >= 80 ? '良好' : score >= 70 ? '合格' : '不合格')
  const formatScore = (score) => Number(score).toFixed(2)
  const getOverallScoreType = (score) => (score >= 90 ? 'success' : score >= 80 ? 'primary' : score >= 70 ? 'warning' : 'danger')

  return {
    canSubmit,
    canRecordResult,
    getStatusType,
    getStatusText,
    getPriorityType,
    getPriorityText,
    getTaskStatusType,
    getTaskStatusText,
    getBadgeType,
    getProgressColor,
    getScoreLevel,
    formatScore,
    getOverallScoreType,
  }
}
