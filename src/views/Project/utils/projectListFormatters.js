/**
 * Formatters for project list display.
 */
import {
  CRM_SOURCE_LABEL,
  EXTERNAL_PLATFORM_SOURCE_LABEL,
  LEGACY_CRM_SOURCE_LABEL,
  MANUAL_SOURCE_LABEL,
} from '@/utils/sourceLabels.js'

export function formatDate(d) {
  if (!d) return '-'
  const date = new Date(d)
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}


export function bidResultTag(result) {
  if (result === 'WON') return 'success'
  if (['LOST', 'FAILED', 'ABANDONED'].includes(result)) return 'danger'
  return 'info'
}

export function priorityTag(priority) {
  if (priority === 'S') return 'danger'
  if (priority === 'A') return 'warning'
  return 'info'
}

export function priorityLabel(priority) {
  const p = String(priority || '').toUpperCase()
  if (p === 'S') return 'S级'
  if (p === 'A') return 'A级'
  if (p === 'B') return 'B级'
  if (p === 'C') return 'C级'
  return priority || '-'
}

export function stageText(stage) {
  const map = {
    INITIATED: '项目立项',
    DRAFTING: '标书制作',
    EVALUATING: '评标中',
    RESULT_PENDING: '结果确认',
    RETROSPECTIVE: '项目复盘',
    CLOSED: '项目结项',
  }
  return map[stage] || stage || '-'
}

// CO-591: 评标结果 evaluationSubStage → 中文（与 EvaluationStatusPanel.vue SUB_STAGE_LABELS 同源）。
// 未知值 fallback 显示原值，避免丢失数据。
const EVALUATION_SUB_STAGE_LABELS = {
  IN_PROGRESS: '评标中',
  AWAITING_BOARD: '评标结果已出，待上会',
  RESULT_OUT: '评标结果已出',
  ANNOUNCED: '评标结果公示',
}

export function evaluationSubStageText(subStage) {
  if (!subStage) return '-'
  return EVALUATION_SUB_STAGE_LABELS[subStage] || subStage
}

// 客户类型枚举名 → 中文 label 映射。
// 与 useProjectSearch.js 的 customerTypeOptions 保持同源（PR !1571 后端归一化为枚举名）。
// 未知值（如历史中文数据）fallback 显示原值，避免丢失数据。
const CUSTOMER_TYPE_LABELS = {
  GOVERNMENT: '政府机关/事业单位/高校',
  CENTRAL_SOE: '央企',
  LOCAL_SOE: '地方国企',
  PRIVATE: '民企',
  FOREIGN: '港澳台及外企',
  OTHER: '其他',
}

export function customerTypeLabel(value) {
  if (!value) return '-'
  return CUSTOMER_TYPE_LABELS[value] || value
}

// 项目类型枚举名 → 中文 label 映射。
// 与 useProjectSearch.js 的 projectTypeOptions 保持同源（后端归一化为枚举名，对齐 ProjectType 枚举）。
// 未知值（如历史中文数据）fallback 显示原值，避免丢失数据。
const PROJECT_TYPE_LABELS = {
  OFFICE: '办公',
  COMPREHENSIVE: '综合',
  COLLECTIVE: '集采',
  INDUSTRIAL: '工业品',
  OTHER: '其他',
}

export function projectTypeLabel(value) {
  if (!value) return '-'
  return PROJECT_TYPE_LABELS[value] || value
}

export function sourceText(source) {
  const map = {
    // 历史数据兼容：旧版写入的英文枚举名仍按 Tender.SourceType 中文 label 显示
    CRM_OPPORTUNITY: CRM_SOURCE_LABEL,
    EXTERNAL_PLATFORM: EXTERNAL_PLATFORM_SOURCE_LABEL,
    MANUAL_SINGLE: MANUAL_SOURCE_LABEL,
    BULK_IMPORT: MANUAL_SOURCE_LABEL,
    // 历史中文标签兼容：旧 CRM 空格标签展示为当前标准标签
    [LEGACY_CRM_SOURCE_LABEL]: CRM_SOURCE_LABEL,
    [CRM_SOURCE_LABEL]: CRM_SOURCE_LABEL,
    [EXTERNAL_PLATFORM_SOURCE_LABEL]: EXTERNAL_PLATFORM_SOURCE_LABEL,
    [MANUAL_SOURCE_LABEL]: MANUAL_SOURCE_LABEL,
  }
  return map[source] || source || '-'
}

