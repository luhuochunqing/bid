// Input: current user, project/qualification options, and quick-start forms
// Output: pure permission checks, validation results, and real API payloads
// Pos: src/views/Dashboard/ - Dashboard quick-start pure helpers
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

export const QUICK_START_PERMISSION = 'dashboard.quickStart'

export const QUICK_START_EXPENSE_TYPES = ['保证金', '标书购买费']

export function hasQuickStartPermission(user = {}) {
  const permissions = Array.isArray(user?.menuPermissions) ? user.menuPermissions : []
  return permissions.includes('all') || permissions.includes(QUICK_START_PERMISSION)
}

export function createDefaultBorrowRequestForm({ projects = [], qualifications = [], user = {} } = {}) {
  return {
    mode: 'qualification',
    projectId: projects[0]?.id || null,
    qualificationId: qualifications[0]?.id || null,
    contractNo: '',
    contractName: '',
    sourceName: '',
    borrowerName: user?.name || '',
    borrowerDept: user?.dept || '',
    customerName: '',
    purpose: '',
    borrowType: '原件借阅',
    expectedReturnDate: '',
    remark: '',
  }
}

export function validateBorrowRequest(form = {}) {
  if (form.mode === 'qualification') {
    if (!form.projectId) return { valid: false, message: '请选择关联项目' }
    if (!form.qualificationId) return { valid: false, message: '请选择借阅资质' }
    if (!String(form.borrowerName || '').trim()) return { valid: false, message: '请填写借用人' }
    if (!String(form.purpose || '').trim()) return { valid: false, message: '请填写借阅用途' }
    return { valid: true, message: '' }
  }

  if (!String(form.contractNo || '').trim()) return { valid: false, message: '请填写合同编号' }
  if (!String(form.contractName || '').trim()) return { valid: false, message: '请填写合同名称' }
  if (!String(form.borrowerName || '').trim()) return { valid: false, message: '请填写申请人' }
  if (!String(form.purpose || '').trim()) return { valid: false, message: '请填写借阅用途' }
  if (!form.expectedReturnDate) return { valid: false, message: '请选择预计归还日期' }
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

export function buildContractBorrowPayload(form = {}) {
  return {
    contractNo: String(form.contractNo || '').trim(),
    contractName: String(form.contractName || '').trim(),
    sourceName: String(form.sourceName || '').trim(),
    borrowerName: String(form.borrowerName || '').trim(),
    borrowerDept: String(form.borrowerDept || '').trim(),
    customerName: String(form.customerName || '').trim(),
    purpose: String(form.purpose || '').trim(),
    borrowType: String(form.borrowType || '原件借阅').trim(),
    expectedReturnDate: form.expectedReturnDate || '',
  }
}

export function createDefaultQuickExpenseForm(projects = []) {
  return {
    type: '保证金',
    projectId: projects[0]?.id || null,
    amount: 0,
    expectedReturnDate: '',
    remark: '',
  }
}

export function validateQuickExpense(form = {}) {
  if (!QUICK_START_EXPENSE_TYPES.includes(form.type)) return { valid: false, message: '请选择费用类型' }
  if (!form.projectId) return { valid: false, message: '请选择关联项目' }
  if (!(Number(form.amount) > 0)) return { valid: false, message: '请输入大于 0 的费用金额' }
  return { valid: true, message: '' }
}

export function buildQuickExpensePayload(form = {}, { today, createdBy } = {}) {
  return {
    projectId: Number(form.projectId),
    category: 'OTHER',
    amount: form.amount,
    date: today,
    expenseType: form.type,
    expectedReturnDate: form.type === '保证金' ? (form.expectedReturnDate || null) : null,
    description: String(form.remark || '').trim(),
    createdBy,
  }
}
