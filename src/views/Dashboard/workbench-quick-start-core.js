// Input: current user, project/qualification options, and quick-start forms
// Output: pure permission checks, validation results, and real API payloads
// Pos: src/views/Dashboard/ - Dashboard quick-start pure helpers
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

export const QUICK_START_PERMISSION = 'dashboard.quickStart'

export function hasQuickStartPermission(user = {}) {
  const permissions = Array.isArray(user?.menuPermissions) ? user.menuPermissions : []
  return permissions.includes('all') || permissions.includes(QUICK_START_PERMISSION)
}

export function createDefaultBorrowRequestForm({ projects = [], qualifications = [], user = {} } = {}) {
  return {
    projectId: projects[0]?.id || null,
    qualificationId: qualifications[0]?.id || null,
    borrowerName: user?.name || '',
    borrowerDept: user?.dept || '',
    purpose: '',
    expectedReturnDate: '',
    remark: '',
  }
}

export function validateBorrowRequest(form = {}) {
  if (!form.projectId) return { valid: false, message: '请选择关联项目' }
  if (!form.qualificationId) return { valid: false, message: '请选择借阅资质' }
  if (!String(form.borrowerName || '').trim()) return { valid: false, message: '请填写借用人' }
  if (!String(form.purpose || '').trim()) return { valid: false, message: '请填写借阅用途' }
  return { valid: true, message: '' }
}

export function buildQualificationBorrowPayload(form = {}) {
  return {
    borrower: String(form.borrowerName || '').trim(),
    department: String(form.borrowerDept || '').trim(),
    projectId: String(form.projectId || ''),
    purpose: String(form.purpose || '').trim(),
    returnDate: form.expectedReturnDate || '',
    remark: String(form.remark || '').trim(),
  }
}
