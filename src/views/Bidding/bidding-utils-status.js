// Input: tender status values from backend responses and legacy frontend UI
// Output: pure helpers for canonical tender status normalization and display
// Pos: src/views/Bidding/ - Tender status utility layer
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

export const TENDER_STATUSES = Object.freeze({
  PENDING_ASSIGNMENT: 'PENDING_ASSIGNMENT',
  TRACKING: 'TRACKING',
  EVALUATED: 'EVALUATED',
  BIDDING: 'BIDDING',
  WON: 'WON',
  LOST: 'LOST',
  ABANDONED: 'ABANDONED'
})

const TENDER_STATUS_META = Object.freeze({
  [TENDER_STATUSES.PENDING_ASSIGNMENT]: {
    label: '待分配',
    tagType: 'info',
    badgeClass: 'pending'
  },
  [TENDER_STATUSES.TRACKING]: {
    label: '跟踪中',
    tagType: 'primary',
    badgeClass: 'tracking'
  },
  [TENDER_STATUSES.EVALUATED]: {
    label: '已评估',
    tagType: 'warning',
    badgeClass: 'evaluated'
  },
  [TENDER_STATUSES.BIDDING]: {
    label: '投标中',
    tagType: 'warning',
    badgeClass: 'bidding'
  },
  [TENDER_STATUSES.WON]: {
    label: '已中标',
    tagType: 'success',
    badgeClass: 'won'
  },
  [TENDER_STATUSES.LOST]: {
    label: '未中标',
    tagType: 'danger',
    badgeClass: 'lost'
  },
  [TENDER_STATUSES.ABANDONED]: {
    label: '已放弃',
    tagType: 'info',
    badgeClass: 'abandoned'
  }
})

const LEGACY_STATUS_ALIASES = Object.freeze({
  pending: TENDER_STATUSES.PENDING_ASSIGNMENT,
  pending_assignment: TENDER_STATUSES.PENDING_ASSIGNMENT,
  following: TENDER_STATUSES.TRACKING,
  tracking: TENDER_STATUSES.TRACKING,
  evaluated: TENDER_STATUSES.EVALUATED,
  bidding: TENDER_STATUSES.BIDDING,
  bidded: TENDER_STATUSES.BIDDING,
  contacted: TENDER_STATUSES.TRACKING,
  quoting: TENDER_STATUSES.TRACKING,
  won: TENDER_STATUSES.WON,
  lost: TENDER_STATUSES.LOST,
  abandoned: TENDER_STATUSES.ABANDONED,
  '待分配': TENDER_STATUSES.PENDING_ASSIGNMENT,
  '待处理': TENDER_STATUSES.PENDING_ASSIGNMENT,
  '跟踪中': TENDER_STATUSES.TRACKING,
  '已评估': TENDER_STATUSES.EVALUATED,
  '投标中': TENDER_STATUSES.BIDDING,
  '已投标': TENDER_STATUSES.BIDDING,
  '已中标': TENDER_STATUSES.WON,
  '未中标': TENDER_STATUSES.LOST,
  '已放弃': TENDER_STATUSES.ABANDONED
})

export function normalizeTenderStatusCode(status) {
  if (!status) {
    return TENDER_STATUSES.PENDING_ASSIGNMENT
  }

  const normalizedValue = String(status).trim()
  const upperValue = normalizedValue.toUpperCase()
  if (TENDER_STATUS_META[upperValue]) {
    return upperValue
  }

  return LEGACY_STATUS_ALIASES[normalizedValue.toLowerCase()] || TENDER_STATUSES.PENDING_ASSIGNMENT
}

export function normalizeTenderRecord(tender = {}) {
  return {
    ...tender,
    status: normalizeTenderStatusCode(tender.status)
  }
}

export function normalizeTenderCollection(tenders = []) {
  if (!Array.isArray(tenders)) {
    return []
  }
  return tenders.map(normalizeTenderRecord)
}

export function matchesTenderStatus(actualStatus, expectedStatus) {
  if (!expectedStatus) {
    return true
  }
  return normalizeTenderStatusCode(actualStatus) === normalizeTenderStatusCode(expectedStatus)
}

export function getTenderStatusText(status) {
  if (status == null || status === '') {
    return '未知'
  }

  const normalizedValue = String(status).trim()
  const upperValue = normalizedValue.toUpperCase()
  if (TENDER_STATUS_META[upperValue]) {
    return TENDER_STATUS_META[upperValue].label
  }

  const aliasValue = LEGACY_STATUS_ALIASES[normalizedValue.toLowerCase()]
  if (aliasValue) {
    return TENDER_STATUS_META[aliasValue].label
  }

  return normalizedValue
}

export function getTenderStatusTagType(status) {
  return TENDER_STATUS_META[normalizeTenderStatusCode(status)]?.tagType || 'info'
}

export function getTenderStatusBadgeClass(status) {
  return TENDER_STATUS_META[normalizeTenderStatusCode(status)]?.badgeClass || 'pending'
}

export function toBackendTenderStatus(status) {
  return normalizeTenderStatusCode(status)
}
