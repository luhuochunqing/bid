import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import ElementPlus from 'element-plus'

// CO-408: 复盘阶段 load 时根据 reportFileIds 回填 reportFiles
// 用真实 el-upload（plugins:[ElementPlus]），不能用 stub——
// v-model:file-list 在自定义 stub 下 prop 传递异常。
const mockCurrentUser = { id: 42, role: '/bidAdmin' }

vi.mock('@/stores/user', () => ({
  useUserStore: () => ({
    get userRole() { return mockCurrentUser.role },
    currentUser: mockCurrentUser,
    token: 'fake-token',
  }),
}))
vi.mock('@/utils/permission', () => ({ isBidManager: (role) => role === '/bidAdmin' || role === 'admin' }))

vi.mock('@/api/config.js', async (importOriginal) => {
  const actual = await importOriginal()
  return { ...actual, getApiUrl: (path) => `http://test${path}` }
})

const getDocumentsMock = vi.fn()
vi.mock('@/api/modules/projectDocuments.js', () => ({
  getDocuments: (...args) => getDocumentsMock(...args),
}))

const getRetrospectiveMock = vi.fn()
const submitRetrospectiveMock = vi.fn()
vi.mock('@/api/modules/projectLifecycle.js', () => ({
  projectLifecycleApi: {
    getRetrospective: (...args) => getRetrospectiveMock(...args),
    submitRetrospective: (...args) => submitRetrospectiveMock(...args),
  },
}))

vi.mock('./retrospectiveLossReasons.js', () => ({ lossReasonOptions: [] }))

const { elMessageWarningMock } = vi.hoisted(() => ({ elMessageWarningMock: vi.fn() }))
vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal()
  return { ...actual, ElMessage: { info: vi.fn(), warning: elMessageWarningMock, error: vi.fn(), success: vi.fn() } }
})

describe('RetrospectiveStage CO-408 回填复盘报告文件名', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getDocumentsMock.mockReset()
    getRetrospectiveMock.mockReset()
  })

  it('load 时根据 reportFileIds 拉取项目文档并回填 reportFiles', async () => {
    getRetrospectiveMock.mockImplementation(() => Promise.resolve({
      success: true,
      data: { meetingTime: '2026-06-29 10:00:00', reportFileIds: [3001, 3002], reviewStatus: null },
    }))
    getDocumentsMock.mockImplementation(() => Promise.resolve({
      data: [
        { id: 3001, name: '复盘报告.docx' },
        { id: 3002, name: '改进措施.pdf' },
        { id: 3003, name: '无关文件.docx' },
      ],
    }))

    const { default: RetrospectiveStage } = await import('./RetrospectiveStage.vue')
    const wrapper = mount(RetrospectiveStage, {
      props: { projectId: 1, resultType: 'WON' },
      global: { plugins: [ElementPlus] },
    })
    await flushPromises()
    await nextTick()
    await flushPromises()

    expect(getRetrospectiveMock).toHaveBeenCalledWith(1)
    expect(getDocumentsMock).toHaveBeenCalledWith(1)
    const fileList = wrapper.findComponent({ name: 'ElUpload' }).props('fileList')
    expect(fileList).toHaveLength(2)
    expect(fileList[0].name).toBe('复盘报告.docx')
    expect(fileList[0].response.data.id).toBe(3001)
    expect(fileList[1].name).toBe('改进措施.pdf')
    // DOM 渲染层文件名可见
    expect(wrapper.text()).toContain('复盘报告.docx')
    expect(wrapper.text()).toContain('改进措施.pdf')
  })

  it('reportFileIds 为空时不回填 reportFiles', async () => {
    getRetrospectiveMock.mockImplementation(() => Promise.resolve({
      success: true, data: { meetingTime: '', reportFileIds: [] },
    }))
    const { default: RetrospectiveStage } = await import('./RetrospectiveStage.vue')
    const wrapper = mount(RetrospectiveStage, {
      props: { projectId: 1, resultType: 'WON' },
      global: { plugins: [ElementPlus] },
    })
    await flushPromises()
    expect(wrapper.findComponent({ name: 'ElUpload' }).props('fileList')).toHaveLength(0)
    expect(getDocumentsMock).not.toHaveBeenCalled()
  })
})

describe('RetrospectiveStage CO-475 复盘报告必填', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getDocumentsMock.mockReset()
    getRetrospectiveMock.mockReset()
    submitRetrospectiveMock.mockReset()
    elMessageWarningMock.mockReset()
  })

  it('WON 时未上传复盘报告应提示用户且不调用提交接口', async () => {
    getRetrospectiveMock.mockImplementation(() => Promise.resolve({
      success: true,
      data: {
        meetingTime: '2026-06-29 10:00:00',
        meetingFormat: 'ONLINE',
        meetingParticipants: '张三,李四',
        winFactors: '优势',
        processHighlights: '亮点',
        postWinImprovements: '建议',
        reportFileIds: [],
        reviewStatus: null,
      },
    }))

    const { default: RetrospectiveStage } = await import('./RetrospectiveStage.vue')
    const wrapper = mount(RetrospectiveStage, {
      props: { projectId: 1, resultType: 'WON' },
      global: { plugins: [ElementPlus] },
    })
    await flushPromises()
    await nextTick()
    await flushPromises()

    await wrapper.find('.btn-container button').trigger('click')
    await flushPromises()

    expect(elMessageWarningMock).toHaveBeenCalledWith('请上传复盘报告')
    expect(submitRetrospectiveMock).not.toHaveBeenCalled()
  })

  it('LOST 时未上传复盘报告应提示用户且不调用提交接口', async () => {
    getRetrospectiveMock.mockImplementation(() => Promise.resolve({
      success: true,
      data: {
        meetingTime: '2026-06-29 10:00:00',
        meetingFormat: 'ONLINE',
        meetingParticipants: '张三,李四',
        lossReasonFlags: ['NOT_IN_TARGET_LIST'],
        processProblems: '问题',
        postLossMeasures: '措施',
        reportFileIds: [],
        reviewStatus: null,
      },
    }))

    const { default: RetrospectiveStage } = await import('./RetrospectiveStage.vue')
    const wrapper = mount(RetrospectiveStage, {
      props: { projectId: 1, resultType: 'LOST' },
      global: { plugins: [ElementPlus] },
    })
    await flushPromises()
    await nextTick()
    await flushPromises()

    await wrapper.find('.btn-container button').trigger('click')
    await flushPromises()

    expect(elMessageWarningMock).toHaveBeenCalledWith('请上传复盘报告')
    expect(submitRetrospectiveMock).not.toHaveBeenCalled()
  })
})
