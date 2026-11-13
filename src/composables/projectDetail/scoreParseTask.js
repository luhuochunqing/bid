// Input: statusFetcher（返回 { data: { taskId, status, errorMessage, completedAt } } 的函数、可选taskId/AbortSignal）
// Output: pollTask / formatTime / STATUS_MAP / TYPE_MAP — spec 041 状态映射与可取消、绑定任务身份的轮询
//         normalizeScoreItem：未知 weight/客观项空 estScore 保留 null（展示层渲染"待确认"，spec 044 FR-001）
//         mapScoreResults / lookupScoreResult：按 scoreItemId 建表，有 id 时不得回退 code
//         getDecisionBadgeType / getDecisionBadgeText: 评分决策状态徽标映射纯函数
// Pos: src/composables/projectDetail/ - 无状态辅助模块
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

// spec 041 真接口的状态枚举 → 抽屉 UI 语义
export const STATUS_MAP = { OK: 'ok', DANGER: 'danger', PENDING: 'neutral' }
export const TYPE_MAP = { OBJECTIVE: '客观项', SUBJECTIVE: '主观项' }
export const CATEGORY_MAP = {
  business: '商务', commercial: '商务',
  technology: '技术', technical: '技术',
  finance: '财务', financial: '财务',
  service: '服务', other: '其他',
}

export function categoryLabel(value) {
  if (value == null || value === '') return '评分项'
  const raw = String(value).trim()
  return CATEGORY_MAP[raw.toLowerCase()] || raw
}

const POLL_INTERVAL_MS = 2000
const POLL_MAX_ATTEMPTS = 900 // 前后端统一 30 分钟超时上限（900 × 2s）

export function formatTime(value) {
  if (!value) return null
  const d = new Date(value)
  return Number.isNaN(d.getTime()) ? String(value) : d.toLocaleString('zh-CN', { hour12: false })
}

/** 轮询指定任务；最新任务变化时明确切换观察，取消只停止本地等待。 */
export async function pollTask(statusFetcher, taskType = '解析', options = {}) {
  const { signal, onTaskChanged } = options
  let taskId = options.taskId, observedReplacement = false
  for (let i = 0; i < POLL_MAX_ATTEMPTS; i++) {
    signal?.throwIfAborted()
    const res = await statusFetcher()
    signal?.throwIfAborted()
    const task = res?.data || {}
    if (taskId != null && task.taskId != null && String(taskId) !== String(task.taskId)) {
      onTaskChanged?.(task.taskId, taskId)
      observedReplacement = true
      taskId = task.taskId
    }
    const matching = taskId == null || (task.taskId != null && String(taskId) === String(task.taskId))
    if (matching && task.status === 'COMPLETED') return observedReplacement ? { ...task, observedReplacement } : task
    if (matching && task.status === 'FAILED') {
      throw Object.assign(new Error(task.errorMessage || '任务执行失败'), { task, backendFailed: true })
    }
    await waitForPoll(signal)
  }
  const verb = taskType === '打分' ? '打分' : '解析'
  throw new Error(`${verb}超时，请检查文件大小或稍后重试`)
}

function waitForPoll(signal) {
  return new Promise((resolve, reject) => {
    let timer
    const onAbort = () => {
      clearTimeout(timer)
      reject(signal.reason)
    }
    timer = setTimeout(() => {
      signal?.removeEventListener('abort', onAbort)
      resolve()
    }, POLL_INTERVAL_MS)
    signal?.addEventListener('abort', onAbort, { once: true })
    if (signal?.aborted) onAbort()
  })
}

function safeParseJson(val, fallback) {
  if (val == null) return fallback
  if (typeof val === 'object') return val
  if (typeof val === 'string' && (val.trim().startsWith('[') || val.trim().startsWith('{'))) {
    try {
      return JSON.parse(val)
    } catch {
      return fallback
    }
  }
  return fallback
}

export function sanitizeCode(raw) {
  if (raw == null) return ''
  let s = String(raw).trim()
  // 剥除零宽字符（NFKC 不处理 U+200B-200D/FEFF，需显式清除）
  s = s.replace(/[\u200B-\u200D\uFEFF]/g, '')
  // 与后端 ScoreCodeSanitizer 对齐：标准 NFKC 归一（全角 ｎｕｌｌ->null，全角括号/标点->半角，①->1）。
  // 差集说明：U+3001 、不受 NFKC 影响（中文编号"一、"保留）；后端另有 50 字符截断，前端展示码不落库故不截断。
  s = s.normalize('NFKC').trim()
  if (!s || /^(?:null|undefined|none|n\/?a|\(null\)|null\.0)$/i.test(s)) {
    return ''
  }
  s = s.replace(/\(\s*null\s*\)/gi, '')
  s = s.replace(/(?:^|(?<=[._/\\\-\s]))(?:null|undefined|none|n\/?a)(?:(?=[._/\\\-\s])|$)/gi, '')
  s = s.replace(/[._/\\\-\s]{2,}/g, '.')
  s = s.replace(/^[._/\\\-\s]+/, '')
  s = s.replace(/[._/\\\-\s]+$/, '')
  if (!s || /^(?:null|undefined|none|n\/?a|\(null\))$/i.test(s)) {
    return ''
  }
  return s
}

export function normalizeScoreItem(s, i) {
  const isSubj = s.scoreType === 'SUBJECTIVE' || s.scoreType === '主观项'
  const status = STATUS_MAP[s.status] || 'neutral'
  const parsedCells = safeParseJson(s.sourceCellIds, null)
  const cellList = Array.isArray(s.sourceCellIds)
    ? s.sourceCellIds
    : (Array.isArray(parsedCells) ? parsedCells : (s.sourceCellIds ? [String(s.sourceCellIds)] : []))

  const displayCode = sanitizeCode(s.code) || `S${i + 1}`

  return {
    id: s.id,
    code: displayCode,
    dim: categoryLabel(s.dim),
    detail: s.detail || '',
    weight: s.weight != null ? Number(s.weight) : null,
    scoreType: TYPE_MAP[s.scoreType] || s.scoreType || (isSubj ? '主观项' : '客观项'),
    status,
    estScore: isSubj ? '待确认' : (s.estScore != null ? Number(s.estScore) : null),
    estBasis: isSubj
      ? (s.estBasis || '主观项需专家评审')
      : (s.estBasis != null ? s.estBasis : (s.estScore != null ? '知识库匹配完成' : '待人工确认预计得分')),
    kbHit: s.kbHit != null ? Boolean(s.kbHit) : null,
    sourceText: s.sourceText || s.detail || '',
    contextNote: s.contextNote || '',
    location: s.location || '',
    sourceCellIds: cellList,
    weightEvidence: safeParseJson(s.weightEvidence, s.weightEvidence || null),
    extractionMethod: s.extractionMethod || '',
    confidence: s.confidence != null ? Number(s.confidence) : null,
    validationStatus: s.validationStatus || '',
    parentScoreItemId: s.parentScoreItemId || null,
  }
}

export function mapScoreResults(results) {
  const resultMap = Object.create(null)
  for (const row of results || []) {
    const normalized = normalizeScoreResult(row)
    if (row.scoreItemId != null) {
      resultMap[`id:${row.scoreItemId}`] = normalized
    }
    // 冲突策略：首写获胜（同一清洗码多结果并存时保留首行，无告警，展示映射可接受）
    if (row.code) {
      const cleanCode = sanitizeCode(row.code)
      if (resultMap[`code:${row.code}`] == null) {
        resultMap[`code:${row.code}`] = normalized
      }
      if (cleanCode && resultMap[`code:${cleanCode}`] == null) {
        resultMap[`code:${cleanCode}`] = normalized
      }
      if (resultMap[row.code] == null) {
        resultMap[row.code] = normalized
      }
      if (cleanCode && resultMap[cleanCode] == null) {
        resultMap[cleanCode] = normalized
      }
    }
  }
  return resultMap
}

export function lookupScoreResult(results, item) {
  if (!results || !item) return null
  if (item.id != null) {
    return results[`id:${item.id}`] ?? null
  }
  if (item.code != null) {
    const cleanCode = sanitizeCode(item.code)
    return results[`code:${item.code}`]
      ?? (cleanCode ? results[`code:${cleanCode}`] : null)
      ?? results[item.code]
      ?? (cleanCode ? results[cleanCode] : null)
      ?? null
  }
  return null
}

export function normalizeScoreResult(r) {
  const isSubj = r.scoreType === 'SUBJECTIVE' || r.scoreType === '主观项'
  const isPending = r.status === 'PENDING' || r.statusStage2 === 'PENDING' || Boolean(r.missedReason)
  return {
    outcome: r.outcome || null,
    reasonCode: r.reasonCode || null,
    originTaskId: r.originTaskId ?? null,
    status: STATUS_MAP[r.status] || (r.actualScore != null ? 'ok' : 'neutral'),
    score: isSubj ? null : (r.actualScore != null ? Number(r.actualScore) : null),
    actualScore: isSubj ? null : (r.actualScore != null ? Number(r.actualScore) : null),
    evalText: isSubj ? '待确认' : (r.actualScore != null ? `${r.actualScore} 分` : (isPending ? '待确认' : '未评分')),
    basis: r.evidence || '',
    quote: r.quote || '',
    missedReason: r.missedReason || '',
    suggestion: r.suggestion || '',
    matchRatio: r.matchRatio != null ? Number(r.matchRatio) : null,
    reuseKind: r.reuseKind || null,
  }
}

const DECISION_BADGE_TYPE_MAP = {
  AUTO_APPROVED: 'success',
  MANUAL_APPROVED: 'success',
  REVIEW_REQUIRED: 'warning',
  STRUCTURE_INCOMPLETE: 'danger',
  PARSE_FAILED: 'info',
}

const DECISION_BADGE_TEXT_MAP = {
  AUTO_APPROVED: '自动通过',
  MANUAL_APPROVED: '人工采纳通过',
  REVIEW_REQUIRED: '待人工复核',
  STRUCTURE_INCOMPLETE: '结构异常',
  PARSE_FAILED: '解析失败',
}

export function getDecisionBadgeType(decisionStatus, reviewSubmitted) {
  if (reviewSubmitted) return 'success'
  return DECISION_BADGE_TYPE_MAP[decisionStatus] || ''
}

export function getDecisionBadgeText(decisionStatus, reviewSubmitted) {
  if (reviewSubmitted) return '已完成复核'
  return DECISION_BADGE_TEXT_MAP[decisionStatus] || ''
}

/** 仅这些状态代表"本次解析没产出"，落到项目上会与仍在生效的历史评分项冲突。 */
const FAILURE_DECISION_STATUSES = new Set(['STRUCTURE_INCOMPLETE', 'PARSE_FAILED'])

/**
 * 当次解析失败、但项目仍保留着可用的历史评分项时不展示失败徽章：
 * 否则用户看到的是"评分项正确"与"结构异常"并存，误以为表格结构有问题。
 * 评分项为空时仍展示，真实失败不能被掩盖。
 */
export function shouldHideFailureBadge(decisionStatus, activeItemCount) {
  return FAILURE_DECISION_STATUSES.has(decisionStatus) && Number(activeItemCount) > 0
}
