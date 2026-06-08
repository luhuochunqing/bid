import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { nextTick, ref } from 'vue'
import Personnel from './Personnel.vue'

// Mock Element Plus 组件
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

// Mock user store
vi.mock('@/stores/user.js', () => ({
  useUserStore: () => ref({ userRole: 'bid_admin', currentUser: { role: 'bid_admin' } })
}))

// Mock qualification store
vi.mock('@/stores/qualification', () => ({
  useQualificationStore: () => ref({})
}))

describe('Personnel.vue - 4.3.1.3 查看证书', () => {
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
          'el-tag': {
            props: ['type'],
            template: '<span class="el-tag" :class="type"><slot /></span>'
          },
          'el-drawer': true,
          'el-tabs': true,
          'el-tab-pane': true,
          'el-descriptions': true,
          'el-descriptions-item': true,
          'el-link': true,
          'el-empty': true,
          'el-pagination': true,
          'el-alert': true,
          'el-dialog': true,
          'el-upload': true,
          'el-checkbox': true,
          'el-row': true,
          'el-col': true,
          'el-radio-group': true,
          'el-radio': true,
          'Plus': true,
          'Warning': true,
          'CircleClose': true
        }
      }
    })
  })

  describe('证书状态标签', () => {
    it('certStatusLabel 应正确映射四种状态', () => {
      expect(wrapper.vm.certStatusLabel('VALID')).toBe('有效')
      expect(wrapper.vm.certStatusLabel('EXPIRING')).toBe('即将到期')
      expect(wrapper.vm.certStatusLabel('EXPIRED')).toBe('已过期')
      expect(wrapper.vm.certStatusLabel('PERMANENT')).toBe('永久有效')
      expect(wrapper.vm.certStatusLabel('UNKNOWN')).toBe('UNKNOWN')
      expect(wrapper.vm.certStatusLabel('')).toBe('—')
    })

    it('certStatusTagType 应返回正确的标签类型', () => {
      expect(wrapper.vm.certStatusTagType('VALID')).toBe('success')
      expect(wrapper.vm.certStatusTagType('EXPIRING')).toBe('warning')
      expect(wrapper.vm.certStatusTagType('EXPIRED')).toBe('danger')
      expect(wrapper.vm.certStatusTagType('PERMANENT')).toBe('primary')
      expect(wrapper.vm.certStatusTagType('UNKNOWN')).toBe('info')
    })
  })

  describe('操作日志 Tab', () => {
    it('openDetail 传入 log Tab 时应加载操作日志', async () => {
      wrapper.vm.openDetail({ id: 1, name: '测试' }, 'log')
      await nextTick()
      // 验证 detailActiveTab 被设置为 log
      expect(wrapper.vm.detailActiveTab).toBe('log')
      expect(wrapper.vm.detailVisible).toBe(true)
    })

    it('operationLogs 初始应为空数组', () => {
      expect(wrapper.vm.operationLogs).toEqual([])
      expect(wrapper.vm.operationLogsLoading).toBe(false)
    })
  })
})
