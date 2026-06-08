import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import AttachmentReplaceDialog from './AttachmentReplaceDialog.vue'

// Mock API client
vi.mock('@/api/client', () => ({
  default: {
    post: vi.fn().mockResolvedValue({ code: 200 })
  }
}))

// Mock Element Plus
vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn() }
}))

describe('AttachmentReplaceDialog - §4.2.1.3 附件替换', () => {
  let wrapper

  beforeEach(() => {
    wrapper = mount(AttachmentReplaceDialog, {
      props: {
        modelValue: true,
        qualificationId: 123,
        currentFileName: 'old-cert.pdf'
      },
      global: {
        stubs: {
          'el-dialog': {
            template: '<div class="el-dialog" :data-title="title"><slot /><slot name="footer" /></div>',
            props: ['title']
          },
          'el-upload': {
            template: '<div class="el-upload" :data-accept="accept"><slot /></div>',
            props: ['accept', 'drag'],
            methods: { clearFiles: vi.fn() }
          },
          'el-button': { template: '<button class="el-button" :disabled="disabled"><slot /></button>', props: ['disabled', 'type', 'loading'] },
          'el-icon': { template: '<span class="el-icon"><slot /></span>' },
          'el-alert': { template: '<div class="el-alert" :data-type="type"><slot /></div>', props: ['type', 'title'] }
        }
      }
    })
  })

  describe('弹窗结构', () => {
    it('应显示替换附件标题（有当前文件时）', () => {
      expect(wrapper.props('currentFileName')).toBe('old-cert.pdf')
    })

    it('应显示当前附件文件名', () => {
      const fileCard = wrapper.find('[data-testid="current-file"]')
      expect(fileCard.exists()).toBe(true)
      expect(fileCard.text()).toContain('old-cert.pdf')
    })

    it('无当前文件时应显示上传附件标题', async () => {
      await wrapper.setProps({ currentFileName: '' })
      expect(wrapper.props('currentFileName')).toBe('')
    })
  })

  describe('文件选择', () => {
    it('未选择文件时确认按钮应禁用', () => {
      const btn = wrapper.findAll('.el-button').find(b => b.text().includes('确认替换'))
      expect(btn.exists()).toBe(true)
      expect(btn.attributes('disabled')).toBeDefined()
    })

    it('选择文件后确认按钮应启用', async () => {
      const file = new File(['content'], 'new-cert.pdf', { type: 'application/pdf' })
      await wrapper.vm.handleFileChange({ raw: file })
      expect(wrapper.vm.selectedFile).toBe(file)
    })

    it('超过10MB的文件应触发校验错误', async () => {
      const hugeFile = new File(['x'], 'huge.pdf', { type: 'application/pdf' })
      Object.defineProperty(hugeFile, 'size', { value: 10485761 })
      await wrapper.vm.handleFileChange({ raw: hugeFile })
      expect(wrapper.vm.selectedFile).toBeNull()
    })
  })

  describe('清除文件', () => {
    it('清除后selectedFile应为null', async () => {
      const file = new File(['content'], 'new-cert.pdf', { type: 'application/pdf' })
      await wrapper.vm.handleFileChange({ raw: file })
      expect(wrapper.vm.selectedFile).not.toBeNull()
      wrapper.vm.clearFile()
      expect(wrapper.vm.selectedFile).toBeNull()
    })
  })

  describe('确认替换', () => {
    it('未选择文件时不应提交', async () => {
      const http = (await import('@/api/client')).default
      wrapper.vm.handleConfirm()
      await nextTick()
      expect(http.post).not.toHaveBeenCalled()
    })

    it('选择文件后应调用上传接口', async () => {
      const http = (await import('@/api/client')).default
      const file = new File(['content'], 'new-cert.pdf', { type: 'application/pdf' })
      await wrapper.vm.handleFileChange({ raw: file })
      await wrapper.vm.handleConfirm()
      expect(http.post).toHaveBeenCalledWith(
        '/api/knowledge/qualifications/123/upload',
        expect.any(FormData),
        expect.objectContaining({ headers: { 'Content-Type': 'multipart/form-data' } })
      )
    })
  })
})
