// Input: bid result API payloads in old/new backend shapes
// Output: frontend-ready normalized bid result objects
// Pos: src/api/modules/ - Bid result normalizers
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

const toNumber = (value, fallback = 0) => {
  const number = Number(value)
  return Number.isFinite(number) ? number : fallback
}

const toArray = (value) => (Array.isArray(value) ? value : [])

const toLowerText = (value, fallback = '') => String(value || fallback).toLowerCase()

const normalizeResult = (value) => {
  const result = toLowerText(value, 'lost')
  if (result === 'won' || result === 'lost') return result
  return 'lost'
}

const normalizeFetchStatus = (value) => {
  const status = toLowerText(value, 'pending')
  if (['pending', 'confirmed', 'ignored'].includes(status)) return status
  return 'pending'
}

const normalizeReminderType = (value) => {
  const type = toLowerText(value, 'report')
  if (type.includes('notice')) return 'notice'
  if (type.includes('report')) return 'report'
  return type === 'won' ? 'notice' : 'report'
}

const normalizeReminderStatus = (value) => {
  const status = toLowerText(value, 'pending')
  if (['pending', 'uploaded', 'reminded'].includes(status)) return status
  return status === 'sent' ? 'reminded' : 'pending'
}

export const normalizeOverview = (data = {}) => ({
  lastSyncTime: data?.lastSyncTime || data?.syncTime || '',
  pendingCount: toNumber(data?.pendingCount ?? data?.pendingFetchCount, 0),
  uploadPending: toNumber(data?.uploadPending ?? data?.pendingReminderCount, 0),
  competitorCount: toNumber(data?.competitorCount, 0)
})

export const normalizeFetchResult = (item = {}) => ({
  id: item?.id,
  source: item?.source || '',
  tenderId: item?.tenderId ?? null,
  projectId: item?.projectId ?? null,
  projectName: item?.projectName || '',
  result: normalizeResult(item?.result),
  amount: item?.amount ?? null,
  fetchTime: item?.fetchTime || '',
  status: normalizeFetchStatus(item?.status),
  confirmedAt: item?.confirmedAt || '',
  confirmedBy: item?.confirmedBy ?? null,
  ignoredReason: item?.ignoredReason || '',
  registrationType: toLowerText(item?.registrationType || 'manual'),
  contractStartDate: item?.contractStartDate || '',
  contractEndDate: item?.contractEndDate || '',
  contractDurationMonths: item?.contractDurationMonths ?? null,
  remark: item?.remark || '',
  skuCount: item?.skuCount ?? null,
  winAnnounceDocUrl: item?.winAnnounceDocUrl || '',
  noticeDocumentId: item?.noticeDocumentId ?? null,
  analysisDocumentId: item?.analysisDocumentId ?? null
})

export const normalizeReminder = (item = {}) => ({
  id: item?.id,
  projectId: item?.projectId ?? null,
  projectName: item?.projectName || '',
  owner: item?.owner || item?.ownerName || '',
  ownerId: item?.ownerId ?? null,
  lastResultId: item?.lastResultId ?? null,
  type: normalizeReminderType(item?.type || item?.reminderType),
  status: normalizeReminderStatus(item?.status),
  remindTime: item?.remindTime || '',
  lastReminderComment: item?.lastReminderComment || '',
  attachmentDocumentId: item?.attachmentDocumentId ?? null,
  uploadedAt: item?.uploadedAt || '',
  uploadedBy: item?.uploadedBy ?? null
})

export const normalizeCompetitorReport = (item = {}) => ({
  company: item?.company || item?.competitorName || '',
  skuCount: item?.skuCount || '',
  category: item?.category || '',
  discount: item?.discount || '',
  payment: item?.payment || item?.paymentTerms || '',
  winRate: item?.winRate || '',
  projectCount: toNumber(item?.projectCount, 0),
  trend: item?.trend || 'flat'
})

export const normalizeCompetitorRecord = (item = {}) => ({
  id: item?.id ?? null,
  company: item?.company || item?.competitorName || '',
  competitorId: item?.competitorId ?? null,
  projectId: item?.projectId ?? null,
  skuCount: item?.skuCount ?? null,
  category: item?.category || '',
  discount: item?.discount || '',
  paymentTerms: item?.paymentTerms || item?.payment || '',
  notes: item?.notes || item?.remark || '',
  amount: item?.amount ?? null,
  wonAt: item?.wonAt || ''
})

const normalizeAttachment = (attachment = {}) => ({
  id: attachment?.id ?? attachment?.documentId ?? null,
  name: attachment?.name || attachment?.fileName || '',
  fileType: attachment?.fileType || '',
  fileUrl: attachment?.fileUrl || attachment?.url || '',
  uploadedAt: attachment?.uploadedAt || attachment?.time || ''
})

export const normalizeDetail = (data = {}) => {
  const fetchResult = data?.fetchResult || data
  const normalizedFetchResult = normalizeFetchResult(fetchResult)
  const reminder = data?.reminder ? normalizeReminder(data.reminder) : null

  return {
    ...normalizedFetchResult,
    reminder,
    reminders: reminder ? [reminder] : [],
    requiredAttachment: data?.requiredAttachment ? normalizeAttachment(data.requiredAttachment) : null,
    attachments: {
      noticeDocument: data?.noticeAttachment
        ? normalizeAttachment(data.noticeAttachment)
        : null,
      analysisDocument: data?.analysisAttachment
        ? normalizeAttachment(data.analysisAttachment)
        : null
    },
    competitors: toArray(data?.competitorWins || data?.competitors).map(normalizeCompetitorRecord)
  }
}
