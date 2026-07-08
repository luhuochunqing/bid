import { ref } from 'vue'
import { shallowMount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const obsUpload = {
  uploading: ref(false),
  progress: ref(0),
  progressPercent: ref(0),
  currentFile: ref(null),
  error: ref(null),
  upload: vi.fn(),
  cancel: vi.fn(),
  reset: vi.fn(),
}

const refs = {
  importResult: ref(null),
  importing: ref(false),
  tenderFile: ref(null),
}

const bidAgent = {
  ...refs,
  selectedTenderFileName: ref(''),
  obsUpload,
  selectTenderFile: vi.fn(),
  clearTenderFile: vi.fn(),
  importTenderDocument: vi.fn(),
}

const context = { bidAgent }

vi.mock('@/composables/projectDetail/context.js', () => ({
  useProjectDetailContext: () => context,
}))

import ProjectDetailBidAgentTenderUpload from './ProjectDetailBidAgentTenderUpload.vue'

describe('ProjectDetailBidAgentTenderUpload', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    refs.importResult.value = null
    refs.importing.value = false
    refs.tenderFile.value = null
    bidAgent.selectedTenderFileName.value = ''
    obsUpload.uploading.value = false
    obsUpload.progressPercent.value = 0
    obsUpload.currentFile.value = null
    obsUpload.error.value = null
  })

  it('renders the upload section with placeholder text', () => {
    const wrapper = shallowMount(ProjectDetailBidAgentTenderUpload)
    expect(wrapper.find('.tender-upload-step').exists()).toBe(true)
    expect(wrapper.text()).toContain('选择')
  })

  it('shows the selected file name when a tender file is chosen', () => {
    bidAgent.selectedTenderFileName.value = '招标文件.docx'
    const wrapper = shallowMount(ProjectDetailBidAgentTenderUpload)
    expect(wrapper.text()).toContain('招标文件.docx')
  })

  it('displays import success alert when importResult has a document', () => {
    refs.importResult.value = { document: { name: '招标文件.docx', extractedTextLength: 1200 } }
    const wrapper = shallowMount(ProjectDetailBidAgentTenderUpload)
    const html = wrapper.html()
    expect(html).toContain('alert')
    expect(html).toContain('已解析')
  })

  it('renders ObsUploadProgress component', () => {
    const wrapper = shallowMount(ProjectDetailBidAgentTenderUpload)
    expect(wrapper.findComponent({ name: 'ObsUploadProgress' }).exists()).toBe(true)
  })
})
