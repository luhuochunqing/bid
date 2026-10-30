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
const registerResultMock = vi.fn()
vi.mock('@/api/modules/projectLifecycle.js', () => ({
  projectLifecycleApi: {
    getResult: (...args) => getResultMock(...args),
    registerResult: (...args) => registerResultMock(...args),
  },
}))

// 竞品下拉走后端全局字典（specs/055），jsdom 中 mock 掉避免真实网络请求
vi.mock('@/api/modules/competitionIntel.js', () => ({
  competitionIntelApi: {
    getCompetitors: vi.fn(() => Promise.resolve({ data: [{ name: '华为' }, { name: '中兴' }] })),
    upsertCompetitor: vi.fn(() => Promise.resolve({})),
  },
}))

const checkPrecipitationReadinessMock = vi.fn()
const precipitateCasesMock = vi.fn()
const getPrecipitationTaskMock = vi.fn().mockResolvedValue(null)
vi.mock('@/api/modules/knowledge.js', () => ({
  casesApi: {
    checkPrecipitationReadiness: (...args) => checkPrecipitationReadinessMock(...args),
    precipitateCases: (...args) => precipitateCasesMock(...args),
    getPrecipitationTask: (...args) => getPrecipitationTaskMock(...args),
  },
}))

vi.mock('@/constants/projectStages.js', () => ({ getResultConfirmNextTab: () => 'RETROSPECTIVE' }))

const elMessageBoxConfirmMock = vi.fn()
vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    ElMessage: { info: vi.fn(), warning: vi.fn(), error: vi.fn(), success: vi.fn() },
    ElMessageBox: { confirm: (...args) => elMessageBoxConfirmMock(...args) },
  }
})

describe('ResultConfirmStage CO-408 回填凭证文件名', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getDocumentsMock.mockReset()
    getResultMock.mockReset()
  })

  it('刷新页面后直接展示案例失败原因，按钮恢复后仍保留说明', async () => {
    getResultMock.mockResolvedValue({ data: { resultType: 'WON', evidenceFileIds: [], competitors: [] } })
    getPrecipitationTaskMock.mockResolvedValueOnce({ status: 'FAILED', message: '案例生成失败：该项目缺少评分项。请先完成评分项解析，再重新生成。' })
    const { default: ResultConfirmStage } = await import('./ResultConfirmStage.vue')
    const wrapper = mount(ResultConfirmStage, { props: { projectId: 1 }, global: { plugins: [ElementPlus] } })
    await flushPromises()
    expect(wrapper.text()).toContain('该项目缺少评分项')
    expect(wrapper.text()).toContain('请先完成评分项解析')
    wrapper.unmount()
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

  it('结果已登记时：展示 AI 自动生成案例按钮并触发 checkPrecipitationReadiness', async () => {
    getResultMock.mockImplementation(() => Promise.resolve({
      data: { resultType: 'WON', registeredAt: '2026-08-25 12:00:00', evidenceFileIds: [] },
    }))
    checkPrecipitationReadinessMock.mockResolvedValue({
      data: { canPrecipitate: true, missingItems: [] },
    })

    const { default: ResultConfirmStage } = await import('./ResultConfirmStage.vue')
    const wrapper = mount(ResultConfirmStage, {
      props: { projectId: 1 },
      global: { plugins: [ElementPlus] },
    })
    await flushPromises()

    expect(checkPrecipitationReadinessMock).toHaveBeenCalledWith(1)
    expect(wrapper.text()).toContain('AI自动生成案例')
  })

  it('点击 AI 自动生成案例按钮时调用 casesApi.precipitateCases', async () => {
    getResultMock.mockImplementation(() => Promise.resolve({
      data: { resultType: 'WON', registeredAt: '2026-08-25 12:00:00', evidenceFileIds: [] },
    }))
    checkPrecipitationReadinessMock.mockResolvedValue({
      data: { canPrecipitate: true, missingItems: [] },
    })
    precipitateCasesMock.mockResolvedValue({ message: '案例沉淀任务已触发' })

    const { default: ResultConfirmStage } = await import('./ResultConfirmStage.vue')
    const wrapper = mount(ResultConfirmStage, {
      props: { projectId: 1 },
      global: { plugins: [ElementPlus] },
    })
    await flushPromises()

    await wrapper.vm.triggerPrecipitation()
    expect(precipitateCasesMock).toHaveBeenCalledWith(1)
  })
})

describe('ResultConfirmStage 结果登记二次确认（登记后竞品不可再修改）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getResultMock.mockReset()
    registerResultMock.mockReset()
    elMessageBoxConfirmMock.mockReset()
    // 默认桩：用户在弹窗点「返回检查」（取消）。确认场景用 mockResolvedValueOnce 覆盖
    elMessageBoxConfirmMock.mockRejectedValue(new Error('cancel'))
  })

  // 凭证上传链路在 jsdom 中难以模拟，直接写入 form 后点「登记结果」（form 已 defineExpose）
  async function mountAndFillForm(competitorRows = 1) {
    getResultMock.mockImplementation(() => Promise.resolve({
      data: { resultType: 'WON', evidenceFileIds: [], competitors: [] },
    }))
    const { default: ResultConfirmStage } = await import('./ResultConfirmStage.vue')
    const wrapper = mount(ResultConfirmStage, {
      props: { projectId: 1 },
      global: { plugins: [ElementPlus] },
    })
    await flushPromises()

    const vm = wrapper.vm
    vm.form.servicePeriodYears = 2
    vm.form.servicePeriodEndDate = '2026-12-31'
    vm.form.evidenceFileIds.push(9001)
    if (competitorRows > 0) vm.form.competitors[0].name = '华为'
    return wrapper
  }

  function findRegisterButton(wrapper) {
    const btn = wrapper.findAll('button').find(b => b.text().includes('登记结果'))
    expect(btn).toBeTruthy()
    return btn
  }

  it('点击「登记结果」先弹二次确认；取消时不调用 registerResult', async () => {
    const wrapper = await mountAndFillForm()
    await findRegisterButton(wrapper).trigger('click')
    await flushPromises()

    expect(elMessageBoxConfirmMock).toHaveBeenCalledTimes(1)
    expect(registerResultMock).not.toHaveBeenCalled()
  })

  it('确认弹窗文案包含已填写竞品行数与不可修改警示', async () => {
    const wrapper = await mountAndFillForm(1)
    await findRegisterButton(wrapper).trigger('click')
    await flushPromises()

    const message = elMessageBoxConfirmMock.mock.calls[0][0]
    expect(message).toContain('1 家竞争对手情况')
    expect(message).toContain('不可再修改')
    // 弹窗为警告级别（type 在第 3 参 options 中）
    expect(elMessageBoxConfirmMock.mock.calls[0][2]).toMatchObject({ type: 'warning' })
  })

  it('未填写竞品时确认文案提示「尚未填写」', async () => {
    const wrapper = await mountAndFillForm(0)
    await findRegisterButton(wrapper).trigger('click')
    await flushPromises()

    expect(elMessageBoxConfirmMock.mock.calls[0][0]).toContain('尚未填写竞争对手情况')
  })

  it('确认后调用 registerResult 并进入已登记锁定态', async () => {
    elMessageBoxConfirmMock.mockResolvedValueOnce('confirm')
    registerResultMock.mockResolvedValueOnce({ data: {} })

    const wrapper = await mountAndFillForm()
    await findRegisterButton(wrapper).trigger('click')
    await flushPromises()

    expect(registerResultMock).toHaveBeenCalledTimes(1)
    const [projectId, payload] = registerResultMock.mock.calls[0]
    expect(projectId).toBe(1)
    expect(payload.resultType).toBe('WON')
    expect(payload.servicePeriodYears).toBe(2)
    expect(payload.competitors).toHaveLength(1)
    expect(payload.competitors[0].name).toBe('华为')

    expect(wrapper.emitted('registered')).toBeTruthy()
    // 登记后进入锁定态：按钮变「已登记」，竞品卡片出现锁定标识
    expect(wrapper.text()).toContain('已登记')
    expect(wrapper.text()).toContain('已登记 · 锁定不可修改')
  })

  it('必填校验失败时不弹二次确认、不提交', async () => {
    getResultMock.mockImplementation(() => Promise.resolve({
      data: { resultType: 'WON', evidenceFileIds: [], competitors: [] },
    }))
    const { default: ResultConfirmStage } = await import('./ResultConfirmStage.vue')
    const wrapper = mount(ResultConfirmStage, {
      props: { projectId: 1 },
      global: { plugins: [ElementPlus] },
    })
    await flushPromises()

    // 未填服务周期/凭证 → 先被前端必填校验拦下
    wrapper.vm.form.competitors[0].name = '华为'
    await findRegisterButton(wrapper).trigger('click')
    await flushPromises()

    expect(elMessageBoxConfirmMock).not.toHaveBeenCalled()
    expect(registerResultMock).not.toHaveBeenCalled()
  })
})
