import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import ScoreParseDrawer from './ScoreParseDrawer.vue'
import ScoreItemDetailModal from './ScoreItemDetailModal.vue'
import { defaultScoreTemplate } from '@/composables/projectDetail/scoreParseDefaults.js'
import { bidAgentApi } from '@/api/modules/bidAgent.js'
import { projectsApi } from '@/api/modules/projects.js'
import { ElMessageBox, ElMessage } from 'element-plus'

vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    ElMessageBox: {
      confirm: vi.fn(),
    },
    ElMessage: {
      success: vi.fn(),
      warning: vi.fn(),
      info: vi.fn(),
      error: vi.fn(),
    },
  }
})

vi.mock('@/api/modules/bidAgent.js', () => ({
  bidAgentApi: {
    getFullAnalysis: vi.fn(),
    getQualificationMatch: vi.fn(),
    getScoringCriteria: vi.fn(),
  },
}))

vi.mock('@/api/modules/projects.js', () => ({
  projectsApi: {
    importScoreDraftsFromAnalysis: vi.fn(),
  },
}))

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
    ElMessageBox.confirm.mockResolvedValue('confirm')
    bidAgentApi.getFullAnalysis.mockResolvedValue({
      data: {
        sourceFileName: '国家级数据中心扩容项目招标文件.pdf',
        bidFileName: '西域数智化投标文件_v3.pdf',
        parseTime: '2026-08-15 14:00:00',
        scoreTime: '2026-08-15 14:30:00',
        scoringCriteria: {
          items: defaultScoreTemplate,
        },
      },
    })
    bidAgentApi.getQualificationMatch.mockResolvedValue({ data: {} })
    bidAgentApi.getScoringCriteria.mockResolvedValue({
      data: {
        sourceFileName: '国家级数据中心扩容项目招标文件.pdf',
        structuredItems: defaultScoreTemplate,
      },
    })
    projectsApi.importScoreDraftsFromAnalysis.mockResolvedValue({
      data: { importedCount: 13 },
    })
  })

  // QA-TC01: 阶段 1 (招标文件解析) 完整流程与数据展现
  it('QA-TC01: [阶段1] 正确渲染 13 项评分细则提取、满足度预判与统计汇总', async () => {
    const wrapper = mount(ScoreParseDrawer, {
      props: { projectId },
      global: { stubs: globalStubs },
    })

    await wrapper.vm.open({ stage: 1, autoScore: false })

    expect(wrapper.vm.visible).toBe(true)
    expect(wrapper.vm.currentStage).toBe(1)
    expect(wrapper.text()).toContain('阶段 1 · 招标文件解析')
    expect(wrapper.text()).toContain('国家级数据中心扩容项目招标文件.pdf')

    // 验证 13 项评分标准与维度
    expect(wrapper.vm.scoreItems.length).toBe(13)
    expect(wrapper.text()).toContain('A1')
    expect(wrapper.text()).toContain('总体架构设计')
    expect(wrapper.text()).toContain('D2')
    expect(wrapper.text()).toContain('CMMI 5 级认证')

    // 验证统计数据与客观/主观权重分布
    expect(wrapper.vm.totalWeight).toBe(100)
    expect(wrapper.vm.objectiveWeight).toBe(41)
    expect(wrapper.vm.subjectiveWeight).toBe(59)
    expect(wrapper.vm.statsOkCount).toBe(3)
    expect(wrapper.vm.statsDangerCount).toBe(1)
    expect(wrapper.vm.statsNeutralCount).toBe(9)

    // 阶段 1 标书未上传占位符
    expect(wrapper.text()).toContain('尚未上传投标文件')
  })

  // QA-TC02: 阶段 2 (投标文件打分) 客观项计分与主观项隔离
  it('QA-TC02: [阶段2] 实际打分展示、客观项得分合计 (38分) 与主观项专家评审', async () => {
    const wrapper = mount(ScoreParseDrawer, {
      props: { projectId },
      global: { stubs: globalStubs },
    })

    await wrapper.vm.open({ stage: 2, autoScore: false })

    expect(wrapper.vm.currentStage).toBe(2)
    expect(wrapper.text()).toContain('阶段 2 · 投标文件打分')
    expect(wrapper.text()).toContain('西域数智化投标文件_v3.pdf')

    // 验证实际得分总和（客观项满分 41 分中实际获得 38 分）
    expect(wrapper.vm.actualTotalScore).toBe(38)
    expect(wrapper.text()).toContain('38')
    expect(wrapper.text()).toContain('/ 41 分')
    expect(wrapper.text()).toContain('仅客观项得分，主观项待评审')

    // 主观项展示
    expect(wrapper.text()).toContain('待专家评审')
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
    expect(modalWrapper.text()).toContain('标书已补充 CMMI 3 级证书说明及替代方案')
    expect(modalWrapper.text()).toContain('CMMI 5 级认证未找到匹配证书')
    expect(modalWrapper.text()).toContain('建议尽快启动 CMMI 5 级认证评估流程')
  })

  // QA-TC04: 操作工具链验收（重新解析、导入草稿、导出报告）
  it('QA-TC04: [操作栏] 重新解析、导入评分草稿与报告导出降级正常工作', async () => {
    const wrapper = mount(ScoreParseDrawer, {
      props: { projectId },
      global: { stubs: globalStubs },
    })

    await wrapper.vm.open({ stage: 2 })

    // 1. 重新解析
    await wrapper.vm.reparse()
    expect(ElMessage.success).toHaveBeenCalledWith('已重新解析评分标准')

    // 2. 导入评分草稿
    await wrapper.vm.importToDrafts()
    expect(ElMessageBox.confirm).toHaveBeenCalled()
    expect(projectsApi.importScoreDraftsFromAnalysis).toHaveBeenCalledWith(projectId)
    expect(ElMessage.success).toHaveBeenCalledWith('成功导入 13 条评分项到草稿库')
    expect(wrapper.emitted('imported')?.[0]).toEqual([{ count: 13 }])

    // 3. 导出报告（模拟弹窗被拦截时的 Blob 降级）
    const originalOpen = window.open
    window.open = vi.fn().mockReturnValue(null)

    wrapper.vm.exportReport()
    expect(ElMessage.success).toHaveBeenCalledWith('已导出报告文件')

    window.open = originalOpen
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
})
