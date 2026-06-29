import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import ElementPlus from 'element-plus'

// CO-408: 结果确认阶段 load 时根据 evidenceFileIds 回填 evidenceFiles
// 用真实 el-upload（plugins:[ElementPlus]），不能用 stub——
// v-model:file-list 在自定义 stub 下 prop 传递异常。
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

const getResultMock = vi.fn()
vi.mock('@/api/modules/projectLifecycle.js', () => ({
  projectLifecycleApi: {
    getResult: (...args) => getResultMock(...args),
    registerResult: vi.fn(),
  },
}))

vi.mock('@/constants/projectStages.js', () => ({ getResultConfirmNextTab: () => 'RETROSPECTIVE' }))

vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal()
  return { ...actual, ElMessage: { info: vi.fn(), warning: vi.fn(), error: vi.fn(), success: vi.fn() } }
})

describe('ResultConfirmStage CO-408 回填凭证文件名', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getDocumentsMock.mockReset()
    getResultMock.mockReset()
  })

  it('load 时根据 evidenceFileIds 拉取项目文档并回填 evidenceFiles', async () => {
    getResultMock.mockImplementation(() => Promise.resolve({
      data: { resultType: 'WON', evidenceFileIds: [3001, 3002], competitors: [] },
    }))
    getDocumentsMock.mockImplementation(() => Promise.resolve({
      data: [
        { id: 3001, name: '中标通知书.pdf' },
        { id: 3002, name: '合同副本.pdf' },
        { id: 3003, name: '无关文件.docx' },
      ],
    }))

    const { default: ResultConfirmStage } = await import('./ResultConfirmStage.vue')
    const wrapper = mount(ResultConfirmStage, {
      props: { projectId: 1 },
      global: { plugins: [ElementPlus] },
    })
    await flushPromises()
    await nextTick()
    await flushPromises()

    expect(getResultMock).toHaveBeenCalledWith(1)
    expect(getDocumentsMock).toHaveBeenCalledWith(1)
    const fileList = wrapper.findComponent({ name: 'ElUpload' }).props('fileList')
    expect(fileList).toHaveLength(2)
    expect(fileList[0].name).toBe('中标通知书.pdf')
    expect(fileList[0].response.data.id).toBe(3001)
    expect(fileList[1].name).toBe('合同副本.pdf')
    // DOM 渲染层文件名可见
    expect(wrapper.text()).toContain('中标通知书.pdf')
    expect(wrapper.text()).toContain('合同副本.pdf')
  })

  it('evidenceFileIds 为空时不回填 evidenceFiles', async () => {
    getResultMock.mockImplementation(() => Promise.resolve({ data: { resultType: 'WON', evidenceFileIds: [] } }))
    const { default: ResultConfirmStage } = await import('./ResultConfirmStage.vue')
    const wrapper = mount(ResultConfirmStage, {
      props: { projectId: 1 },
      global: { plugins: [ElementPlus] },
    })
    await flushPromises()
    expect(wrapper.findComponent({ name: 'ElUpload' }).props('fileList')).toHaveLength(0)
    expect(getDocumentsMock).not.toHaveBeenCalled()
  })
})
