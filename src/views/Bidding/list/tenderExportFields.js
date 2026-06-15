// Input: tender row objects from filteredTenders
// Output: normalized export row with 26 Chinese-header fields per blueprint spec
// Pos: src/views/Bidding/list/ - Tender export field mapping

const pad = (n) => String(n).padStart(2, '0')

function fmtDateTime(val) {
  if (!val) return '-'
  const d = new Date(val)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function fmtDate(val) {
  if (!val) return '-'
  const d = new Date(val)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

function fmtBudget(val) {
  if (val == null || val === '') return '-'
  const n = Number(val)
  return Number.isNaN(n) ? '-' : n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

const FIELD_MAP = [
  ['序号',              (t, i) => i],
  ['项目名称',          (t) => t.title],
  ['招标主体',          (t) => t.purchaserName],
  ['总部所在地',        (t) => t.region],
  ['来源',              (t) => t.source],
  ['来源平台',          (t) => t.sourcePlatform],
  ['发布日期',          (t) => fmtDate(t.publishDate)],
  ['报名截止日期',      (t) => fmtDateTime(t.registrationDeadline)],
  ['开标时间',          (t) => fmtDateTime(t.bidOpeningTime)],
  ['截止日期',          (t) => fmtDateTime(t.deadline)],
  ['预算金额',          (t) => fmtBudget(t.budget)],
  ['客户类型',          (t) => t.customerType],
  ['优先级',            (t) => t.priority ? `${t.priority}级` : '-'],
  ['项目类型',          (t) => t.projectType],
  ['联系人1',           (t) => t.contactName],
  ['联系人1手机号',     (t) => t.contactPhone],
  ['联系人1座机',       (t) => t.contactTel],
  ['联系人1邮箱',       (t) => t.contactMail],
  ['联系人2',           (t) => t.contactName2],
  ['联系人2手机号',     (t) => t.contactPhone2],
  ['联系人2座机',       (t) => t.contactTel2],
  ['联系人2邮箱',       (t) => t.contactMail2],
  ['标讯描述',          (t) => t.description],
  ['标讯信息',          (t) => t.bidNotice],
  ['创建人',            (t) => t.creatorName],
  ['创建时间',          (t) => fmtDateTime(t.createdAt)],
  ['标讯状态',          (t) => t.status],
]

/**
 * @param {Object} tender — 标讯行对象
 * @param {number} index — 序号（从 1 开始）
 * @returns {Object} 导出行对象，键为中文列名
 */
export function normalizeTenderForExport(tender, index) {
  const row = {}
  for (const [label, getter] of FIELD_MAP) {
    row[label] = (getter(tender, index) ?? '-') || '-'
  }
  return row
}
