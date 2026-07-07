import { defineComponent } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

// ---------------------------------------------------------------------------
// Mocks
// ---------------------------------------------------------------------------

const searchOpportunities = vi.fn()
const searchOpportunitiesByTender = vi.fn()
const getContactPersons = vi.fn()

vi.mock('@/api/modules/crm.js', () => ({
  crmApi: {
    searchOpportunities,
    searchOpportunitiesByTender,
    getContactPersons,
  },
}))

const elMessage = {
  info: vi.fn(),
  error: vi.fn(),
  warning: vi.fn(),
  success: vi.fn(),
}

vi.mock('element-plus', () => ({
  ElMessage: elMessage,
}))

const { useCrmOpportunitySelector } = await import('./useCrmOpportunitySelector.js')

// ---------------------------------------------------------------------------
// Harness
// ---------------------------------------------------------------------------

function createHarness(props) {
  return defineComponent({
    template: '<div />',
    setup() {
      return useCrmOpportunitySelector(props, vi.fn())
    },
  })
}

// ===========================================================================
// Tests
// ===========================================================================

describe('useCrmOpportunitySelector', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('初始打开且无手动搜索条件时，按招标主体（CRM groupName）查询同集团商机', async () => {
    searchOpportunities.mockResolvedValue({
      data: { list: [{ id: 1, name: '商机A' }], totalCount: 1 },
    })

    const props = {
      tenderer: '山东海化集团有限公司',
      registrationDeadline: '2026-06-03 23:59:00',
      bidOpeningTime: '2026-06-04 23:59:00',
      alreadyLinkedName: '',
    }
    const wrapper = mount(createHarness(props))
    await wrapper.vm.openSearch()
    await flushPromises()

    expect(searchOpportunities).toHaveBeenCalledWith({
      pageIndex: 1,
      pageSize: 10,
      body: { groupName: ['山东海化集团有限公司'] },
    })
    expect(searchOpportunitiesByTender).not.toHaveBeenCalled()
    expect(wrapper.vm.results).toEqual([{ id: 1, name: '商机A' }])
  })

  it('按招标主体查不到时，兜底拉取全量商机', async () => {
    searchOpportunities
      .mockResolvedValueOnce({ data: { list: [], totalCount: 0 } })
      .mockResolvedValueOnce({ data: { list: [{ id: 4, name: '兜底商机' }], totalCount: 1 } })

    const props = {
      tenderer: '山东海化集团有限公司',
      registrationDeadline: '2026-06-03 23:59:00',
      bidOpeningTime: '2026-06-04 23:59:00',
      alreadyLinkedName: '',
    }
    const wrapper = mount(createHarness(props))
    await wrapper.vm.openSearch()
    await flushPromises()

    expect(searchOpportunities).toHaveBeenCalledTimes(2)
    expect(searchOpportunities).toHaveBeenNthCalledWith(1, {
      pageIndex: 1,
      pageSize: 10,
      body: { groupName: ['山东海化集团有限公司'] },
    })
    expect(searchOpportunities).toHaveBeenNthCalledWith(2, {
      pageIndex: 1,
      pageSize: 10,
      body: { selectAll: true },
    })
    expect(wrapper.vm.results).toEqual([{ id: 4, name: '兜底商机' }])
  })

  it('用户输入商机名称后，使用通用分页查询', async () => {
    searchOpportunities.mockResolvedValue({
      data: { list: [{ id: 2, name: '搜索结果' }], totalCount: 1 },
    })

    const props = {
      tenderer: '山东海化集团有限公司',
      registrationDeadline: '2026-06-03 23:59:00',
      bidOpeningTime: '2026-06-04 23:59:00',
      alreadyLinkedName: '',
    }
    const wrapper = mount(createHarness(props))
    wrapper.vm.searchForm.name = '搜索'
    await wrapper.vm.doSearch(1)
    await flushPromises()

    expect(searchOpportunities).toHaveBeenCalledWith({
      pageIndex: 1,
      pageSize: 10,
      body: { name: '搜索' },
    })
    expect(searchOpportunitiesByTender).not.toHaveBeenCalled()
  })

  it('无蓝图条件时兜底拉取全量商机', async () => {
    searchOpportunities.mockResolvedValue({
      data: { list: [{ id: 3, name: '全量商机' }], totalCount: 1 },
    })

    const props = { tenderer: '', registrationDeadline: '', bidOpeningTime: '', alreadyLinkedName: '' }
    const wrapper = mount(createHarness(props))
    await wrapper.vm.openSearch()
    await flushPromises()

    expect(searchOpportunities).toHaveBeenCalledWith({
      pageIndex: 1,
      pageSize: 10,
      body: { selectAll: true },
    })
  })

  it('CRM 选择模式字段映射：风险预判←riskPrediction，支持备注←remark，GAP 附件←gapFile，投标建议理由留空', async () => {
    getContactPersons.mockResolvedValue({ data: [] })
    const chance = {
      id: 100,
      name: 'CRM商机',
      code: 'C001',
      riskPrediction: '竞争对手低价冲击',
      remark: '客户决策周期长',
      bidDocumentDisadvantage: '资质门槛高',
      backupPlan: true,
      managerUnderstandProcess: '是',
      projectGap: '时间紧张',
      gapFile: 'https://crm.example.com/gap.pdf',
      customerRevenue: 5000,
      planSupplierCount: 5,
      ecommerceMroAmount: 200,
      evaluationTime: '2026-06-04',
    }
    searchOpportunities.mockResolvedValue({ data: { list: [chance], totalCount: 1 } })

    const props = { tenderer: '', registrationDeadline: '', bidOpeningTime: '', alreadyLinkedName: '' }
    const emitFn = vi.fn()
    const wrapper = mount(defineComponent({
      template: '<div />',
      setup() { return useCrmOpportunitySelector(props, emitFn) },
    }))
    await wrapper.vm.openSearch()
    await flushPromises()

    wrapper.vm.onSelect(chance)
    await wrapper.vm.confirmLink()
    await flushPromises()

    const emitted = emitFn.mock.calls[emitFn.mock.calls.length - 1][1]
    expect(emitted.opportunityId).toBe('C001')
    expect(emitted.evaluationData.basic).toEqual(expect.objectContaining({
      riskAssessment: '竞争对手低价冲击',
      supportNotes: '客户决策周期长',
      projectPlanGap: '时间紧张',
      projectPlanGapFiles: [{ fileName: 'GAP附件', fileUrl: 'https://crm.example.com/gap.pdf' }],
    }))
    // CO-312: 是否投标/弃标原因由项目负责人手动填写，关联时不带入 recommendation 段
    expect(emitted.evaluationData.recommendation).toBeUndefined()
  })

  it('GAP 附件：CRM gapFile 为 JSON 数组字符串时正确解析为多文件', async () => {
    getContactPersons.mockResolvedValue({ data: [] })
    const chance = {
      id: 101,
      name: 'CRM商机2',
      code: 'C002',
      gapFile: '[{"url":"https://crm.example.com/a.xlsx","name":"附件A.xlsx"},{"url":"https://crm.example.com/b.pdf","name":"附件B.pdf"}]',
    }
    searchOpportunities.mockResolvedValue({ data: { list: [chance], totalCount: 1 } })
    const props = { tenderer: '', registrationDeadline: '', bidOpeningTime: '', alreadyLinkedName: '' }
    const emitFn = vi.fn()
    const wrapper = mount(defineComponent({
      template: '<div />',
      setup() { return useCrmOpportunitySelector(props, emitFn) },
    }))
    await wrapper.vm.openSearch()
    await flushPromises()
    wrapper.vm.onSelect(chance)
    await wrapper.vm.confirmLink()
    await flushPromises()
    const emitted = emitFn.mock.calls[emitFn.mock.calls.length - 1][1]
    expect(emitted.evaluationData.basic.projectPlanGapFiles).toEqual([
      { fileName: '附件A.xlsx', fileUrl: 'https://crm.example.com/a.xlsx' },
      { fileName: '附件B.pdf', fileUrl: 'https://crm.example.com/b.pdf' },
    ])
  })

  // CO-312: 是否投标/弃标原因由项目负责人手动填写，关联 CRM 商机时 evaluationData
  // 不应包含 recommendation 段（无论 CRM 商机的 backupPlan 取何值）。
  it('CO-312 CRM 选择模式：evaluationData 不含 recommendation 段（是否投标/弃标原因手动填）', async () => {
    getContactPersons.mockResolvedValue({ data: [] })
    for (const backupPlan of [undefined, false, true]) {
      const chance = { id: 100, name: 'CRM商机', code: 'C001', backupPlan }
      searchOpportunities.mockResolvedValue({ data: { list: [chance], totalCount: 1 } })

      const props = { tenderer: '', registrationDeadline: '', bidOpeningTime: '', alreadyLinkedName: '' }
      const emitFn = vi.fn()
      const wrapper = mount(defineComponent({
        template: '<div />',
        setup() { return useCrmOpportunitySelector(props, emitFn) },
      }))
      await wrapper.vm.openSearch()
      await flushPromises()

      wrapper.vm.onSelect(chance)
      await wrapper.vm.confirmLink()
      await flushPromises()

      const emitted = emitFn.mock.calls[emitFn.mock.calls.length - 1][1]
      expect(emitted.evaluationData.recommendation).toBeUndefined()
    }
  })

  it('CRM 选择模式会把对接人映射为客户信息', async () => {
    const chance = { id: 285001, name: '最新标讯商机', code: 'CC20260619001' }
    searchOpportunities.mockResolvedValue({ data: { list: [chance], totalCount: 1 } })
    getContactPersons.mockResolvedValue({
      data: [
        {
          position: '1',
          name: '张三',
          phone: '18888888888',
          email: 'zhangsan@example.com',
          ehsyProjectManager: '张頔',
          contactMethod: '电话',
          preferenceBasis: '客户明确偏向西域',
          contacted: true,
          guidedBidDocument: true,
          getKeyInfo: true,
          deleteDisadvantage: false,
          syncInfo: true,
          preferenceLevel: 'HIGH',
          guaranteeWin: true,
          impactRate: '80%',
        },
      ],
    })

    const props = { tenderer: '', registrationDeadline: '', bidOpeningTime: '', alreadyLinkedName: '' }
    const emitFn = vi.fn()
    const wrapper = mount(defineComponent({
      template: '<div />',
      setup() { return useCrmOpportunitySelector(props, emitFn) },
    }))
    await wrapper.vm.openSearch()
    await flushPromises()

    wrapper.vm.onSelect(chance)
    await wrapper.vm.confirmLink()
    await flushPromises()

    expect(getContactPersons).toHaveBeenCalledWith(285001)
    expect(emitFn).toHaveBeenCalledWith('linked', expect.objectContaining({
      opportunityId: 'CC20260619001',
      evaluationData: expect.objectContaining({
        customerInfos: [expect.objectContaining({
          NAME: '张三',
          CONTACT_INFO: '18888888888',
          XIYU_CONTACT: '张頔',
          CONTACT_METHOD: '电话',
          INFO_TENDENCY_BASIS: '客户明确偏向西域',
          CONTACTED: '是',
          GUIDED_BID: '是',
          CAN_GET_KEY_INFO: '是',
          CAN_REMOVE_ADVERSE: '否',
          CAN_SYNC_EVAL: '是',
          TENDENCY: 'HIGH',
          INFO_CLEAR_WINNER_BID: 'true',
          INFO_WIN_RATE_IMPACT: '80%',
        })],
      }),
    }))
  })

  // CO-431: CRM 对接人 position 是数字字符串（生产日志实锤，见 CO-329 PR #1124 评论），
  // 必须 CRM_POSITION_TO_ROLE[c.position] 按 position 映射到对应角色行；
  // 之前 revert 后改成 idx % 14 按数组下标强行分配，导致 position=8（招标文件制作人）
  // 被错配到第 1 行（项目最高决策人）。
  it('CO-431 按 CRM position 字段映射角色，不按数组下标强行分配', async () => {
    const chance = { id: 285002, name: 'CO431商机', code: 'CC20260630101' }
    searchOpportunities.mockResolvedValue({ data: { list: [chance], totalCount: 1 } })
    getContactPersons.mockResolvedValue({
      data: [
        {
          position: '8', // 招标文件制作人
          name: '李四',
          phone: '13700000000',
        },
        {
          position: '5', // 电商公司总经理
          name: '王五',
          phone: '13800000000',
        },
      ],
    })

    const props = { tenderer: '', registrationDeadline: '', bidOpeningTime: '', alreadyLinkedName: '' }
    const emitFn = vi.fn()
    const wrapper = mount(defineComponent({
      template: '<div />',
      setup() { return useCrmOpportunitySelector(props, emitFn) },
    }))
    await wrapper.vm.openSearch()
    await flushPromises()

    wrapper.vm.onSelect(chance)
    await wrapper.vm.confirmLink()
    await flushPromises()

    const emitted = emitFn.mock.calls[emitFn.mock.calls.length - 1][1]
    const infos = emitted.evaluationData.customerInfos
    expect(infos).toHaveLength(2)
    // position=8 → 招标文件制作人，不是 idx=0 的项目最高决策人
    expect(infos[0]).toEqual(expect.objectContaining({
      roleKey: 'BID_DOCUMENT_PREPARER',
      NAME: '李四',
      POSITION: '8',
    }))
    // position=5 → 电商公司总经理，不是 idx=1 的物资公司董事长
    expect(infos[1]).toEqual(expect.objectContaining({
      roleKey: 'ELECTRONICS_COMPANY_GENERAL_MANAGER',
      NAME: '王五',
      POSITION: '5',
    }))
  })

  // CO-431: position 缺失/超范围时落到 EXTERNAL_ROLE_N（外部对接人），不抢占 14 个固定坑位，
  // 避免与按 position 映射的对接人撞车丢人。EXTERNAL_ROLE_N 是后端 TenderEvaluationCustomerInfoPolicy
  // 认可的合法 roleKey（isValidRoleKey 接受 EXTERNAL_ROLE_\d+），前端 getCustomerInfoRoleLabel
  // 也支持渲染为"外部对接人N"。
  it('CO-431 position 缺失/超范围时落到 EXTERNAL_ROLE_N，不抢占固定坑位', async () => {
    const chance = { id: 285003, name: '无职位商机', code: 'CC20260630102' }
    searchOpportunities.mockResolvedValue({ data: { list: [chance], totalCount: 1 } })
    getContactPersons.mockResolvedValue({
      data: [
        { name: '赵六', phone: '13900000000' }, // 无 position
        { position: '99', name: '钱七', phone: '14000000000' }, // 超出字典范围
      ],
    })

    const props = { tenderer: '', registrationDeadline: '', bidOpeningTime: '', alreadyLinkedName: '' }
    const emitFn = vi.fn()
    const wrapper = mount(defineComponent({
      template: '<div />',
      setup() { return useCrmOpportunitySelector(props, emitFn) },
    }))
    await wrapper.vm.openSearch()
    await flushPromises()

    wrapper.vm.onSelect(chance)
    await wrapper.vm.confirmLink()
    await flushPromises()

    const emitted = emitFn.mock.calls[emitFn.mock.calls.length - 1][1]
    const infos = emitted.evaluationData.customerInfos
    expect(infos).toHaveLength(2)
    // 无 position → EXTERNAL_ROLE_1（外部对接人1），POSITION 留空让用户手选
    expect(infos[0]).toEqual(expect.objectContaining({
      roleKey: 'EXTERNAL_ROLE_1',
      NAME: '赵六',
      POSITION: null,
    }))
    // position='99' 超出字典 → EXTERNAL_ROLE_2，不抢占固定坑位
    expect(infos[1]).toEqual(expect.objectContaining({
      roleKey: 'EXTERNAL_ROLE_2',
      NAME: '钱七',
      POSITION: null,
    }))
  })

  // CO-431 P0: 撞车场景——兜底到固定坑位会与按 position 映射的对接人冲突丢人。
  // position='8'(招标文件制作人) + 无position + position='2'(物资公司董事长) 三人同时存在时，
  // 无position的人必须落到 EXTERNAL_ROLE_N，不能抢占第2行物资公司董事长，否则 position='2' 的人被覆盖丢失。
  it('CO-431 兜底不与 position 映射的对接人撞车（不丢失任何人）', async () => {
    const chance = { id: 285004, name: '撞车商机', code: 'CC20260630103' }
    searchOpportunities.mockResolvedValue({ data: { list: [chance], totalCount: 1 } })
    getContactPersons.mockResolvedValue({
      data: [
        { position: '8', name: '李四', phone: '13700000000' }, // 招标文件制作人 → 第8行
        { name: '赵六', phone: '13900000000' }, // 无 position → 不能占第2行
        { position: '2', name: '王五', phone: '13800000000' }, // 物资公司董事长 → 第2行
      ],
    })

    const props = { tenderer: '', registrationDeadline: '', bidOpeningTime: '', alreadyLinkedName: '' }
    const emitFn = vi.fn()
    const wrapper = mount(defineComponent({
      template: '<div />',
      setup() { return useCrmOpportunitySelector(props, emitFn) },
    }))
    await wrapper.vm.openSearch()
    await flushPromises()

    wrapper.vm.onSelect(chance)
    await wrapper.vm.confirmLink()
    await flushPromises()

    const emitted = emitFn.mock.calls[emitFn.mock.calls.length - 1][1]
    const infos = emitted.evaluationData.customerInfos
    // 三人都在，无一人丢失
    expect(infos).toHaveLength(3)
    const roleKeys = infos.map(i => i.roleKey)
    // 三个 roleKey 互不重复（无撞车）
    expect(new Set(roleKeys).size).toBe(3)
    // position='8' 和 position='2' 各落到位
    expect(infos.find(i => i.NAME === '李四')).toEqual(expect.objectContaining({
      roleKey: 'BID_DOCUMENT_PREPARER',
      POSITION: '8',
    }))
    expect(infos.find(i => i.NAME === '王五')).toEqual(expect.objectContaining({
      roleKey: 'MATERIALS_COMPANY_CHAIRMAN',
      POSITION: '2',
    }))
    // 无 position 的赵六落到 EXTERNAL_ROLE_1，不抢占第2行
    expect(infos.find(i => i.NAME === '赵六')).toEqual(expect.objectContaining({
      roleKey: 'EXTERNAL_ROLE_1',
      POSITION: null,
    }))
  })


  it('CRM 对接人查询失败时仍应继续关联商机并提交空客户信息', async () => {
    const chance = { id: 285001, name: '最新标讯商机', code: 'CC20260619001' }
    searchOpportunities.mockResolvedValue({ data: { list: [chance], totalCount: 1 } })
    getContactPersons.mockRejectedValue(new Error('contact-persons failed'))

    const props = { tenderer: '', registrationDeadline: '', bidOpeningTime: '', alreadyLinkedName: '' }
    const emitFn = vi.fn()
    const wrapper = mount(defineComponent({
      template: '<div />',
      setup() { return useCrmOpportunitySelector(props, emitFn) },
    }))
    await wrapper.vm.openSearch()
    await flushPromises()

    wrapper.vm.onSelect(chance)
    await wrapper.vm.confirmLink()
    await flushPromises()

    expect(getContactPersons).toHaveBeenCalledWith(285001)
    expect(emitFn).toHaveBeenCalledWith('linked', expect.objectContaining({
      opportunityId: 'CC20260619001',
      opportunityName: '最新标讯商机',
      evaluationData: expect.objectContaining({ customerInfos: [] }),
    }))
    expect(elMessage.warning).toHaveBeenCalledWith('CRM对接人查询失败，已继续关联商机，客户信息未自动带入')
    expect(elMessage.error).not.toHaveBeenCalledWith('CRM对接人查询失败，无法带入客户信息')
  })

  it('手动输入模式字段映射：风险预判←projectRiskText，支持备注←remark', async () => {
    const props = { tenderer: '', registrationDeadline: '', bidOpeningTime: '', alreadyLinkedName: '' }
    const emitFn = vi.fn()
    const wrapper = mount(defineComponent({
      template: '<div />',
      setup() { return useCrmOpportunitySelector(props, emitFn) },
    }))

    wrapper.vm.manualForm.name = '手动商机'
    wrapper.vm.manualForm.projectRiskText = '手动风险'
    wrapper.vm.manualForm.remark = '手动备注'
    wrapper.vm.confirmManual()
    await wrapper.vm.confirmLink()
    await flushPromises()

    expect(emitFn).toHaveBeenCalledWith('linked', expect.objectContaining({
      opportunityName: '手动商机',
      evaluationData: expect.objectContaining({
        basic: expect.objectContaining({
          riskAssessment: '手动风险',
          supportNotes: '手动备注',
          projectPlanGapFiles: [],
        }),
      }),
    }))
  })
})
