// Input: normalized expense rows from the resources API
// Output: deposit return tracking rows for the expense page
// Pos: src/views/Resource/expense/ - Expense follow-up helper
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

function normalizeTrackingStatus(expense = {}) {
  if (expense.status === 'returned') return 'returned'
  if (expense.overdue) return 'overdue'
  if (expense.backendStatus === 'RETURN_REQUESTED') return 'return_requested'
  return 'pending'
}

export function buildDepositTrackingList(expenses = []) {
  return expenses
    .filter((expense) => expense.type === '保证金')
    .map((expense) => ({
      ...expense,
      expectedReturnDate: expense.expectedReturnDate || '',
      lastRemindedAt: expense.lastRemindedAt || '',
      overdue: Boolean(expense.overdue),
      trackingStatus: normalizeTrackingStatus(expense)
    }))
}

export function getTrackingStatusLabel(row = {}) {
  if (row.trackingStatus === 'returned') return '已退还'
  if (row.trackingStatus === 'overdue') return '超期未退'
  if (row.trackingStatus === 'return_requested') return '已申请退还'
  return '待退还'
}

export function getTrackingStatusType(row = {}) {
  if (row.trackingStatus === 'returned') return 'success'
  if (row.trackingStatus === 'overdue') return 'danger'
  if (row.trackingStatus === 'return_requested') return 'warning'
  return 'info'
}
