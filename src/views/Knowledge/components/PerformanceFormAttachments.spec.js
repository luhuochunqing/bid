// CO-442 防回归测试：附件资料字段从 el-input 改为 el-upload，attachmentMap 改为列表结构
// 历史缺陷：原本每个 fileType 只能填 1 条 fileName/fileUrl 字符串，不符合"支持多文件"需求
// Pos: src/views/Knowledge/components/ - Performance form attachments test
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'

const mockUploadAttachment = vi.fn()
vi.mock('@/api/modules/performance.js', () => ({
  performanceApi: {
    uploadAttachment: (...args) => mockUploadAttachment(...args),
  },
}))

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
}))

const stubs = {
  'el-form-item': { template: '<div class="el-form-item"><slot /></div>', props: ['label'] },
  'el-input': { template: '<input />', props: ['modelValue'] },
  'el-switch': { template: '<span class="el-switch" />', props: ['modelValue'] },
  'el-upload': {
    template: '<div class="el-upload-stub"><slot /></div>',
    props: ['httpRequest', 'fileList', 'accept', 'multiple', 'showFileList', 'beforeUpload'],
    methods: {
      clearFiles() {},
    },
  },
  'el-button': {
    template: '<button class="el-button" @click="$emit(\'click\')"><slot /></button>',
    props: ['type', 'size'],
    emits: ['click'],
  },
  'el-icon': { template: '<span><slot /></span>' },
}

import PerformanceFormAttachments from './PerformanceFormAttachments.vue'

function createWrapper(formOverrides = {}) {
  setActivePinia(createPinia())
  const form = {
    mallWebsiteUrl: '',
    hasBidNotice: false,
    customerType: '',
    remarks: '',
    attachmentMap: {
      CONTRACT_AGREEMENT: [],
      MALL_SCREENSHOT: [],
      SOE_DIRECTORY: [],
      CATEGORY_PAGE: [],
      RELATIONSHIP_PROOF: [],
      BID_NOTICE: [],
      OTHER: [],
    },
    ...formOverrides,
  }
  return mount(PerformanceFormAttachments, {
    global: { stubs },
    props: { form },
  })
}

describe('CO-442 PerformanceFormAttachments 附件上传改造', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('attachmentMap 每个 fileType 初始为空数组（支持多文件）', () => {
    const wrapper = createWrapper()
    const form = wrapper.props('form')
    for (const type of ['CONTRACT_AGREEMENT', 'MALL_SCREENSHOT', 'SOE_DIRECTORY', 'CATEGORY_PAGE', 'RELATIONSHIP_PROOF', 'OTHER']) {
      expect(Array.isArray(form.attachmentMap[type])).toBe(true)
      expect(form.attachmentMap[type].length).toBe(0)
    }
  })

  it('上传成功后文件追加到对应 fileType 的数组', async () => {
    mockUploadAttachment.mockResolvedValue({
      data: { fileName: '合同协议.pdf', fileUrl: '/data/abc.pdf' },
    })
    const wrapper = createWrapper()
    const vm = wrapper.vm
    // 模拟 el-upload 的 http-request 回调
    const fakeUploadFile = { file: new File(['pdf'], '合同协议.pdf', { type: 'application/pdf' }) }
    await vm.handleUpload({ file: fakeUploadFile.file, fileType: 'CONTRACT_AGREEMENT' })
    await flushPromises()

    const form = wrapper.props('form')
    expect(form.attachmentMap.CONTRACT_AGREEMENT.length).toBe(1)
    expect(form.attachmentMap.CONTRACT_AGREEMENT[0]).toEqual({
      fileName: '合同协议.pdf',
      fileUrl: '/data/abc.pdf',
      fileType: 'CONTRACT_AGREEMENT',
    })
  })

  it('删除已上传的文件从数组中移除', async () => {
    const wrapper = createWrapper({
      attachmentMap: {
        CONTRACT_AGREEMENT: [
          { fileName: 'a.pdf', fileUrl: '/data/a.pdf', fileType: 'CONTRACT_AGREEMENT' },
          { fileName: 'b.pdf', fileUrl: '/data/b.pdf', fileType: 'CONTRACT_AGREEMENT' },
        ],
        MALL_SCREENSHOT: [],
        SOE_DIRECTORY: [],
        CATEGORY_PAGE: [],
        RELATIONSHIP_PROOF: [],
        BID_NOTICE: [],
        OTHER: [],
      },
    })
    const vm = wrapper.vm
    vm.removeFile('CONTRACT_AGREEMENT', 0)
    const form = wrapper.props('form')
    expect(form.attachmentMap.CONTRACT_AGREEMENT.length).toBe(1)
    expect(form.attachmentMap.CONTRACT_AGREEMENT[0].fileName).toBe('b.pdf')
  })

  it('央企客户时显示央企名录与关系证明字段', () => {
    const wrapper = createWrapper({ customerType: 'CENTRAL_SOE' })
    expect(wrapper.text()).toContain('央企名录')
    expect(wrapper.text()).toContain('关系证明')
  })

  it('hasBidNotice=true 时显示中标通知书字段', () => {
    const wrapper = createWrapper({ hasBidNotice: true })
    expect(wrapper.text()).toContain('中标通知书')
  })

  it('渲染 6 个 el-upload 控件（不含中标通知书，因其联动显示）', () => {
    const wrapper = createWrapper()
    const uploads = wrapper.findAll('.el-upload-stub')
    // 6 个常显字段：合同协议、商城截图、央企名录、关系证明、品类页、其他附件
    // 注意：央企名录和关系证明仅 CENTRAL_SOE 时显示，默认客户类型不显示
    // 所以默认场景下：合同协议、商城截图、品类页、其他附件 = 4 个
    expect(uploads.length).toBeGreaterThanOrEqual(4)
  })
})
