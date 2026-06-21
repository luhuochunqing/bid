/**
 * 项目阶段流转配置
 * 统一维护所有阶段定义、顺序、路由映射和推进规则
 */

export const PROJECT_STAGES = Object.freeze([
  { code: 'INITIATED', title: '项目立项', route: 'initiation' },
  { code: 'DRAFTING', title: '标书制作', route: 'drafting' },
  { code: 'EVALUATING', title: '评标中', route: 'evaluation' },
  { code: 'RESULT_PENDING', title: '结果确认', route: 'result' },
  { code: 'RETROSPECTIVE', title: '项目复盘', route: 'retrospective' },
  { code: 'CLOSED', title: '项目结项', route: 'closure' },
])

/**
 * 获取阶段索引（用于判断顺序）
 */
export function getStageIndex(stageCode) {
  return PROJECT_STAGES.findIndex(s => s.code === stageCode)
}

/**
 * 获取下一阶段代码
 */
export function getNextStage(currentStage) {
  const idx = getStageIndex(currentStage)
  return idx >= 0 && idx < PROJECT_STAGES.length - 1
    ? PROJECT_STAGES[idx + 1].code
    : null
}

/**
 * 阶段推进映射：当前阶段 → 推进后应切换到的目标阶段
 * 用于统一 Stage 组件的 switch-tab 事件
 */
export const STAGE_TRANSITION_MAP = Object.freeze({
  DRAFTING: 'EVALUATING',
  EVALUATING: 'RESULT_PENDING',
})

/**
 * 结果确认阶段的下一跳逻辑（根据结果类型）
 */
export function getResultConfirmNextTab(resultType) {
  return (resultType === 'WON' || resultType === 'LOST') ? 'RETROSPECTIVE' : 'CLOSED'
}
