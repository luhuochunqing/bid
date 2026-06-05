// 评标子状态展示工具函数

const SUB_STAGE_LABELS = {
  IN_PROGRESS: '评标中',
  AWAITING_BOARD: '评标结果已出，待上会',
  RESULT_OUT: '评标结果已出',
  ANNOUNCED: '评标结果公示'
}

const SUB_STAGE_TAGS = {
  IN_PROGRESS: 'primary',
  AWAITING_BOARD: 'warning',
  RESULT_OUT: 'info',
  ANNOUNCED: 'success'
}

export function evalSubStageText(subStage) {
  return SUB_STAGE_LABELS[subStage] || subStage || '-'
}

export function evalSubStageTag(subStage) {
  return SUB_STAGE_TAGS[subStage] || 'info'
}
