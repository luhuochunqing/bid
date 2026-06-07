import { mount, flushPromises } from '@vue/test-utils'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

const state = vi.hoisted(() => ({
  roleCode: 'bid_admin',
  userRole: 'bid_admin',
  currentUser: { roleCode: 'bid_admin', role: 'bid_admin', name: '测试用户' },
  borrowRecords: [
    {
      id: 11,
      qualificationName: 'AAA资质',
      borrower: '张三',
      department: '市场部',
      purpose: 'bidding',
      borrowDate: '2026-06-01',
      returnDate: '2026-06-10',
      status: 'borrowed'
    }
  ],
  borrowLoading: false,
  borrowFeaturePlaceholder: null,
  workflowFields: [{ field: 'borrower', label: '借用人', type: 'text', required: true }]
}))

vi.mock('@/stores/user', () => ({
  useUserStore: () => ({
    currentUser: state.currentUser,
    userRole: state.userRole,
    userName: '测试用户'
  })
}))

vi.mock('@/stores/qualification', () => ({
  useQualificationStore: () => ({
    borrowRecords: state.borrowRecords,
    borrowLoading: state.borrowLoading,
    borrowFeaturePlaceholder: state.borrowFeaturePlaceholder,
    loadBorrowRecords: vi.fn().mockResolvedValue({ success: true, data: state.borrowRecords }),
    submitBorrow: vi.fn().mockResolvedValue({ success: true }),
    returnBorrow: vi.fn().mockResolvedValue({ success: true })
  })
}))

vi.mock('@/api/client', () => ({
  default: {
    get: vi.fn(async (url) => {
      if (String(url).includes('/api/knowledge/qualifications?')) {
        return {
          data: {
            code: 200,
            data: {
              content: [
                {
                  id: 1,
                  name: 'AAA资质',
                  fileUrl: '/files/aaa.pdf',
                  level: 'FIRST',
                  issuer: '住建厅',
                  certificateNo: 'NO-001',
                  issueDate: '2026-01-01',
                  expiryDate: '2027-01-01',
                  status: 'IN_STOCK',
                  currentBorrowStatus: 'borrowed'
                }
              ],
              totalElements: 1
            }
          }
        }
      }
      if (String(url).includes('/check-borrow')) {
        return { data: { allowed: true, reason: '', borrowRecordId: 11 } }
      }
      return { data: { code: 200, data: {} } }
    }),
    post: vi.fn(async () => ({ data: { data: 1 } }))
  }
}))

vi.mock('@/api', async () => {
  const actual = await vi.importActual('@/api')
  return {
    ...actual,
    workflowFormApi: {
      getFormDefinition: vi.fn().mockResolvedValue({ data: { fields: state.workflowFields } })
    },
    qualificationsApi: {
      createBorrow: vi.fn().mockResolvedValue({ success: true }),
      returnBorrow: vi.fn().mockResolvedValue({ success: true })
    },
    isFeatureUnavailableResponse: vi.fn(() => false)
  }
})

vi.mock('element-plus', async () => {
  const actual = await vi.importActual('element-plus')
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
    ElMessageBox: {
      confirm: vi.fn().mockResolvedValue('confirm'),
      prompt: vi.fn().mockResolvedValue({ value: '下架原因' })
    }
  }
})

import Qualification from './Qualification.vue'

const stubs = {
  'el-card': { template: '<div><slot name="header" /><slot /></div>' },
  'el-form': { template: '<form><slot /></form>' },
  'el-form-item': { template: '<div><slot /></div>' },
  'el-input': { template: '<input />' },
  'el-select': { template: '<select><slot /></select>' },
  'el-option': { template: '<option><slot /></option>' },
  'el-date-picker': { template: '<input />' },
  'el-table': { template: '<div><slot /></div>' },
  'el-table-column': { template: '<div />' },
  'el-pagination': { template: '<div />' },
  'el-empty': { template: '<div />' },
  'el-tag': { template: '<span><slot /></span>' },
  'el-icon': { template: '<i><slot /></i>' },
  'el-alert': { template: '<div><slot /></div>' },
  'el-dialog': { props: ['modelValue'], template: '<div v-if="modelValue"><slot /><slot name="footer" /></div>' },
  'el-button': {
    props: ['type', 'loading', 'link', 'disabled'],
    emits: ['click'],
    template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>'
  },
  QualFormDialog: { template: '<div />' },
  AlertConfigDialog: { template: '<div />' },
  QualificationBorrowDialog: { template: '<div />' },
  QualificationBorrowHistoryCard: {
    props: ['records', 'loading', 'featurePlaceholder'],
    emits: ['create', 'return'],
    template: '<div class="borrow-history-card-stub">借阅记录</div>'
  }
}

function mountPage(roleCode) {
  state.roleCode = roleCode
  state.userRole = roleCode
  state.currentUser = { roleCode, role: roleCode, name: '测试用户' }

  return mount(Qualification, {
    global: {
      stubs,
      plugins: [createPinia()],
      directives: { loading: {} }
    }
  })
}

describe('Qualification.vue permissions', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('shows admin-only alert controls for bid_admin', async () => {
    const wrapper = mountPage('bid_admin')
    await flushPromises()

    expect(wrapper.text()).toContain('告警配置')
    expect(wrapper.text()).toContain('扫描到期')
    expect(wrapper.text()).toContain('借阅')
    expect(wrapper.text()).toContain('记录')
  })

  it('hides admin-only alert controls for bid_lead but keeps manage and borrow actions', async () => {
    const wrapper = mountPage('bid_lead')
    await flushPromises()

    expect(wrapper.text()).toContain('新增资质')
    expect(wrapper.text()).toContain('下载导入模板')
    expect(wrapper.text()).toContain('借阅')
    expect(wrapper.text()).toContain('记录')
    expect(wrapper.text()).not.toContain('告警配置')
    expect(wrapper.text()).not.toContain('扫描到期')
  })

  it('shows only view-and-borrow actions for bid_specialist', async () => {
    const wrapper = mountPage('bid_specialist')
    await flushPromises()

    expect(wrapper.text()).toContain('导出台账')
    expect(wrapper.text()).toContain('借阅')
    expect(wrapper.text()).toContain('记录')
    expect(wrapper.text()).not.toContain('新增资质')
    expect(wrapper.text()).not.toContain('下载导入模板')
    expect(wrapper.text()).not.toContain('告警配置')
    expect(wrapper.text()).not.toContain('扫描到期')
  })
})
