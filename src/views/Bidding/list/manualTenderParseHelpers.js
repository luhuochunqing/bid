// Input: doc-insight parse result for manual tender intake
// Output: normalized editable manual tender form fields
// Pos: src/views/Bidding/list/ - Pure helpers for manual tender document recognition
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
// 字段映射口径（与 constants.js 中 createManualTenderForm() 保持一致）：
// 表单字段: title | tenderAgency | region | deadline | bidOpeningTime | customerType |
//           priority | projectType | contact | phone | landline | mail | contact2 | phone2 |
//           landline2 | mail2 | description | tenderInfo | tags | attachments |
//           sourceDocumentName | sourceDocumentFileType | sourceDocumentFileUrl |
//           pastedText | crmOpportunityId
// AI 不回填: budget（已从表单删除）, purchaser（已从表单删除）, tenderScope（表单的 description 为手填）
// contactEmail 单独映射到 mail，不再作为 phone 的 fallback

function cleanText(value) {
  if (value === null || value === undefined) return ''
  return String(value).trim()
}

function firstText(...values) {
  for (const value of values) {
    const text = cleanText(value)
    if (text) return text
  }
  return ''
}

function normalizeTags(value) {
  if (Array.isArray(value)) {
    return value.map(cleanText).filter(Boolean)
  }
  const text = cleanText(value)
  if (!text) return []
  return text.split(/[,，、;\s]+/).map(cleanText).filter(Boolean)
}

export function normalizeBudgetYuan(value) {
  if (value === null || value === undefined || value === '') return null
  if (typeof value === 'number') {
    return Number.isFinite(value) ? value : null
  }

  const text = cleanText(value).replace(/,/g, '')
  const matched = text.match(/-?\d+(?:\.\d+)?/)
  if (!matched) return null

  const amount = Number(matched[0])
  if (!Number.isFinite(amount)) return null
  if (text.includes('亿元') || text.includes('亿')) return amount * 100000000
  if (text.includes('万元') || text.includes('万')) return amount * 10000
  return amount
}

function normalizeDeadline(value) {
  const text = cleanText(value)
  if (!text) return null
  const dateOnly = text.match(/^(\d{4})-(\d{2})-(\d{2})$/)
  if (dateOnly) {
    return new Date(Number(dateOnly[1]), Number(dateOnly[2]) - 1, Number(dateOnly[3]))
  }
  const date = new Date(text)
  return Number.isNaN(date.getTime()) ? null : date
}

/**
 * 将 AI 解析结果映射到人工录入表单字段。
 * 仅映射表单实际存在的字段：
 * - title       <- tenderTitle / projectName
 * - tenderAgency <- tenderAgency / agencyName
 * - region      <- headquartersLocation / region
 * - deadline    <- deadline
 * - bidOpeningTime <- bidOpeningTime / openingTime
 * - customerType <- customerType（值直接透传，不做选项映射）
 * - priority    <- priority（值直接透传，不做选项映射）
 * - contact     <- contactName / contactPerson / contact
 * - phone       <- contactPhone / phone / mobile（不含 email）
 * - landline    <- contactTel / contactLandline / landline
 * - mail        <- contactEmail / email
 * - contact2    <- contactName2 / secondaryContact
 * - phone2      <- contactPhone2 / phone2 / secondaryPhone
 * - landline2   <- contactTel2 / contactLandline2 / secondaryLandline
 * - mail2       <- contactEmail2 / email2 / secondaryEmail
 * - tenderInfo  <- tenderInfo（完整原文）/ tenderScope（摘要，兼容旧 AI 响应）
 * - tags        <- tags
 *
 * 不映射：budget（已从表单删除）、tenderScope（不回填 description）
 */
export function normalizeManualTenderParseResult(result = {}) {
  const data = result?.extractedData || {}
  return {
    title: firstText(data.tenderTitle, data.title, data.projectName),
    purchaser: firstText(data.purchaserName, data.tenderAgency, data.agencyName),
    region: firstText(data.headquartersLocation, data.region),
    deadline: normalizeDeadline(data.deadline),
    bidOpeningTime: normalizeDeadline(firstText(data.bidOpeningTime, data.openingTime)),
    customerType: firstText(data.customerType),
    priority: firstText(data.priority),
    contact: firstText(data.contactName, data.contactPerson, data.contact),
    phone: firstText(data.contactPhone, data.phone, data.mobile, data.contactMobile),
    landline: firstText(data.contactTel, data.contactLandline, data.landline),
    mail: firstText(data.contactEmail, data.email),
    contact2: firstText(data.contactName2, data.secondaryContact),
    phone2: firstText(data.contactPhone2, data.phone2, data.secondaryPhone),
    landline2: firstText(data.contactTel2, data.contactLandline2, data.secondaryLandline),
    mail2: firstText(data.contactEmail2, data.email2, data.secondaryEmail),
    tenderInfo: firstText(data.tenderInfo, data.tenderScope),
    tags: normalizeTags(data.tags),
  }
}
