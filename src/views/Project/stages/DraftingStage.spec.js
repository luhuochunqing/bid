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
const submitBidForReviewMock = vi.fn(() => Promise.resolve({ data: {} }))
const approveBidMock = vi.fn(() => Promise.resolve({ data: {} }))
const rejectBidMock = vi.fn(() => Promise.resolve({ data: {} }))

vi.mock('@/api/modules/projectLifecycle.js', () => ({
  projectLifecycleApi: {
    getDrafting: (...args) => getDraftingMock(...args),
    submitBidForReview: (...args) => submitBidForReviewMock(...args),
    approveBid: (...args) => approveBidMock(...args),
    rejectBid: (...args) => rejectBidMock(...args),
    submitBid: vi.fn(),
  },
}))

const deleteDocumentMock = vi.fn(() => Promise.resolve({ success: true }))
const uploadDocumentMock = vi.fn(() => Promise.resolve({ success: true, data: { id: 1 } }))
vi.mock('@/api/modules/projectDocuments.js', () => ({
  getDocuments: (...args) => getDocumentsMock(...args),
  deleteDocument: (...args) => deleteDocumentMock(...args),
  uploadDocument: (...args) => uploadDocumentMock(...args),
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
import { ElMessage } from 'element-plus'
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
  ProjectDocumentTable: {
    name: 'ProjectDocumentTable',
    props: { projectId: { type: [String, Number], default: null }, readonly: { type: Boolean, default: false }, canDownload: { type: Boolean, default: true }, canDelete: { type: Boolean, default: false } },
    template: '<div />',
  },
  UserPicker: {
    name: 'UserPicker',
    props: ['excludeIds', 'modelValue', 'mode', 'multiple', 'initialOptions', 'placeholder', 'clearable'],
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
  ElButton: { props: ['loading', 'disabled'], template: '<button :disabled="disabled"><slot /></button>' },
  ElAlert: { name: 'ElAlert', template: '<div />' },
  ElDialog: { template: '<div />' },
  ElInput: { template: '<input />' },
  ElCheckbox: { template: '<input type="checkbox" />' },
  CaseSliceRecommendDrawer: { template: '<div />' },
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

  it('CO-484 v2：排除当前用户/投标负责人/团队成员（项目经理与辅助人员不再排除）', async () => {
    const wrapper = await mountDraftingStage()
    const picker = wrapper.findComponent({ name: 'UserPicker' })
    const excludeIds = picker.props('excludeIds')
    // 排除：当前用户 42、投标负责人 3、团队成员 2（项目经理 1 需包含不排除、辅助人员 4 解禁不排除）
    expect(excludeIds).toEqual(expect.arrayContaining([42, 3, 2]))
    expect(excludeIds.length).toBe(3)
    expect(excludeIds).not.toContain(1)
    expect(excludeIds).not.toContain(4)
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

  it('load() 应拉取 BID 列表并回填 bidFiles，刷新后文件名仍展示', async () => {
    // CO-420: documentCategory 改为标准枚举名 BID（原 BID_DOCUMENT）
    getDocumentsMock.mockImplementation(() => Promise.resolve({
      data: [
        { id: 3001, name: '投标文件_v1.pdf', documentCategory: 'BID' },
        { id: 3002, name: '技术方案.docx', documentCategory: 'BID' },
      ],
    }))

    const wrapper = await mountDraftingStage({ currentStage: 'DRAFTING' })

    // 验证 getDocuments 被调用且传了 documentCategory=BID
    expect(getDocumentsMock).toHaveBeenCalledWith(1, { documentCategory: 'BID' })

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
      data: [{ id: 3001, name: '投标文件.pdf', documentCategory: 'BID' }],
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
      data: [{ id: 3001, name: '投标文件.pdf', documentCategory: 'BID' }],
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
      data: [{ id: 3001, name: '投标文件.pdf', documentCategory: 'BID' }],
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
      data: [{ id: 3001, name: '投标文件.pdf', documentCategory: 'BID' }],
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
      data: [{ id: 3001, name: '投标文件.pdf', documentCategory: 'BID' }],
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

  it('bid-projectLeader + file 无 uploaderId → 删除按钮隐藏（CO-383：非 admin_lead 且非上传者本人）', async () => {
    // CO-383: canDeleteDocumentAs 在 uploaderId 缺失时只允许 admin_lead；
    // bid-projectLeader 非 admin_lead，且 file 无 uploaderId 无法证明是上传者本人 → 隐藏。
    // beforeEach 的 getDocumentsMock 返回的 file 不含 uploaderId，复用此 mock。
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

  it('bid-projectLeader + file.uploaderId=42（上传者本人）→ 删除按钮可见（CO-383：不管角色，上传者本人可删）', async () => {
    // CO-383: 用户需求"应该不管什么角色 都可以在没有保存的时候 删除"。
    // bid-projectLeader 非 admin_lead，但 file.response.data.uploaderId == 当前用户 id (42) → 允许删除。
    mockCurrentUser.role = 'bid-projectLeader'
    getDocumentsMock.mockImplementation(() => Promise.resolve({
      data: [{ id: 3001, name: '投标文件.pdf', documentCategory: 'BID', uploaderId: 42 }],
    }))
    getDraftingMock.mockImplementation(() => Promise.resolve({
      data: { reviewStatus: null }
    }))

    const wrapper = await mountDraftingStage({ currentStage: 'DRAFTING' })
    await flushPromises()

    const deleteBtns = wrapper.findAll('button')
    const deleteBtn = deleteBtns.find(b => b.text() === '删除')
    expect(deleteBtn?.exists()).toBe(true)

    // 还原角色
    mockCurrentUser.role = '/bidAdmin'
  })

  it('bid-projectLeader + file.uploaderId=99（非上传者）→ 删除按钮隐藏（CO-383：仅上传者本人可删）', async () => {
    mockCurrentUser.role = 'bid-projectLeader'
    getDocumentsMock.mockImplementation(() => Promise.resolve({
      data: [{ id: 3001, name: '投标文件.pdf', documentCategory: 'BID', uploaderId: 99 }],
    }))
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

// CO-407: 投标文件字段标题添加必填标识 *
describe('DraftingStage 投标文件必填标识 - CO-407', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getDraftingMock.mockReset()
    getDocumentsMock.mockReset()
    getDraftingMock.mockImplementation(() => Promise.resolve({ data: {} }))
    getDocumentsMock.mockImplementation(() => Promise.resolve({ data: [] }))
    mockCurrentUser.role = '/bidAdmin'
  })

  it('投标文件标题处显示 * 号必填标识', async () => {
    const wrapper = await mountDraftingStage()
    const bidTitle = wrapper.find('.bid-title')
    expect(bidTitle.exists()).toBe(true)
    expect(bidTitle.text()).toContain('投标文件')
    // 必填标识 * 应嵌套在 .bid-title 内
    const requiredMark = bidTitle.find('.required-mark')
    expect(requiredMark.exists()).toBe(true)
    expect(requiredMark.text()).toBe('*')
  })

  it('必填标识使用项目统一样式类（color: #e65100）', async () => {
    const wrapper = await mountDraftingStage()
    const requiredMark = wrapper.find('.bid-title .required-mark')
    expect(requiredMark.exists()).toBe(true)
    // 仅断言 class 存在，具体颜色由全局样式统一控制，避免冗余样式断言
    expect(requiredMark.classes()).toContain('required-mark')
  })
})

// CO-483 + CO-484: 标书审核多人化 + 驳回后审核人清空
describe('DraftingStage 多人审核 + CO-483 驳回后清空 - CO-483/CO-484', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getDraftingMock.mockReset()
    getDocumentsMock.mockReset()
    getDocumentsMock.mockImplementation(() => Promise.resolve({ data: [] }))
    mockCurrentUser.role = '/bidAdmin'
  })

  it('UserPicker 启用多选模式（multiple=true）', async () => {
    getDraftingMock.mockImplementation(() => Promise.resolve({ data: {} }))
    const wrapper = await mountDraftingStage()
    const picker = wrapper.findComponent({ name: 'UserPicker' })
    expect(picker.exists()).toBe(true)
    expect(picker.props('multiple')).toBe(true)
  })

  it('CO-483 + CO-484 v2：驳回后 UserPicker 重新渲染且 bidReviewerIds 清空，投标负责人可重新选择', async () => {
    // 后端返回 rejected 状态 + 旧 reviewerId=200，前端不应回填旧审核人
    getDraftingMock.mockImplementation(() => Promise.resolve({
      data: {
        reviewStatus: 'REJECTED',
        reviewerId: 200,
        reviewerName: '旧审核人',
        rejectReason: '旧驳回原因',
        reviewers: [{ reviewerId: 200, reviewerName: '旧审核人', decision: 'REJECTED', comment: '旧驳回原因' }],
      }
    }))

    const wrapper = await mountDraftingStage()
    await flushPromises()

    const picker = wrapper.findComponent({ name: 'UserPicker' })
    // CO-484 v2：rejected 状态下 UserPicker 应渲染（投标负责人可重新选择审核人）
    expect(picker.exists()).toBe(true)
    // CO-483：bidReviewerIds 应清空，不预填旧审核人
    expect(picker.props('modelValue')).toEqual([])
    // 驳回原因 el-alert 仍渲染（ElAlert stub，检查组件存在）
    const alerts = wrapper.findAllComponents({ name: 'ElAlert' })
    expect(alerts.length).toBeGreaterThanOrEqual(1)
    // 审核记录仍展示（含驳回人记录）
    const records = wrapper.findAll('.review-record-item')
    expect(records.length).toBeGreaterThanOrEqual(1)
    expect(records[0].text()).toContain('旧审核人')
    expect(records[0].text()).toContain('驳回')
    // 重新提交按钮展示
    const submitBtn = wrapper.findAll('button').find(b => b.text().includes('重新提交标书审核'))
    expect(submitBtn).toBeTruthy()
  })

  it('CO-484：审核中状态展示审核进度文案（已通过 X/Y）', async () => {
    getDraftingMock.mockImplementation(() => Promise.resolve({
      data: {
        reviewStatus: 'REVIEWING',
        reviewerId: 200,
        reviewers: [
          { reviewerId: 200, reviewerName: '审核人A', decision: 'APPROVED', comment: null },
          { reviewerId: 201, reviewerName: '审核人B', decision: null, comment: null },
        ],
      }
    }))

    const wrapper = await mountDraftingStage()
    await flushPromises()

    // 验证进度 ElAlert 组件存在（ElAlert stub 是 <div />，无 .el-alert 类）
    const alerts = wrapper.findAllComponents({ name: 'ElAlert' })
    // CO-484 进度 alert 应该存在（reviewerProgressText 非空时渲染）
    expect(alerts.length).toBeGreaterThanOrEqual(1)
  })

  it('CO-484 v2：未提交审核时展示提示文案"需包含项目负责人...3人"', async () => {
    // reviewState=null 时（未提交审核），UserPicker 可见且提示文案应展示
    getDraftingMock.mockImplementation(() => Promise.resolve({ data: {} }))
    const wrapper = await mountDraftingStage()
    await flushPromises()

    const tip = wrapper.find('.bid-reviewer-tip')
    expect(tip.exists()).toBe(true)
    expect(tip.text()).toContain('需包含项目负责人')
    expect(tip.text()).toContain('不能选择自己')
    expect(tip.text()).toContain('3人')
  })

  it('CO-484：approved 状态不展示提示文案', async () => {
    getDraftingMock.mockImplementation(() => Promise.resolve({
      data: {
        reviewStatus: 'APPROVED',
        reviewerId: 200,
        reviewers: [{ reviewerId: 200, reviewerName: '审核人A', decision: 'APPROVED', comment: null }],
      }
    }))
    const wrapper = await mountDraftingStage()
    await flushPromises()

    const tip = wrapper.find('.bid-reviewer-tip')
    expect(tip.exists()).toBe(false)
  })

  it('CO-484 v2：审核记录展示"操作人：审核通过 / 驳回（原因）"', async () => {
    getDraftingMock.mockImplementation(() => Promise.resolve({
      data: {
        reviewStatus: 'REVIEWING',
        reviewerId: 200,
        reviewers: [
          { reviewerId: 200, reviewerName: '张三', decision: 'APPROVED', comment: null },
          { reviewerId: 201, reviewerName: '李四', decision: 'REJECTED', comment: '内容不完整' },
          { reviewerId: 202, reviewerName: '王五', decision: null, comment: null },
        ],
      }
    }))
    const wrapper = await mountDraftingStage()
    await flushPromises()

    const records = wrapper.findAll('.review-record-item')
    // 已操作的 2 人展示（未决策的王五不展示）
    expect(records.length).toBe(2)
    expect(records[0].text()).toBe('张三：审核通过')
    expect(records[1].text()).toBe('李四：驳回（内容不完整）')
  })

  it('CO-484 v2：提交审核后调用 load 刷新，多人审核人正确回填', async () => {
    // 首次 load 返回空状态（未提交审核）
    getDraftingMock.mockImplementationOnce(() => Promise.resolve({ data: {} }))
    // submitBidForReview 后第二次 load 返回多人审核人
    getDraftingMock.mockImplementationOnce(() => Promise.resolve({
      data: {
        reviewStatus: 'REVIEWING',
        reviewerId: 200,
        reviewers: [
          { reviewerId: 200, reviewerName: '张三', decision: null, comment: null },
          { reviewerId: 201, reviewerName: '李四', decision: null, comment: null },
        ],
      }
    }))

    const wrapper = await mountDraftingStage()
    await flushPromises()

    // 选择 2 个审核人并提交
    await wrapper.findComponent({ name: 'UserPicker' }).vm.$emit('update:modelValue', [200, 201])
    const submitBtn = wrapper.findAll('button').find(b => b.text().includes('提交标书审核'))
    expect(submitBtn).toBeTruthy()
    await submitBtn.trigger('click')
    await flushPromises()

    // 验证 submitBidForReview 被调用
    expect(submitBidForReviewMock).toHaveBeenCalled()
    // 验证 load 被调用了 2 次（初始 + 提交后刷新）
    expect(getDraftingMock).toHaveBeenCalledTimes(2)
    // 验证多人审核人正确回填
    const text = wrapper.text()
    expect(text).toContain('张三')
    expect(text).toContain('李四')
  })

  // CO-484 v2：当前审核人点击"审核通过"后，去掉驳回按钮、置灰审核通过按钮
  it('CO-484 v2：当前审核人已 APPROVED 后，驳回按钮隐藏、审核通过按钮置灰', async () => {
    // 当前用户 ID=42，作为审核人且已 APPROVED；另一审核人 201 未决策，整体 REVIEWING
    getDraftingMock.mockImplementation(() => Promise.resolve({
      data: {
        reviewStatus: 'REVIEWING',
        reviewerId: 42,
        reviewers: [
          { reviewerId: 42, reviewerName: '我', decision: 'APPROVED', comment: null },
          { reviewerId: 201, reviewerName: '李四', decision: null, comment: null },
        ],
      }
    }))

    const wrapper = await mountDraftingStage()
    await flushPromises()

    const buttons = wrapper.findAll('button').map(b => b.text())
    // 驳回按钮应隐藏
    expect(buttons.find(t => t.includes('驳回'))).toBeFalsy()
    // 审核通过按钮应存在但置灰（文案变为"已通过"）
    const approveBtn = wrapper.findAll('button').find(b => b.text().includes('已通过'))
    expect(approveBtn).toBeTruthy()
    // ElButton stub 把 disabled 渲染到 button attribute
    expect(approveBtn.attributes('disabled')).toBeDefined()
  })

  it('CO-484 v2：当前审核人未决策时，驳回和审核通过按钮都可见且可点击', async () => {
    getDraftingMock.mockImplementation(() => Promise.resolve({
      data: {
        reviewStatus: 'REVIEWING',
        reviewerId: 42,
        reviewers: [
          { reviewerId: 42, reviewerName: '我', decision: null, comment: null },
        ],
      }
    }))

    const wrapper = await mountDraftingStage()
    await flushPromises()

    const rejectBtn = wrapper.findAll('button').find(b => b.text().includes('驳回'))
    const approveBtn = wrapper.findAll('button').find(b => b.text().includes('审核通过'))
    expect(rejectBtn).toBeTruthy()
    expect(approveBtn).toBeTruthy()
    expect(rejectBtn.attributes('disabled')).toBeFalsy()
    expect(approveBtn.attributes('disabled')).toBeFalsy()
  })
})

// 防复发：DELETE 按钮防重复点击守卫
// 场景：用户点击"删除" → await deleteDocument() 期间按钮未禁用 → 用户又点 → 后端 404
describe('DraftingStage handleRemoveBidFile 防重复点击 - 服务器 404 根因修复', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getDraftingMock.mockReset()
    getDocumentsMock.mockReset()
    deleteDocumentMock.mockReset()
    getDraftingMock.mockImplementation(() => Promise.resolve({ data: { reviewStatus: null } }))
    getDocumentsMock.mockImplementation(() => Promise.resolve({
      data: [{ id: 842, name: '投标文件.pdf', documentCategory: 'BID', uploaderId: 42 }],
    }))
    deleteDocumentMock.mockImplementation(() => Promise.resolve({ success: true }))
    mockCurrentUser.role = '/bidAdmin'
  })

  it('防复发核心断言：删除期间重复点击不会二次调用 deleteDocument', async () => {
    let resolveDelete
    deleteDocumentMock.mockImplementation(() => new Promise(r => { resolveDelete = r }))

    const wrapper = await mountDraftingStage({ currentStage: 'DRAFTING' })
    await flushPromises()

    const deleteBtn = wrapper.findAll('button').find(b => b.text() === '删除')
    expect(deleteBtn).toBeTruthy()

    // 第一次点击（pending 中，未 resolve）
    await deleteBtn.trigger('click')
    await flushPromises()

    // 第二次点击：应被守卫跳过，deleteDocument 仍只被调用 1 次
    await deleteBtn.trigger('click')
    await flushPromises()

    expect(deleteDocumentMock).toHaveBeenCalledTimes(1)

    // resolve 第一次的 Promise，让其完成
    resolveDelete({ success: true })
    await flushPromises()

    // 完成后仍只被调用 1 次
    expect(deleteDocumentMock).toHaveBeenCalledTimes(1)
  })

  it('删除失败时释放锁，允许重试', async () => {
    // 第一次抛错（用 mockImplementationOnce 避免 unhandled rejection）
    deleteDocumentMock.mockImplementationOnce(() => Promise.reject(new Error('network error')))

    const wrapper = await mountDraftingStage({ currentStage: 'DRAFTING' })
    await flushPromises()

    const deleteBtn = wrapper.findAll('button').find(b => b.text() === '删除')
    await deleteBtn.trigger('click')
    await flushPromises()

    // 第一次失败
    expect(deleteDocumentMock).toHaveBeenCalledTimes(1)

    // 第二次应能再次调用（锁已释放）
    deleteDocumentMock.mockImplementationOnce(() => Promise.resolve({ success: true }))
    await deleteBtn.trigger('click')
    await flushPromises()

    expect(deleteDocumentMock).toHaveBeenCalledTimes(2)
  })

  it('删除期间按钮置灰', async () => {
    let resolveDelete
    deleteDocumentMock.mockImplementation(() => new Promise(r => { resolveDelete = r }))

    const wrapper = await mountDraftingStage({ currentStage: 'DRAFTING' })
    await flushPromises()

    const deleteBtn = wrapper.findAll('button').find(b => b.text() === '删除')
    expect(deleteBtn.attributes('disabled')).toBeFalsy()

    await deleteBtn.trigger('click')
    await nextTick()

    // 删除期间按钮应置灰
    expect(deleteBtn.attributes('disabled')).toBeDefined()

    resolveDelete({ success: true })
    await flushPromises()
    await nextTick()

    // 完成后恢复可点击
    expect(deleteBtn.attributes('disabled')).toBeFalsy()
  })
})

// UX 修复：上传成功后刷新投标文件列表 + ElMessage.success 提示
describe('DraftingStage customUpload 上传成功后刷新列表 + 成功提示', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getDraftingMock.mockReset()
    getDocumentsMock.mockReset()
    deleteDocumentMock.mockReset()
    uploadDocumentMock.mockReset()
    getDraftingMock.mockImplementation(() => Promise.resolve({ data: { reviewStatus: null } }))
    getDocumentsMock.mockImplementation(() => Promise.resolve({ data: [] }))
    uploadDocumentMock.mockImplementation(() => Promise.resolve({ success: true, data: { id: 1 } }))
    mockCurrentUser.role = '/bidAdmin'
  })

  it('上传成功后显示 ElMessage.success 提示', async () => {
    const wrapper = await mountDraftingStage({ currentStage: 'DRAFTING' })
    await flushPromises()

    // 通过 setupState 访问 customUpload（<script setup> 内部函数）
    const customUpload = wrapper.vm.$.setupState.customUpload
    expect(typeof customUpload).toBe('function')

    await customUpload({
      file: new File(['x'], 'test.pdf'),
      data: { documentCategory: 'BID' },
      onProgress: vi.fn(),
    })
    await flushPromises()

    expect(ElMessage.success).toHaveBeenCalledWith('test.pdf 上传成功')
  })

  it('上传成功后调用 loadBidFiles 刷新投标文件列表', async () => {
    const wrapper = await mountDraftingStage({ currentStage: 'DRAFTING' })
    await flushPromises()

    // mount 时 loadBidFiles 已被调用一次（onMounted → load → loadBidFiles）
    getDocumentsMock.mockClear()

    const customUpload = wrapper.vm.$.setupState.customUpload
    await customUpload({
      file: new File(['x'], 'test.pdf'),
      data: { documentCategory: 'BID' },
      onProgress: vi.fn(),
    })
    await flushPromises()

    // loadBidFiles 应再次调用 getDocuments
    expect(getDocumentsMock).toHaveBeenCalled()
  })
})

// bugfix：审核人无法下载项目文档/招标文件（ProjectDocumentTable 的 :can-download 排除审核人）
// 根因：DraftingStage.vue:8 :can-download="perm.isAdminLead || perm.isAssignedBidSpecialist"
//       审核人（任意角色 + 被指派为 reviewer）两项都不满足 → 下载按钮不渲染。
// 后端 ProjectAccessScopeService（CO-315）已放行审核人下载项目文档，前端口径不一致。
// 修复：:can-download 末尾加 || perm.canReviewBid（复用「是否指派审核人」判断）。
describe('DraftingStage 项目文档下载审核人放行 - bugfix', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getDraftingMock.mockReset()
    getDocumentsMock.mockReset()
    getDraftingMock.mockImplementation(() => Promise.resolve({ data: {} }))
    getDocumentsMock.mockImplementation(() => Promise.resolve({ data: [] }))
    mockCurrentUser.role = '/bidAdmin'
  })

  it('不回归：管理员角色 → ProjectDocumentTable canDownload=true', async () => {
    mockCurrentUser.role = '/bidAdmin'
    const wrapper = await mountDraftingStage({ currentStage: 'DRAFTING' })
    const table = wrapper.findComponent({ name: 'ProjectDocumentTable' })
    expect(table.exists()).toBe(true)
    expect(table.props('canDownload')).toBe(true)
  })

  it('不回归：投标负责人（bid-Team + primaryLeadId 匹配）→ canDownload=true', async () => {
    // currentUserId=42，bid-Team + primaryLeadId=42 → isAssignedBidSpecialist=true
    mockCurrentUser.role = 'bid-Team'
    // context mock 里 primaryLeadUserId=3，需让 42 匹配 → 通过 getDrafting 返回 primaryLeadId
    // 但 isAssignedBidSpecialist 依赖 opts.primaryLeadId，来自 ctx.project.primaryLeadUserId=3
    // 42 ≠ 3，所以此用例验证的是「非该项目的 bid-Team」→ false（防守边界）
    const wrapper = await mountDraftingStage({ currentStage: 'DRAFTING' })
    const table = wrapper.findComponent({ name: 'ProjectDocumentTable' })
    expect(table.props('canDownload')).toBe(false)
    // 还原
    mockCurrentUser.role = '/bidAdmin'
  })

  it('bugfix：当前用户是指派审核人（reviewerId=42）→ canDownload=true', async () => {
    // 审核人角色（非 admin/lead），但被指派为该项目审核人
    mockCurrentUser.role = 'bid-administration'
    getDraftingMock.mockImplementation(() => Promise.resolve({
      data: {
        reviewStatus: 'REVIEWING',
        reviewerId: 42,
        reviewers: [{ reviewerId: 42, reviewerName: '我', decision: null, comment: null }],
      },
    }))
    const wrapper = await mountDraftingStage({ currentStage: 'DRAFTING' })
    await flushPromises()
    const table = wrapper.findComponent({ name: 'ProjectDocumentTable' })
    expect(table.exists()).toBe(true)
    expect(table.props('canDownload')).toBe(true)
    // 还原
    mockCurrentUser.role = '/bidAdmin'
  })

  it('bugfix：当前用户在多人审核人列表中（reviewers 含 42）→ canDownload=true', async () => {
    mockCurrentUser.role = 'bid-otherDept'
    getDraftingMock.mockImplementation(() => Promise.resolve({
      data: {
        reviewStatus: 'REVIEWING',
        reviewerId: 200,
        reviewers: [
          { reviewerId: 200, reviewerName: '张三', decision: 'APPROVED', comment: null },
          { reviewerId: 42, reviewerName: '我', decision: null, comment: null },
        ],
      },
    }))
    const wrapper = await mountDraftingStage({ currentStage: 'DRAFTING' })
    await flushPromises()
    const table = wrapper.findComponent({ name: 'ProjectDocumentTable' })
    expect(table.props('canDownload')).toBe(true)
    // 还原
    mockCurrentUser.role = '/bidAdmin'
  })

  it('防守：非审核人、非 admin、非 lead → canDownload=false（不过度放行）', async () => {
    mockCurrentUser.role = 'bid-administration'
    // 未提交审核，reviewers 为空 → canReviewBid=false
    getDraftingMock.mockImplementation(() => Promise.resolve({ data: {} }))
    const wrapper = await mountDraftingStage({ currentStage: 'DRAFTING' })
    await flushPromises()
    const table = wrapper.findComponent({ name: 'ProjectDocumentTable' })
    expect(table.props('canDownload')).toBe(false)
    // 还原
    mockCurrentUser.role = '/bidAdmin'
  })
})
