import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises } from '@vue/test-utils'

// CO-367: 标书审核人不能选择自己
// CO-381: 投标文件阶段只读守卫（BID_DOCUMENT 列表回填 + 下载按阶段守卫）
const mockCurrentUser = { id: 42, role: '/bidAdmin', menuPermissions: [] }

vi.mock('@/stores/user.js', () => ({
  useUserStore: () => ({
    get userRole() { return mockCurrentUser.role },
    hasPermission: (key) => mockCurrentUser.menuPermissions.includes(key),
    currentUser: mockCurrentUser,
    token: 'fake-token',
  }),
}))

const getDraftingMock = vi.fn(() => Promise.resolve({ data: {} }))
const getDocumentsMock = vi.fn(() => Promise.resolve({ data: [] }))

vi.mock('@/api/modules/projectLifecycle.js', () => ({
  projectLifecycleApi: {
    getDrafting: (...args) => getDraftingMock(...args),
    submitBidForReview: vi.fn(),
    approveBid: vi.fn(),
    rejectBid: vi.fn(),
    submitBid: vi.fn(),
  },
}))

vi.mock('@/api/modules/projectDocuments.js', () => ({
  getDocuments: (...args) => getDocumentsMock(...args),
  deleteDocument: vi.fn(),
  getDocumentDownloadUrl: (projectId, documentId) => `/api/projects/${projectId}/documents/${documentId}/download`,
}))

const downloadWithFilenameMock = vi.fn()
vi.mock('@/utils/download.js', () => ({
  downloadWithFilename: (...args) => downloadWithFilenameMock(...args),
}))

vi.mock('@/api/config.js', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    getApiUrl: (path) => `http://test${path}`,
  }
})
vi.mock('@/constants/projectStages.js', () => ({ STAGE_TRANSITION_MAP: { DRAFTING: 'EVALUATING' } }))
vi.mock('element-plus', () => ({ ElMessage: { info: vi.fn(), warning: vi.fn(), error: vi.fn(), success: vi.fn() } }))
vi.mock('@element-plus/icons-vue', () => ({
  DocumentChecked: {}, MagicStick: {}, Search: {}, Trophy: {}, UploadFilled: {},
}))

// 提供 projectDetail context：项目参与者不包含当前用户 42
vi.mock('@/composables/projectDetail/context.js', () => ({
  useProjectDetailContext: () => ({
    project: { value: { managerId: 1, teamMembers: [2], primaryLeadUserId: 3, secondaryLeadUserId: 4 } },
    userStore: { currentUser: mockCurrentUser },
    bidAgent: {},
    bidDocQualityResult: { value: null },
    runBidDocumentQualityCheck: () => {},
  }),
}))

const stubs = {
  ProjectDocumentTable: { template: '<div />' },
  UserPicker: {
    name: 'UserPicker',
    props: ['excludeIds', 'modelValue', 'mode', 'initialOptions', 'placeholder', 'clearable'],
    template: '<div data-test="picker" />',
  },
  ElCard: { template: '<section><slot name="header" /><slot /></section>' },
  ElUpload: {
    name: 'ElUpload',
    props: { fileList: { type: Array, default: () => [] }, disabled: Boolean },
    // CO-381: 用 div + v-for 包 slot，避免 <template v-for> 在 stub 中渲染不出 a 链接
    template: `<div class="mock-upload">
      <div v-for="(file, idx) in fileList" :key="file.uid || file.name || file.id || idx" class="mock-upload-row">
        <slot name="file" :file="file" />
      </div>
    </div>`,
  },
  ElButton: { props: ['loading', 'disabled'], template: '<button><slot /></button>' },
  ElAlert: { template: '<div />' },
  ElDialog: { template: '<div />' },
  ElInput: { template: '<input />' },
  ElCheckbox: { template: '<input type="checkbox" />' },
  AiRecommendDrawer: { template: '<div />' },
  PerformanceRecommendDrawer: { template: '<div />' },
  QualityCheckDialog: { template: '<div />' },
}

async function mountDraftingStage(props = {}) {
  const { default: DraftingStage } = await import('./DraftingStage.vue')
  const wrapper = mount(DraftingStage, { props: { projectId: 1, ...props }, global: { stubs } })
  // CO-381: onMounted(load) 内部串行 await getDrafting + loadBidFiles，必须 flushPromises 才能让 bidFiles 回填
  await flushPromises()
  await nextTick()
  return wrapper
}

describe('DraftingStage reviewerExcludeIds - CO-367', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getDraftingMock.mockReset()
    getDocumentsMock.mockReset()
    downloadWithFilenameMock.mockReset()
    getDraftingMock.mockImplementation(() => Promise.resolve({ data: {} }))
    getDocumentsMock.mockImplementation(() => Promise.resolve({ data: [] }))
  })

  it('排除当前登录用户，避免选择自己作为标书审核人', async () => {
    const wrapper = await mountDraftingStage()
    const picker = wrapper.findComponent({ name: 'UserPicker' })
    expect(picker.exists()).toBe(true)
    const excludeIds = picker.props('excludeIds')
    // 当前用户 ID 42 必须在排除列表中
    expect(excludeIds).toContain(42)
  })

  it('同时排除项目参与者（项目经理/团队成员/主副负责人）', async () => {
    const wrapper = await mountDraftingStage()
    const picker = wrapper.findComponent({ name: 'UserPicker' })
    const excludeIds = picker.props('excludeIds')
    // 项目参与者：1, 2, 3, 4 + 当前用户 42
    expect(excludeIds).toEqual(expect.arrayContaining([1, 2, 3, 4, 42]))
    expect(excludeIds.length).toBe(5)
  })
})

// CO-381: 投标文件阶段只读守卫
describe('DraftingStage bidFiles 持久化与下载阶段守卫 - CO-381', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getDraftingMock.mockReset()
    getDocumentsMock.mockReset()
    downloadWithFilenameMock.mockReset()
    getDraftingMock.mockImplementation(() => Promise.resolve({ data: {} }))
    getDocumentsMock.mockImplementation(() => Promise.resolve({ data: [] }))
    // 还原角色（CO-381 第 4 个用例改成 bid-administration，避免泄漏到后续用例）
    mockCurrentUser.role = '/bidAdmin'
  })

  it('load() 应拉取 BID_DOCUMENT 列表并回填 bidFiles，刷新后文件名仍展示', async () => {
    // 模拟后端返回 2 个 BID_DOCUMENT
    getDocumentsMock.mockImplementation(() => Promise.resolve({
      data: [
        { id: 3001, name: '投标文件_v1.pdf', documentCategory: 'BID_DOCUMENT' },
        { id: 3002, name: '技术方案.docx', documentCategory: 'BID_DOCUMENT' },
      ],
    }))

    const wrapper = await mountDraftingStage({ currentStage: 'DRAFTING' })

    // 验证 getDocuments 被调用且传了 documentCategory=BID_DOCUMENT
    expect(getDocumentsMock).toHaveBeenCalledWith(1, { documentCategory: 'BID_DOCUMENT' })

    // 验证 ElUpload 收到 fileList 长度为 2
    const upload = wrapper.findComponent({ name: 'ElUpload' })
    expect(upload.props('fileList')).toHaveLength(2)
    expect(upload.props('fileList')[0].name).toBe('投标文件_v1.pdf')
    expect(upload.props('fileList')[0].response.data.id).toBe(3001)

    // 验证 <a> 链接渲染了文件名
    const links = wrapper.findAll('.upload-file-link')
    expect(links).toHaveLength(2)
    expect(links[0].text()).toBe('投标文件_v1.pdf')
  })

  it('DRAFTING 阶段 + 有下载权限：点击文件名触发下载', async () => {
    getDocumentsMock.mockImplementation(() => Promise.resolve({
      data: [{ id: 3001, name: '投标文件.pdf', documentCategory: 'BID_DOCUMENT' }],
    }))

    const wrapper = await mountDraftingStage({ currentStage: 'DRAFTING' })

    const link = wrapper.find('.upload-file-link')
    expect(link.exists()).toBe(true)
    await link.trigger('click')

    expect(downloadWithFilenameMock).toHaveBeenCalledTimes(1)
    // 第一个参数是 URL，含 documentId=3001
    const [url, fallbackName] = downloadWithFilenameMock.mock.calls[0]
    expect(url).toContain('/documents/3001/download')
    expect(fallbackName).toBe('投标文件.pdf')
  })

  it('EVALUATING 阶段：点击文件名不触发下载（文件只读）', async () => {
    getDocumentsMock.mockImplementation(() => Promise.resolve({
      data: [{ id: 3001, name: '投标文件.pdf', documentCategory: 'BID_DOCUMENT' }],
    }))

    const wrapper = await mountDraftingStage({ currentStage: 'EVALUATING' })

    const link = wrapper.find('.upload-file-link')
    expect(link.exists()).toBe(true)
    // 文件名仍可见
    expect(link.text()).toBe('投标文件.pdf')
    // 但点击不触发下载
    await link.trigger('click')
    expect(downloadWithFilenameMock).not.toHaveBeenCalled()
  })

  it('DRAFTING 阶段 + 无下载权限（bid-administration 角色）：点击文件名不触发下载', async () => {
    // bid-administration（行政人员）的 roleGroup 为 null，canDownloadDocument = false
    mockCurrentUser.role = 'bid-administration'
    getDocumentsMock.mockImplementation(() => Promise.resolve({
      data: [{ id: 3001, name: '投标文件.pdf', documentCategory: 'BID_DOCUMENT' }],
    }))

    const wrapper = await mountDraftingStage({ currentStage: 'DRAFTING' })

    const link = wrapper.find('.upload-file-link')
    expect(link.exists()).toBe(true)
    await link.trigger('click')
    expect(downloadWithFilenameMock).not.toHaveBeenCalled()

    // 还原角色
    mockCurrentUser.role = '/bidAdmin'
  })

  it('DRAFTING 阶段：上传和删除按钮在 bidDone=false 时仍可用（保护现有功能）', async () => {
    getDocumentsMock.mockImplementation(() => Promise.resolve({
      data: [{ id: 3001, name: '投标文件.pdf', documentCategory: 'BID_DOCUMENT' }],
    }))

    const wrapper = await mountDraftingStage({ currentStage: 'DRAFTING' })

    // ElUpload 不应被 disabled（bidDone=false + canManageBidFiles=true，因为 /bidAdmin 角色）
    const upload = wrapper.findComponent({ name: 'ElUpload' })
    expect(upload.props('disabled')).toBe(false)

    // 删除按钮应存在
    const deleteBtn = wrapper.find('button')
    expect(deleteBtn.exists()).toBe(true)
  })
})

// CO-382: 删除按钮守卫——仅"上传后、提交前"允许删除
// 业务规则：reviewState === null（未提交审核）或 'rejected'（被驳回，可修改后重提）时显示删除按钮；
//          'reviewing'（审核中）/ 'approved'（已通过）/ bidDone（已投标）时隐藏
describe('DraftingStage 删除按钮提交前守卫 - CO-382', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getDraftingMock.mockReset()
    getDocumentsMock.mockReset()
    downloadWithFilenameMock.mockReset()
    getDocumentsMock.mockImplementation(() => Promise.resolve({
      data: [{ id: 3001, name: '投标文件.pdf', documentCategory: 'BID_DOCUMENT' }],
    }))
    mockCurrentUser.role = '/bidAdmin'
  })

  it('reviewState=null（未提交审核）：删除按钮可见', async () => {
    getDraftingMock.mockImplementation(() => Promise.resolve({
      data: { reviewStatus: null }
    }))

    const wrapper = await mountDraftingStage({ currentStage: 'DRAFTING' })
    await flushPromises()

    const deleteBtns = wrapper.findAll('button')
    const deleteBtn = deleteBtns.find(b => b.text() === '删除')
    expect(deleteBtn?.exists()).toBe(true)
  })

  it('reviewState=reviewing（审核中）：删除按钮隐藏', async () => {
    getDraftingMock.mockImplementation(() => Promise.resolve({
      data: { reviewStatus: 'reviewing' }
    }))

    const wrapper = await mountDraftingStage({ currentStage: 'DRAFTING' })
    await flushPromises()

    const deleteBtns = wrapper.findAll('button')
    const deleteBtn = deleteBtns.find(b => b.text() === '删除')
    expect(deleteBtn).toBeUndefined()
  })

  it('reviewState=approved（已通过审核）：删除按钮隐藏', async () => {
    getDraftingMock.mockImplementation(() => Promise.resolve({
      data: { reviewStatus: 'approved' }
    }))

    const wrapper = await mountDraftingStage({ currentStage: 'DRAFTING' })
    await flushPromises()

    const deleteBtns = wrapper.findAll('button')
    const deleteBtn = deleteBtns.find(b => b.text() === '删除')
    expect(deleteBtn).toBeUndefined()
  })

  it('reviewState=rejected（被驳回）：删除按钮可见（允许修改后重提）', async () => {
    getDraftingMock.mockImplementation(() => Promise.resolve({
      data: { reviewStatus: 'rejected' }
    }))

    const wrapper = await mountDraftingStage({ currentStage: 'DRAFTING' })
    await flushPromises()

    const deleteBtns = wrapper.findAll('button')
    const deleteBtn = deleteBtns.find(b => b.text() === '删除')
    expect(deleteBtn?.exists()).toBe(true)
  })

  it('lead_assist 角色（bid-projectLeader）：删除按钮隐藏（无删除权限，防止 403 UX 不一致）', async () => {
    // CO-382 review: canManageBidFiles 范围比 canDeleteDocument 宽，删除按钮应使用 canDeleteDocument
    mockCurrentUser.role = 'bid-projectLeader'
    getDraftingMock.mockImplementation(() => Promise.resolve({
      data: { reviewStatus: null }
    }))

    const wrapper = await mountDraftingStage({ currentStage: 'DRAFTING' })
    await flushPromises()

    const deleteBtns = wrapper.findAll('button')
    const deleteBtn = deleteBtns.find(b => b.text() === '删除')
    expect(deleteBtn).toBeUndefined()

    // 还原角色
    mockCurrentUser.role = '/bidAdmin'
  })
})
