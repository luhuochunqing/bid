import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ScoreParseDrawer from './ScoreParseDrawer.vue'
import ScoreItemDetailModal from './ScoreItemDetailModal.vue'
import { scoreParseApi } from '@/api/modules/scoreParse.js'
import { ElMessage } from 'element-plus'

vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    ElMessage: {
      success: vi.fn(),
      warning: vi.fn(),
      info: vi.fn(),
      error: vi.fn(),
    },
  }
})

vi.mock('@/api/modules/scoreParse.js', () => ({
  scoreParseApi: {
    getLatestExtraction: vi.fn(),
    getExtraction: vi.fn(),
    activateExtraction: vi.fn(),
    getItems: vi.fn(),
    triggerParse: vi.fn(),
    getParseStatus: vi.fn(),
    triggerScoring: vi.fn(),
    getScoringStatus: vi.fn(),
    getResults: vi.fn(),
  },
}))

// spec 041 真接口 DTO 形状（ScoreItemDTO / ScoreScoringResultsDTO）
const qaItemsFixture = [
  { id: 1, code: 'A1', dim: '技术方案', detail: '总体架构设计', weight: 12, scoreType: 'SUBJECTIVE', status: 'PENDING', estScore: null, estBasis: null, kbHit: null },
  { id: 2, code: 'D1', dim: '资质业绩', detail: 'ISO9001 质量认证', weight: 6, scoreType: 'OBJECTIVE', status: 'OK', estScore: 6, estBasis: '知识库命中 ISO9001 证书', kbHit: true },
  { id: 3, code: 'D2', dim: '资质业绩', detail: 'CMMI 5 级认证', weight: 5, scoreType: 'OBJECTIVE', status: 'DANGER', estScore: 0, estBasis: '知识库无 CMMI 5 级证书', kbHit: false },
]

const qaResultsFixture = {
  results: [
    { scoreItemId: 1, code: 'A1', dim: '技术方案', detail: '总体架构设计', weight: 12, scoreType: 'SUBJECTIVE', status: 'PENDING', actualScore: null, evidence: null, quote: null, missedReason: null, suggestion: '建议细化微服务与容灾架构' },
    { scoreItemId: 2, code: 'D1', dim: '资质业绩', detail: 'ISO9001 质量认证', weight: 6, scoreType: 'OBJECTIVE', status: 'OK', actualScore: 6, evidence: '标书第 7 章提供 ISO9001 证书复印件', quote: '第 7 章资质证明：已取得 ISO9001 证书', missedReason: null, suggestion: null },
    { scoreItemId: 3, code: 'D2', dim: '资质业绩', detail: 'CMMI 5 级认证', weight: 5, scoreType: 'OBJECTIVE', status: 'PENDING', actualScore: 3, evidence: '标书已补充 CMMI 3 级说明及替代方案', quote: '第 7 章资质证明：我方已通过 CMMI 3 级认证', missedReason: 'CMMI 5 级认证未找到匹配证书', suggestion: '建议尽快启动 CMMI 5 级认证评估流程' },
  ],
  summary: null,
}

describe('QA Test Suite: AI 评分标准解析 V3 全链路验收', () => {
  const projectId = 1001
  const globalStubs = {
    'el-drawer': {
      template: '<div class="el-drawer-mock" v-if="modelValue"><slot /></div>',
      props: ['modelValue'],
    },
    'el-dialog': {
      template: '<div class="el-dialog-mock" v-if="modelValue"><div class="el-dialog__title">{{ title }}</div><slot /></div>',
      props: ['modelValue', 'title'],
    },
    'el-button': true,
  }

  beforeEach(() => {
    vi.clearAllMocks()
    scoreParseApi.getLatestExtraction.mockRejectedValue({ response: { status: 409, data: { msg: 'EXTRACTION_FEATURE_DISABLED' } } })
    scoreParseApi.getItems.mockResolvedValue({ data: { items: qaItemsFixture, summary: null } })
    scoreParseApi.triggerParse.mockResolvedValue({ data: { taskId: 't-parse', status: 'PENDING' } })
    scoreParseApi.getParseStatus.mockResolvedValue({ data: { taskId: 't-parse', status: 'COMPLETED', progress: 100, completedAt: '2026-08-15T14:00:00' } })
    scoreParseApi.triggerScoring.mockResolvedValue({ data: { taskId: 't-score', status: 'PENDING' } })
    scoreParseApi.getScoringStatus.mockResolvedValue({ data: { taskId: 't-score', status: 'COMPLETED', progress: 100, completedAt: '2026-08-15T14:30:00' } })
    scoreParseApi.getResults.mockResolvedValue({ data: qaResultsFixture })
  })

  // QA-TC01: 阶段 1 (招标文件解析) 完整流程与数据展现
  it('QA-TC01: [阶段1] 正确渲染评分细则提取、满足度预判与统计汇总', async () => {
    const wrapper = mount(ScoreParseDrawer, {
      props: { projectId },
      global: { stubs: globalStubs },
    })

    await wrapper.vm.open({ stage: 1, autoScore: false })

    expect(wrapper.vm.visible).toBe(true)
    expect(wrapper.vm.currentStage).toBe(1)
    // 顶部操作栏已移除（导入草稿/导出报告/阶段标签下线），阶段 1 以 section 标题与空态体现
    expect(wrapper.text()).toContain('招标文件解析')
    // 真接口不返回文件名，展示 — 占位而非硬编码假文件名
    expect(wrapper.text()).toContain('招标文件：—')

    // 验证评分标准与维度
    expect(wrapper.vm.scoreItems.length).toBe(3)
    expect(wrapper.findAll('.code-cell').map((cell) => cell.text())).toEqual(['1', '2', '3'])
    expect(wrapper.text()).toContain('总体架构设计')
    expect(wrapper.text()).toContain('CMMI 5 级认证')

    // 验证统计数据与客观/主观权重分布
    expect(wrapper.vm.totalWeight).toBe(23)
    expect(wrapper.vm.objectiveWeight).toBe(11)
    expect(wrapper.vm.subjectiveWeight).toBe(12)
    expect(wrapper.vm.statsOkCount).toBe(1)
    expect(wrapper.vm.statsDangerCount).toBe(1)
    expect(wrapper.vm.statsNeutralCount).toBe(1)

    // 合计区布局守卫：满足状态三项竖排、客观/主观权重各占一行（列对齐布局回归防护）
    const stackCells = wrapper.findAll('.tfoot-stack-cell')
    expect(stackCells.length).toBe(2)
    expect(stackCells[0].findAll('.stat-tag').length).toBe(3)
    expect(stackCells[1].text()).toContain('客观项权重')
    expect(stackCells[1].text()).toContain('主观项权重')

    // 阶段 1 标书未上传占位符
    expect(wrapper.text()).toContain('尚未上传投标文件')
  })

  // QA-TC02: 阶段 2 (投标文件打分) 真实后端 API 驱动计分与主观项隔离
  it('QA-TC02: [阶段2] 真实打分展示、客观项得分合计与主观项专家评审', async () => {
    scoreParseApi.getScoringStatus.mockResolvedValueOnce({ data: null }) // 先确认无任务，才接受显式AUTO入口
    scoreParseApi.getResults
      .mockResolvedValueOnce({ data: { results: [], summary: null } })
      .mockResolvedValueOnce({ data: qaResultsFixture })
    const wrapper = mount(ScoreParseDrawer, {
      props: { projectId },
      global: { stubs: globalStubs },
    })

    await wrapper.vm.open({ stage: 2, autoScore: true })

    // spec 041 真接口：触发 → 轮询 → 拉结果
    expect(scoreParseApi.triggerScoring).toHaveBeenCalledWith(projectId, expect.objectContaining({ source: 'AUTO' }))
    expect(scoreParseApi.getScoringStatus).toHaveBeenCalledWith(projectId)
    expect(scoreParseApi.getResults).toHaveBeenCalledWith(projectId)
    expect(wrapper.vm.currentStage).toBe(2)
    expect(wrapper.text()).toContain('投标文件评分')

    // 验证实际得分总和（客观项满分 11 分中实际获得 9 分）
    expect(wrapper.vm.actualTotalScore).toBe(9)
    expect(wrapper.text()).toContain('9')
    expect(wrapper.text()).toContain('客观项权重 11')
    expect(wrapper.text()).toContain('主观项权重 12')
    // 阶段 2 合计区与阶段 1 同构：共用 ScoreParseTable，竖排列对齐布局一致（对齐需求回归防护）
    // 限定阶段 2 容器，避免与阶段 1 表格的同类单元格混淆
    const actualStackCells = wrapper.findAll('.scoring-table-container .tfoot-stack-cell')
    expect(actualStackCells.length).toBe(2)
    expect(actualStackCells[0].findAll('.stat-tag').length).toBe(3)

    // 底部说明文案
    expect(wrapper.text()).toContain('AI 基于标书内容 + 知识库证书自动判定，计入总分')
    expect(wrapper.text()).toContain('需评标专家人工评审，AI 不计分')
  })

  // QA-TC02b: 打分按钮固定全量打分（「全部/仅不满足/勾选」单选已删的回归护栏）
  it('QA-TC02b: [阶段2] 手动打分按钮固定提交 scope=ALL 全量打分', async () => {
    scoreParseApi.getResults
      .mockResolvedValueOnce({ data: { results: [], summary: null } })
      .mockResolvedValueOnce({ data: qaResultsFixture })
    const wrapper = mount(ScoreParseDrawer, {
      props: { projectId },
      global: { stubs: globalStubs },
    })

    await wrapper.vm.open({ stage: 2 })

    const scoreBtn = wrapper.findAll('button.btn-primary-sm').find((b) => b.text().includes('AI 实际打分'))
    expect(scoreBtn).toBeTruthy()
    expect(scoreBtn.attributes('disabled')).toBeUndefined()
    await scoreBtn.trigger('click')
    await flushPromises()

    // 单选删除后 UI 无 scope 输入源：请求体必为全量，itemIds 恒为空数组
    expect(scoreParseApi.triggerScoring).toHaveBeenCalledWith(projectId, {
      source: 'MANUAL',
      scope: 'ALL',
      itemIds: [],
    })
  })

  // QA-TC03: 评分项详情与建议弹窗（ScoreItemDetailModal）多场景验收
  it('QA-TC03: [详情弹窗] 验证评分细则、标书精准引用、缺失说明与修改建议', async () => {
    const wrapper = mount(ScoreParseDrawer, {
      props: { projectId },
      global: { stubs: globalStubs },
    })

    await wrapper.vm.open({ stage: 2 })

    // 打开 D2 (CMMI 5 级认证 - 部分满足项)
    const cmmiItem = wrapper.vm.scoreItems.find((s) => s.code === 'D2')
    const cmmiResult = wrapper.vm.scoreResults['D2']
    wrapper.vm.openDetail(cmmiItem, cmmiResult, 'actual')

    expect(wrapper.vm.detailModalVisible).toBe(true)
    expect(wrapper.vm.selectedItem.code).toBe('D2')

    // 挂载 DetailModal 单独进行 DOM 元素断言
    const modalWrapper = mount(ScoreItemDetailModal, {
      props: {
        visible: true,
        mode: 'actual',
        item: cmmiItem,
        result: cmmiResult,
      },
      global: { stubs: globalStubs },
    })

    expect(modalWrapper.text()).toContain('D2 · 资质业绩 — 实际评分详情')
    expect(modalWrapper.text()).toContain('3 / 5')
    expect(modalWrapper.text()).toContain('标书已补充 CMMI 3 级说明及替代方案')
    expect(modalWrapper.text()).toContain('第 7 章资质证明')
    expect(modalWrapper.text()).toContain('CMMI 5 级认证未找到匹配证书')
    expect(modalWrapper.text()).toContain('建议尽快启动 CMMI 5 级认证评估流程')
  })

  // QA-TC04: 重新解析操作验收（顶部操作栏移除后，入口保留在「招标文件解析」标题栏）
  it('QA-TC04: [操作栏] 招标文件解析标题栏「重新解析」触发真实解析任务', async () => {
    const wrapper = mount(ScoreParseDrawer, {
      props: { projectId },
      global: { stubs: globalStubs },
    })

    await wrapper.vm.open({ stage: 2 })

    const reparseBtn = wrapper.findAll('.section-actions button')[0]
    expect(reparseBtn.text()).toContain('重新解析')
    await reparseBtn.trigger('click')
    await flushPromises()

    expect(scoreParseApi.triggerParse).toHaveBeenCalledWith(projectId, { source: 'MANUAL' })
    expect(scoreParseApi.getItems).toHaveBeenCalled()
    expect(ElMessage.success).toHaveBeenCalledWith('已重新解析评分标准')
  })

  // QA-TC05: 折叠面板与展开切换验收
  it('QA-TC05: [折叠状态] Section 1 评分标准折叠面板切换顺畅', async () => {
    const wrapper = mount(ScoreParseDrawer, {
      props: { projectId },
      global: { stubs: globalStubs },
    })

    await wrapper.vm.open({ stage: 1 })
    expect(wrapper.vm.isSection1Expanded).toBe(true)

    // 模拟点击折叠头
    const collapseHeader = wrapper.find('.collapse-header')
    await collapseHeader.trigger('click')
    expect(wrapper.vm.isSection1Expanded).toBe(false)
  })

  // QA-TC06: 空数据状态验收（PRD §5.3）
  it('QA-TC06: [空状态] 当未解析到评分标准时，展示标准空状态提示而非虚假模板', async () => {
    scoreParseApi.getItems.mockResolvedValue({ data: { items: [], summary: null } })

    const wrapper = mount(ScoreParseDrawer, {
      props: { projectId },
      global: { stubs: globalStubs },
    })

    await wrapper.vm.open({ stage: 1, autoScore: false })
    expect(wrapper.vm.scoreItems.length).toBe(0)
    expect(wrapper.text()).toContain('尚未解析到评分标准，将使用立项招标文件解析')
  })

  // QA-TC07: 待确认客观项空值得分展示（spec 044 FR-001/002/003，PRD 1.3）
  it('QA-TC07: [阶段1] 客观项无预判得分显示"待确认"，不得误显示红色 0 分', async () => {
    // 后端待确认路径：知识库类别未识别 / 单项匹配失败 → estScore=null + PENDING
    scoreParseApi.getItems.mockResolvedValue({
      data: {
        items: [
          { id: 1, code: 'D1', dim: '资质业绩', detail: 'ISO9001 质量认证', weight: 6, scoreType: 'OBJECTIVE', status: 'OK', estScore: 6, estBasis: '知识库命中', kbHit: true },
          { id: 2, code: 'D2', dim: '资质业绩', detail: 'CMMI 5 级认证', weight: 5, scoreType: 'OBJECTIVE', status: 'DANGER', estScore: 0, estBasis: '知识库未匹配', kbHit: false },
          { id: 3, code: 'W1', dim: '仓储配置', detail: '本地化仓储服务中心', weight: 4, scoreType: 'OBJECTIVE', status: 'PENDING', estScore: null, estBasis: '未识别到知识库匹配类别，待人工确认预计得分', kbHit: false },
        ],
        summary: null,
      },
    })

    const wrapper = mount(ScoreParseDrawer, {
      props: { projectId },
      global: { stubs: globalStubs },
    })

    await wrapper.vm.open({ stage: 1, autoScore: false })

    // 归一化后空值得分保留 null（不再被转成数字 0）
    const pendingItem = wrapper.vm.scoreItems.find((s) => s.code === 'W1')
    expect(pendingItem.estScore).toBeNull()
    expect(pendingItem.estBasis).toBe('未识别到知识库匹配类别，待人工确认预计得分')

    // 表格得分列：满分 full / 真实零分 zero / 待确认 subjective（非 zero 红色）
    const scoreCells = wrapper.findAll('.parse-table tbody tr .score-cell')
    expect(scoreCells).toHaveLength(3)
    expect(scoreCells[0].classes()).toContain('full')
    expect(scoreCells[1].classes()).toContain('zero')
    expect(scoreCells[2].classes()).toContain('subjective')
    expect(scoreCells[2].classes()).not.toContain('zero')
    expect(scoreCells[2].text()).toBe('需人工评估')

    // 合计口径：仅计数字得分（6 + 0），null 不参与求和
    expect(wrapper.vm.estTotalScore).toBe(6)

    // 详情弹窗（预计模式）：待确认项得分显示"待确认"，与表格口径一致
    wrapper.vm.openDetail(pendingItem, null, 'est')
    const modalWrapper = mount(ScoreItemDetailModal, {
      props: { visible: true, mode: 'est', item: pendingItem, result: null },
      global: { stubs: globalStubs },
    })
    expect(modalWrapper.text()).toContain('待确认')
    expect(modalWrapper.find('.detail-value.subjective').exists()).toBe(true)
  })
})
