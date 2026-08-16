// Input: statusFetcher（返回 { data: { status, errorMessage, completedAt } } 的函数）
// Output: pollTask / formatTime / STATUS_MAP / TYPE_MAP — spec 041 状态映射与任务轮询纯函数
// Pos: src/composables/projectDetail/ - 无状态辅助模块
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

// spec 041 真接口的状态枚举 → 抽屉 UI 语义
export const STATUS_MAP = { OK: 'ok', DANGER: 'danger', PENDING: 'neutral' }
export const TYPE_MAP = { OBJECTIVE: '客观项', SUBJECTIVE: '主观项' }

const POLL_INTERVAL_MS = 2000
const POLL_MAX_ATTEMPTS = 900 // 前后端统一 30 分钟超时上限（900 × 2s）

export function formatTime(value) {
  if (!value) return null
  const d = new Date(value)
  return Number.isNaN(d.getTime()) ? String(value) : d.toLocaleString('zh-CN', { hour12: false })
}

/** 轮询异步任务直到 COMPLETED/FAILED（spec 041 US5 语义） */
export async function pollTask(statusFetcher, taskType = '解析') {
  for (let i = 0; i < POLL_MAX_ATTEMPTS; i++) {
    const res = await statusFetcher()
    const task = res?.data || {}
    if (task.status === 'COMPLETED') return task
    if (task.status === 'FAILED') {
      throw new Error(task.errorMessage || '任务执行失败')
    }
    await new Promise((resolve) => setTimeout(resolve, POLL_INTERVAL_MS))
  }
  const verb = taskType === '打分' ? '打分' : '解析'
  throw new Error(`${verb}超时，请检查文件大小或稍后重试`)
}

export function normalizeScoreItem(s, i) {
  const isSubj = s.scoreType === 'SUBJECTIVE' || s.scoreType === '主观项'
  const status = STATUS_MAP[s.status] || 'neutral'
  return {
    code: s.code || `S${i + 1}`,
    dim: s.dim || '评分项',
    detail: s.detail || '',
    weight: s.weight != null ? Number(s.weight) : 0,
    scoreType: TYPE_MAP[s.scoreType] || s.scoreType || (isSubj ? '主观项' : '客观项'),
    status,
    estScore: isSubj ? '待确认' : (s.estScore != null ? Number(s.estScore) : 0),
    estBasis: isSubj ? (s.estBasis || '主观项需专家评审') : (s.estBasis || '知识库匹配完成'),
    kbHit: s.kbHit != null ? Boolean(s.kbHit) : null,
    sourceText: s.sourceText || s.detail || '',
    contextNote: s.contextNote || '',
    location: s.location || '',
  }
}

export function normalizeScoreResult(r) {
  const isSubj = r.scoreType === 'SUBJECTIVE' || r.scoreType === '主观项'
  return {
    status: STATUS_MAP[r.status] || (r.actualScore != null ? 'ok' : 'neutral'),
    score: isSubj ? null : (r.actualScore != null ? Number(r.actualScore) : null),
    actualScore: isSubj ? null : (r.actualScore != null ? Number(r.actualScore) : null),
    evalText: isSubj ? '待确认' : (r.actualScore != null ? `${r.actualScore} 分` : '未评分'),
    basis: r.evidence || '',
    quote: r.quote || '',
    missedReason: r.missedReason || '',
    suggestion: r.suggestion || '',
    matchRatio: r.matchRatio != null ? Number(r.matchRatio) : null,
  }
}
