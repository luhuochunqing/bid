const EXPENSE_CATEGORY_MAP = {
  保证金: 'OTHER',
  标书费: 'OTHER',
  标书购买费: 'OTHER',
  差旅费: 'TRANSPORTATION',
  其他: 'OTHER'
}

export function today() {
  return new Date().toISOString().slice(0, 10)
}

export function defaultSearchForm() {
  return {
    project: '',
    type: '',
    status: ''
  }
}

export function defaultApplyForm() {
  return {
    type: '保证金',
    project: '',
    amount: 0,
    remark: ''
  }
}

export function defaultApprovalForm() {
  return {
    result: 'approved',
    comment: ''
  }
}

export function defaultPaymentForm(userName = '') {
  return {
    amount: 0,
    paidAt: today(),
    paidBy: userName,
    paymentMethod: 'BANK_TRANSFER',
    paymentReference: '',
    remark: ''
  }
}

export function normalizePaymentDateTime(value) {
  const text = String(value || '').trim()
  if (!text) return ''
  if (/^\d{4}-\d{2}-\d{2}$/.test(text)) return `${text}T00:00:00`
  if (/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/.test(text)) return text.replace(' ', 'T')
  if (/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(text)) return `${text}:00`
  if (/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}$/.test(text)) return text.replace(' ', 'T') + ':00'
  return text
}

export function resolveExpenseCategory(type) {
  return EXPENSE_CATEGORY_MAP[type] || 'OTHER'
}

export function addDays(dateText, days) {
  const date = new Date(dateText)
  if (Number.isNaN(date.getTime())) return ''
  date.setDate(date.getDate() + days)
  return date.toISOString().slice(0, 10)
}

export function isOverdue(dateText) {
  if (!dateText) return false
  const targetDate = new Date(dateText)
  if (Number.isNaN(targetDate.getTime())) return false
  return targetDate < new Date()
}

export function getOverdueDays(dateText) {
  if (!isOverdue(dateText)) return 0
  const diffTime = new Date() - new Date(dateText)
  return Math.ceil(diffTime / (1000 * 60 * 60 * 24))
}
