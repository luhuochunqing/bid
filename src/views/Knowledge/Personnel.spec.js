import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { nextTick, ref } from 'vue'
import Personnel from './Personnel.vue'
import { certStatusLabel, certStatusTagType } from './components/personnel/personnelConstants.js'

// Mock Element Plus
vi.mock('element-plus', async () => {
  const actual = await vi.importActual('element-plus')
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
    ElMessageBox: { confirm: vi.fn().mockResolvedValue(true), prompt: vi.fn() }
  }
})

// Mock API
vi.mock('@/api/modules/personnel.js', () => ({
  default: {
    getList: vi.fn().mockResolvedValue({ data: [], code: 200 }),
    getOperationLogs: vi.fn().mockResolvedValue({ data: [], code: 200 })
  }
}))

vi.mock('@/api/modules/personnelBatchApi.js', () => ({
  default: {
    downloadImportTemplate: vi.fn().mockResolvedValue({}),
    startImport: vi.fn().mockResolvedValue({ data: { taskId: '1' } }),
    getImportProgress: vi.fn().mockResolvedValue({ data: { status: 'COMPLETED', progressPercent: 100 } }),
    downloadErrorReport: vi.fn().mockResolvedValue({}),
    startExport: vi.fn().mockResolvedValue({ data: { taskId: '1' } }),
    getExportProgress: vi.fn().mockResolvedValue({ data: { status: 'COMPLETED', progressPercent: 100, totalCount: 0 } }),
    downloadExportFile: vi.fn().mockResolvedValue({}),
    batchAttachAttachments: vi.fn().mockResolvedValue({ data: { successCount: 0, failedCount: 0, unmatchedFiles: [] } })
  }
}))

// Mock user store
vi.mock('@/stores/user.js', () => ({
  useUserStore: () => ({ userRole: 'bid_admin', currentUser: { role: 'bid_admin' } })
}))

// Mock qualification store
vi.mock('@/stores/qualification', () => ({
  useQualificationStore: () => ref({})
}))

describe('Personnel.vue - 4.3.1.3', () => {
  let wrapper

  beforeEach(() => {
    wrapper = mount(Personnel, {
      global: {
        stubs: {
          'el-button': true,
          'el-icon': true,
          'el-card': true,
          'el-form': true,
          'el-form-item': true,
          'el-input': true,
          'el-select': true,
          'el-option': true,
          'el-date-picker': true,
          'el-table': true,
          'el-table-column': true,
          'el-tag': { props: ['type'], template: '<span class="el-tag"><slot /></span>' },
          'el-drawer': true,
          'el-tabs': true,
          'el-tab-pane': true,
          'el-descriptions': true,
          'el-descriptions-item': true,
          'el-dialog': true,
          'el-upload': true,
          'el-checkbox': true,
          'el-row': true,
          'el-col': true,
          'Plus': true,
          'Warning': true,
          'Download': true,
          'Upload': true,
          'Link': true
        }
      }
    })
  })

  describe('证书状态标签', () => {
    it('certStatusLabel 应正确映射四种状态', () => {
      expect(certStatusLabel('VALID')).toBe('有效')
      expect(certStatusLabel('EXPIRING')).toBe('即将到期')
      expect(certStatusLabel('EXPIRED')).toBe('已过期')
      expect(certStatusLabel('PERMANENT')).toBe('永久有效')
      expect(certStatusLabel('UNKNOWN')).toBe('UNKNOWN')
      expect(certStatusLabel('')).toBe('—')
    })

    it('certStatusTagType 应返回正确的标签类型', () => {
      expect(certStatusTagType('VALID')).toBe('success')
      expect(certStatusTagType('EXPIRING')).toBe('warning')
      expect(certStatusTagType('EXPIRED')).toBe('danger')
      expect(certStatusTagType('PERMANENT')).toBe('primary')
      expect(certStatusTagType('UNKNOWN')).toBe('info')
    })
  })

  describe('权限控制', () => {
    it('bid_admin 应有新增权限', () => {
      expect(wrapper.vm.canAdd).toBe(true)
    })

    it('bid_admin 应有导入导出权限', () => {
      expect(wrapper.vm.canImportExport).toBe(true)
    })

    it('bid_admin 应有批量操作权限', () => {
      expect(wrapper.vm.canBatch).toBe(true)
    })
  })

  describe('筛选器', () => {
    it('resetFilters 应重置所有筛选条件', () => {
      wrapper.vm.filters.keyword = 'test'
      wrapper.vm.filters.status = 'ACTIVE'
      wrapper.vm.resetFilters()
      expect(wrapper.vm.filters.keyword).toBe('')
      expect(wrapper.vm.filters.status).toBe('')
    })
  })
})
