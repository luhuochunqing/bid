/**
 * 任务状态常量（前端单一真相源）
 *
 * 与后端任务状态枚举保持对齐。
 * CO-361 三态模型：TODO → REVIEW → COMPLETED（审核驳回回 TODO）。
 * 修改状态时只需更新此文件，所有引用处自动同步。
 */

// 状态码（与后端 canonical form 一致，大写蛇形）
export const TASK_STATUS = {
  TODO: 'TODO',
  REVIEW: 'REVIEW',
  COMPLETED: 'COMPLETED',
}

// 状态显示名（用于 UI 展示）
export const TASK_STATUS_DISPLAY_NAMES = {
  [TASK_STATUS.TODO]: '待开始',
  [TASK_STATUS.REVIEW]: '待审核',
  [TASK_STATUS.COMPLETED]: '已完成',
}

// 语义分组：可执行交付物上传/提交的状态
export const TASK_ACTIONABLE_STATUSES = [
  TASK_STATUS.TODO,
]

// 语义分组：终态（不可再操作）
export const TASK_TERMINAL_STATUSES = [
  TASK_STATUS.COMPLETED,
]

// 语义分组：审核相关状态
export const TASK_REVIEW_STATUSES = [
  TASK_STATUS.REVIEW,
]

/**
 * 根据状态码获取显示名
 * @param {string} status 状态码
 * @returns {string} 显示名，未匹配返回原值
 */
export function getTaskStatusDisplayName(status) {
  return TASK_STATUS_DISPLAY_NAMES[status] || status
}

/**
 * 判断任务是否可执行交付物上传/提交
 * @param {string} status 状态码
 * @returns {boolean}
 */
export function isTaskActionable(status) {
  return TASK_ACTIONABLE_STATUSES.includes(status)
}

/**
 * 判断任务是否为终态
 * @param {string} status 状态码
 * @returns {boolean}
 */
export function isTaskTerminal(status) {
  return TASK_TERMINAL_STATUSES.includes(status)
}
