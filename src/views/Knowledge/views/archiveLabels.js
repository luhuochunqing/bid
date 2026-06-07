// Archive labels and formatters for ProjectArchive views
export const formatDate = (dateStr) => {
  if (!dateStr) return '-'
  return dateStr.slice(0, 10)
}

export const formatDateTime = (dateStr) => {
  if (!dateStr) return '-'
  return dateStr.replace('T', ' ').slice(0, 19)
}

export const STATUS_LABELS = {
  PENDING_INITIATION: '待立项', INITIATED: '已立项', BIDDING: '投标中',
  EVALUATING: '评标中', WON: '已中标', LOST: '未中标',
  FAILED: '已流标', ABANDONED: '已放弃'
}
export const STATUS_TAG_TYPES = {
  PENDING_INITIATION: 'info', INITIATED: 'info', BIDDING: 'success',
  EVALUATING: 'primary', WON: 'success', LOST: 'danger',
  FAILED: 'warning', ABANDONED: 'info'
}

export const getStatusLabel = (status) => STATUS_LABELS[status] || status
export const getStatusTagType = (status) => STATUS_TAG_TYPES[status] || 'info'

export const getFileCategory = (filename) => {
  const fn = String(filename || '').toLowerCase()
  if (fn.includes('商务') || fn.includes('commercial') || fn.includes('资质')) return 'commercial'
  if (fn.includes('技术') || fn.includes('technical') || fn.includes('方案') || fn.includes('scheme')) return 'technical'
  if (fn.includes('澄清') || fn.includes('clarification')) return 'clarification'
  return 'other'
}

export const CATEGORY_LABELS = { commercial: '商务文件', technical: '技术文件', clarification: '澄清文件', other: '其他文件' }
export const CATEGORY_TAG_TYPES = { commercial: 'primary', technical: 'success', clarification: 'warning', other: 'info' }

export const getFileCategoryLabel = (category) => CATEGORY_LABELS[String(category || '').toLowerCase()] || '其他文件'
export const getFileCategoryTagType = (filename) => CATEGORY_TAG_TYPES[getFileCategory(filename)] || 'info'

export const getFileIconClass = (filename) => {
  const fn = String(filename || '').toLowerCase()
  if (fn.includes('pdf')) return 'icon-pdf'
  if (fn.includes('doc') || fn.includes('docx')) return 'icon-word'
  if (fn.includes('xls') || fn.includes('xlsx')) return 'icon-excel'
  return 'icon-default'
}

export const getTimelineItemType = (action) => {
  const act = String(action || '').toUpperCase()
  if (act.includes('DOWNLOAD') || act.includes('EXPORT') || act.includes('下载')) return 'success'
  if (act.includes('PREVIEW') || act.includes('预览')) return 'primary'
  return 'info'
}
