import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import ElementPlus from 'element-plus'

// CO-408: 评标文件再次进入页面时根据 existingDocIds 回填 fileList
// 注意：必须用真实 el-upload（plugins:[ElementPlus]），不能用 stub——
// v-model:file-list 在自定义 stub 下 prop 传递异常（原 trae spec 6/6 失败的根因）。
const mockCurrentUser = { id: 42, role: '/bidAdmin' }

vi.mock('@/stores/user.js', () => ({
  useUserStore: () => ({
    get userRole() { return mockCurrentUser.role },
    currentUser: mockCurrentUser,
    token: 'fake-token',
  }),
}))

vi.mock('@/api/config.js', async (importOriginal) => {
  const actual = await importOriginal()
  return { ...actual, getApiUrl: (path) => `http://test${path}` }
})

const getDocumentsMock = vi.fn()
vi.mock('@/api/modules/projectDocuments.js', () => ({
  getDocuments: (...args) => getDocumentsMock(...args),
}))

vi.mock('@/api/modules/projectLifecycle.js', () => ({
  projectLifecycleApi: { attachEvaluationEvidence: vi.fn(() => Promise.resolve()) },
}))

// 只 mock ElMessage（避免调用真实通知 API），其余 element-plus 用真实组件
vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal()
  return { ...actual, ElMessage: { info: vi.fn(), warning: vi.fn(), error: vi.fn(), success: vi.fn() } }
})

describe('EvaluationEvidenceUpload CO-408 回填评标文件名', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getDocumentsMock.mockReset()
  })

  it('existingDocIds 非空时根据 ids 拉取项目文档并回填 fileList', async () => {
    // 项目下有 3 个文档，existingDocIds 指定 2 个为评标文件
    getDocumentsMock.mockImplementation(() => Promise.resolve({
      data: [
        { id: 3001, name: '开标一览表_v1.pdf' },
        { id: 3002, name: '评标记录.docx' },
        { id: 3003, name: '其他文件.pdf' },
      ],
    }))

    const { default: EvaluationEvidenceUpload } = await import('./EvaluationEvidenceUpload.vue')
    const wrapper = mount(EvaluationEvidenceUpload, {
      props: { projectId: 1, existingDocIds: [], editable: true },
      global: { plugins: [ElementPlus] },
    })
    await flushPromises()

    // 模拟父组件 load 完成后异步赋值 existingDocIds
    await wrapper.setProps({ existingDocIds: [3001, 3002] })
    await flushPromises()
    await nextTick()
    await flushPromises()

    expect(getDocumentsMock).toHaveBeenCalledWith(1)
    const upload = wrapper.findComponent({ name: 'ElUpload' })
    const fileList = upload.props('fileList')
    expect(fileList).toHaveLength(2)
    expect(fileList[0].name).toBe('开标一览表_v1.pdf')
    expect(fileList[0].response.data.id).toBe(3001)
    expect(fileList[1].name).toBe('评标记录.docx')
    // DOM 渲染层文件名可见（自定义 #file slot 用 file.name 渲染）
    expect(wrapper.text()).toContain('开标一览表_v1.pdf')
    expect(wrapper.text()).toContain('评标记录.docx')
  })

  it('existingDocIds 为空时 fileList 清空且不调用 getDocuments', async () => {
    const { default: EvaluationEvidenceUpload } = await import('./EvaluationEvidenceUpload.vue')
    const wrapper = mount(EvaluationEvidenceUpload, {
      props: { projectId: 1, existingDocIds: [], editable: true },
      global: { plugins: [ElementPlus] },
    })
    await flushPromises()
    expect(wrapper.findComponent({ name: 'ElUpload' }).props('fileList')).toHaveLength(0)
    // 不应调用 getDocuments（ids 为空时早返回）
    expect(getDocumentsMock).not.toHaveBeenCalled()
  })
})
