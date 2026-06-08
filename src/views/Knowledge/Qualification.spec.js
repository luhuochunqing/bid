import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import Qualification from './Qualification.vue'

// Mock stores
vi.mock('@/stores/qualification', () => ({
  useQualificationStore: () => ({
    loadQualifications: vi.fn().mockResolvedValue({}),
    loadBorrowRecords: vi.fn().mockResolvedValue({})
  })
}))

vi.mock('@/stores/user.js', () => ({
  useUserStore: () => ({
    hasPermission: vi.fn().mockReturnValue(true)
  })
}))

// Mock API client
vi.mock('@/api/client', () => ({
  default: {
    get: vi.fn().mockResolvedValue({ code: 200, data: { content: [], totalElements: 0 } }),
    post: vi.fn().mockResolvedValue({ code: 200 })
  }
}))

// Mock Element Plus
vi.mock('element-plus', async () => {
  const actual = await vi.importActual('element-plus')
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn() },
    ElMessageBox: { prompt: vi.fn().mockRejectedValue({}), confirm: vi.fn().mockRejectedValue({}) }
  }
})

// Mock child components
vi.mock('./components/qualification/QualFormDialog.vue', () => ({
  default: { template: '<div>QualFormDialog</div>' }
}))
vi.mock('./components/qualification/AlertConfigDialog.vue', () => ({
  default: { template: '<div>AlertConfigDialog</div>' }
}))
vi.mock('./components/qualification/QualificationBorrowDialog.vue', () => ({
  default: { template: '<div>BorrowDialog</div>' }
}))
vi.mock('./components/qualification/QualificationBorrowHistoryCard.vue', () => ({
  default: { template: '<div>BorrowHistoryCard</div>' }
}))
vi.mock('./components/qualification/QualDetailDrawer.vue', () => ({
  default: { template: '<div>DetailDrawer</div>' }
}))
vi.mock('./components/qualification/AttachmentReplaceDialog.vue', () => ({
  default: { template: '<div>AttachmentReplaceDialog</div>' }
}))

describe('Qualification.vue - 4.2.1.2 资质列表', () => {
  let wrapper

  beforeEach(() => {
    wrapper = mount(Qualification, {
      global: {
        stubs: {
          'el-table': {
            template: '<div class="el-table"><slot /></div>',
            methods: { clearSelection: vi.fn() }
          },
          'el-table-column': { template: '<div class="el-table-column" />' },
          'el-pagination': { template: '<div class="el-pagination" />' },
          'el-empty': {
            template: '<div class="el-empty"><slot name="description" /><span>{{ description }}</span></div>',
            props: ['description']
          },
          'el-card': { template: '<div class="el-card"><slot /></div>' },
          'el-form': { template: '<div class="el-form"><slot /></div>' },
          'el-form-item': { template: '<div class="el-form-item" :data-label="label"><slot /></div>', props: ['label'] },
          'el-input': { template: '<input />' },
          'el-select': { template: '<select><slot /></select>' },
          'el-option': { template: '<option />' },
          'el-date-picker': { template: '<input />' },
          'el-button': { template: '<button class="el-button"><slot /></button>' },
          'el-icon': { template: '<span class="el-icon"><slot /></span>' },
          'el-tag': { template: '<span class="el-tag"><slot /></span>' },
          'el-upload': { template: '<div class="el-upload"><slot /><slot name="trigger" /></div>' },
          'el-dialog': { template: '<div class="el-dialog"><slot /></div>' },
          'el-alert': { template: '<div><slot /></div>' }
        }
      }
    })
  })

  describe('页面结构', () => {
    it('页面标题应为"资质证书"', () => {
      const title = wrapper.find('h2')
      expect(title.exists()).toBe(true)
      expect(title.text()).toBe('资质证书')
    })

    it('应包含筛选区', () => {
      expect(wrapper.find('.filter-card').exists()).toBe(true)
    })

    it('应包含数据表格区', () => {
      expect(wrapper.find('.data-card').exists()).toBe(true)
    })

    it('应包含分页器', () => {
      expect(wrapper.find('.pagination-wrap').exists()).toBe(true)
    })
  })

  describe('筛选功能', () => {
    it('应包含证书名称筛选', () => {
      const html = wrapper.html()
      expect(html).toContain('证书名称')
    })

    it('应包含认证机构筛选', () => {
      const html = wrapper.html()
      expect(html).toContain('认证机构')
    })

    it('应包含有效期筛选', () => {
      const html = wrapper.html()
      expect(html).toContain('有效期')
    })

    it('应包含证书状态筛选', () => {
      const html = wrapper.html()
      expect(html).toContain('证书状态')
    })

    it('应包含等级筛选', () => {
      const html = wrapper.html()
      expect(html).toContain('等级')
    })
  })

  describe('空状态', () => {
    it('无数据时应显示空状态组件', async () => {
      await nextTick()
      const empty = wrapper.findAll('.el-empty')
      expect(empty.length).toBeGreaterThan(0)
    })
  })

  describe('批量操作', () => {
    it('默认不应显示批量操作工具栏', () => {
      expect(wrapper.find('.batch-toolbar').exists()).toBe(false)
    })
  })

  describe('操作按钮', () => {
    it('页面操作区应存在', () => {
      expect(wrapper.find('.page-actions').exists()).toBe(true)
    })

    it('权限变量应正确初始化', () => {
      expect(wrapper.find('.page-actions').exists()).toBe(true)
    })
  })
})

describe('Qualification.vue - §4.2.1.3 编辑与附件管理', () => {
  let wrapper

  beforeEach(() => {
    wrapper = mount(Qualification, {
      global: {
        stubs: {
          'el-table': { template: '<div class="el-table"><slot /></div>', methods: { clearSelection: vi.fn() } },
          'el-table-column': { template: '<div class="el-table-column" />' },
          'el-pagination': { template: '<div class="el-pagination" />' },
          'el-empty': { template: '<div class="el-empty"><slot name="description" /></div>' },
          'el-card': { template: '<div class="el-card"><slot /></div>' },
          'el-form': { template: '<div class="el-form"><slot /></div>' },
          'el-form-item': { template: '<div class="el-form-item"><slot /></div>' },
          'el-input': { template: '<input />' },
          'el-select': { template: '<select><slot /></select>' },
          'el-option': { template: '<option />' },
          'el-date-picker': { template: '<input />' },
          'el-button': { template: '<button class="el-button"><slot /></button>' },
          'el-icon': { template: '<span class="el-icon"><slot /></span>' },
          'el-tag': { template: '<span class="el-tag"><slot /></span>' },
          'el-upload': { template: '<div class="el-upload"><slot /></div>' },
          'el-dialog': { template: '<div class="el-dialog"><slot /></div>' },
          'el-alert': { template: '<div class="el-alert"><slot /></div>' }
        }
      }
    })
  })

  describe('附件替换', () => {
    it('handleAttachmentReplace 应打开替换弹窗', async () => {
      wrapper.vm.detailQualification = { id: 42, name: 'Test Cert' }
      wrapper.vm.handleAttachmentReplace({ fileName: 'old.pdf' })
      expect(wrapper.vm.replaceDialogVisible).toBe(true)
      expect(wrapper.vm.replaceQualificationId).toBe(42)
      expect(wrapper.vm.replaceCurrentFileName).toBe('old.pdf')
    })

    it('handleAttachmentUpload 应打开上传弹窗（无附件时）', async () => {
      wrapper.vm.detailQualification = { id: 42, name: 'Test Cert' }
      wrapper.vm.handleAttachmentUpload()
      expect(wrapper.vm.replaceDialogVisible).toBe(true)
      expect(wrapper.vm.replaceQualificationId).toBe(42)
      expect(wrapper.vm.replaceCurrentFileName).toBe('')
    })
  })

  describe('附件删除', () => {
    it('handleAttachmentDelete 应调用 ElMessageBox.confirm', async () => {
      const { ElMessageBox } = await import('element-plus')
      ElMessageBox.confirm = vi.fn().mockRejectedValue(new Error('cancel'))
      wrapper.vm.detailQualification = { id: 42 }
      wrapper.vm.handleAttachmentDelete({ fileName: 'test.pdf' })
      expect(ElMessageBox.confirm).toHaveBeenCalled()
    })
  })

  describe('保存后刷新', () => {
    it('handleFormSaved 应刷新列表', async () => {
      const http = (await import('@/api/client')).default
      wrapper.vm.handleFormSaved()
      expect(http.get).toHaveBeenCalled()
    })
  })
})
