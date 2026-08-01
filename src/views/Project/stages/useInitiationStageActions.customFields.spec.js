// Input: useInitiationStageActions 的 customFields 收集与回显
// Output: CO-601 US1 — buildPayload 携带 customFields（project.initiation scope）；load() 摊平回显
// Pos: src/views/Project/stages/ - 立项阶段 actions 单元测试
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { describe, expect, it, vi, beforeEach } from 'vitest'
import { reactive, ref } from 'vue'

vi.mock('element-plus', () => ({
  ElMessage: { info: vi.fn(), success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  ElMessageBox: { prompt: vi.fn() },
}))

import { useInitiationStageActions } from './useInitiationStageActions.js'

function createState() {
  return {
    existing: ref(false),
    saving: ref(false),
    submitting: ref(false),
    approving: ref(false),
    rejecting: ref(false),
    uploadingDoc: ref(false),
    errorMsg: ref(''),
    reviewStatus: ref(''),
    fieldLocked: ref(false),
    approvalForm: reactive({ biddingLeaderId: null, biddingAssistantId: null }),
    evalPrefilled: ref(false),
  }
}

function makeForm(overrides = {}) {
  return reactive({
    projectName: '测试项目',
    ownerUnit: '测试单位',
    customerType: 'CENTRAL_SOE',
    tenderDocumentId: 1,
    needDeposit: 'NO',
    depositAmount: 0,
    depositPaymentMethod: '',
    depositDueDate: null,
    ...overrides,
  })
}

function setup({ formOverrides = {}, customFieldsSchema = ref([]), initiationData = null } = {}) {
  const form = makeForm(formOverrides)
  const projectLifecycleApi = {
    submitInitiation: vi.fn().mockResolvedValue({ data: { projectId: 1 } }),
    updateInitiation: vi.fn().mockResolvedValue({ data: { projectId: 1 } }),
    getInitiation: initiationData
      ? vi.fn().mockResolvedValue({ data: initiationData })
      : vi.fn(),
  }
  const actions = useInitiationStageActions({
    props: { projectId: 1 },
    emit: vi.fn(),
    form,
    custFixedRows: ref([]),
    bidDocFiles: ref([]),
    planGapFiles: ref([]),
    userStore: { currentUser: { id: 1 }, userName: '张三' },
    projectLifecycleApi,
    projectsApi: { getDetail: vi.fn(), getDocuments: vi.fn() },
    tendersApi: { getDetail: vi.fn(), getEvaluation: vi.fn() },
    usersApi: { getByIds: vi.fn() },
    projectsState: createState(),
    leaderOptions: ref([]),
    assistantOptions: ref([]),
    customFieldsSchema,
  })
  return { actions, form, projectLifecycleApi }
}

describe('useInitiationStageActions customFields — CO-601 立项自定义字段', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('buildPayload 收集 project.initiation scope 自定义字段（schema 减预置清单）', () => {
    const { actions, form } = setup({
      customFieldsSchema: ref([
        { key: 'projectName' }, // 预置
        { key: 'internalReviewNote' }, // 自定义
      ]),
    })
    form.internalReviewNote = '需法务会签'

    const payload = actions.buildPayload()

    expect(payload.customFields).toEqual({
      'project.initiation': { internalReviewNote: '需法务会签' },
    })
    // 预置 key 不进入 customFields
    expect(payload.customFields['project.initiation'].projectName).toBeUndefined()
  })

  it('buildPayload 无自定义值时省略 customFields 键', () => {
    const { actions } = setup()

    const payload = actions.buildPayload()

    expect(payload.customFields).toBeUndefined()
  })

  it('load() 在 Object.assign 后把 customFields[project.initiation] 摊平进 form，预置 key 不被脏数据覆盖', async () => {
    const { actions, form } = setup({
      initiationData: {
        ownerUnit: '国网',
        customerType: 'CENTRAL_SOE',
        reviewStatus: 'DRAFT',
        customFields: {
          'project.initiation': {
            internalReviewNote: '需法务会签',
            projectName: '脏数据覆盖尝试',
          },
        },
      },
    })

    await actions.load()

    expect(form.internalReviewNote).toBe('需法务会签')
    // 预置 key（projectName）以 DTO 权威值为准
    expect(form.projectName).toBe('测试项目')
  })

  it('load() 老立项无 customFields 时不报错', async () => {
    const { actions, form } = setup({
      initiationData: {
        ownerUnit: '国网',
        customerType: 'CENTRAL_SOE',
        reviewStatus: 'DRAFT',
      },
    })

    await actions.load()

    expect(form.ownerUnit).toBe('国网')
  })
})
