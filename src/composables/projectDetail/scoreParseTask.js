// Input: statusFetcher（返回 { data: { status, errorMessage, completedAt } } 的函数）
// Output: pollTask / formatTime / STATUS_MAP / TYPE_MAP — spec 041 状态映射与任务轮询纯函数
// Pos: src/composables/projectDetail/ - 无状态辅助模块
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

// spec 041 真接口的状态枚举 → 抽屉 UI 语义
export const STATUS_MAP = { OK: 'ok', DANGER: 'danger', PENDING: 'neutral' }
export const TYPE_MAP = { OBJECTIVE: '客观项', SUBJECTIVE: '主观项' }

const POLL_INTERVAL_MS = 2000
const POLL_MAX_ATTEMPTS = 150 // 前端 5 分钟轮询上限，后端 30 分钟超时兜底

export function formatTime(value) {
  if (!value) return null
  const d = new Date(value)
  return Number.isNaN(d.getTime()) ? String(value) : d.toLocaleString('zh-CN', { hour12: false })
}

/** 轮询异步任务直到 COMPLETED/FAILED（spec 041 US5 语义） */
export async function pollTask(statusFetcher) {
  for (let i = 0; i < POLL_MAX_ATTEMPTS; i++) {
    const res = await statusFetcher()
    const task = res?.data || {}
    if (task.status === 'COMPLETED') return task
    if (task.status === 'FAILED') {
      throw new Error(task.errorMessage || '任务执行失败')
    }
    await new Promise((resolve) => setTimeout(resolve, POLL_INTERVAL_MS))
  }
  throw new Error('任务轮询超时，请稍后刷新查看结果')
}
