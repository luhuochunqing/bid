// Input: 质量核查项证据列表 / AI 结论文本 / 知识库来源标识 / 评分项与打分结果（已归一化）
// Output: 详情弹窗展示用纯函数（结论前缀剥离、页码聚合、摘录提取、四库链接映射、客观项得分汇总）
// Pos: src/views/Project/stages/composables/ - 质量核查详情展示纯核心（可单测，不依赖组件状态）

/** MANUAL 态结论 [REASON_CODE] 前缀（spec 049 ManualReasons 约定），展示时剥离. */
const REASON_CODE_PREFIX_RE = /^\s*\[[A-Z_]+\]\s*/

/** 核查状态 → 图标/标签类型/中文文案（紧凑行与详情弹窗共用的单一数据源）. */
export const STATUS_META = {
  PASS: { icon: '✓', tagType: 'success', text: '通过' },
  FAIL: { icon: '✗', tagType: 'danger', text: '不通过' },
  MANUAL_REVIEW: { icon: '⚠', tagType: 'warning', text: '待确认' }
}

export const DEFAULT_STATUS_META = { icon: '·', tagType: 'info', text: '未知' }

export function getStatusMeta(status) {
  return STATUS_META[status] || DEFAULT_STATUS_META
}

/** 状态中文文案；未知状态回退原始值. */
export function formatStatus(status) {
  return STATUS_META[status]?.text || status || '未知'
}

/** 四知识库来源 → 库页面路由与中文名（路由见 src/router/index.js knowledge children）. */
export const KB_SOURCE_LINKS = {
  qualification: { label: '资质库', route: '/knowledge/qualification' },
  'brand-authorization': { label: '品牌授权库', route: '/knowledge/brand-auth' },
  personnel: { label: '人员库', route: '/knowledge/personnel' },
  performance: { label: '业绩库', route: '/knowledge/performance' }
}

/** 剥离 MANUAL 结论的 [REASON_CODE] 前缀；空值返回 ''. */
export function stripReasonCode(conclusion) {
  if (!conclusion) return ''
  return conclusion.replace(REASON_CODE_PREFIX_RE, '')
}

/** BID 类证据页码去重聚合 → "第 12 / 56 / 78 页"；无可定位页码返回 ''. */
export function extractBidPages(evidences) {
  const pages = [...new Set(
    (evidences || [])
      .filter(ev => ev?.sourceType === 'BID' && Number.isInteger(ev.pageNumber))
      .map(ev => ev.pageNumber)
  )]
  if (pages.length === 0) return ''
  return `第 ${pages.join(' / ')} 页`
}

/** BID 类证据原文摘录（取第一条非空）；无摘录返回 ''. */
export function extractBidQuote(evidences) {
  const ev = (evidences || []).find(e => e?.sourceType === 'BID' && e.quote)
  return ev ? ev.quote : ''
}

/** KNOWLEDGE_BASE 类证据 → 四库跳转链接（按库去重，忽略未知来源）. */
export function extractKbLinks(evidences) {
  const seen = new Set()
  const links = []
  for (const ev of evidences || []) {
    if (ev?.sourceType !== 'KNOWLEDGE_BASE' || !ev.knowledgeSource) continue
    const def = KB_SOURCE_LINKS[ev.knowledgeSource]
    if (!def || seen.has(ev.knowledgeSource)) continue
    seen.add(ev.knowledgeSource)
    links.push({ source: ev.knowledgeSource, ...def })
  }
  return links
}

/** 客观项实际得分合计（对齐 useScoreParseDrawer.actualTotalScore 口径）；无有效得分返回 null. */
export function sumObjectiveActualScore(items, resultMap) {
  let total = 0
  let hasScore = false
  for (const item of items || []) {
    if (item.scoreType !== '客观项') continue
    const res = (resultMap || {})[item.code]
    const val = res?.actualScore ?? res?.score
    if (typeof val === 'number') {
      total += val
      hasScore = true
    }
  }
  return hasScore ? total : null
}
