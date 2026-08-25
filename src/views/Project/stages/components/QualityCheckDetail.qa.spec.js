// Input: 质量核查详情纯函数与详情弹窗（pass/fail/manual 三态 + scoring_response 得分链接）
// Output: 字段对照表渲染验收（问题定位/原文摘录/联动数据来源/建议操作展示规则）
// Pos: src/views/Project/stages/components/ - 质量核查详情 QA spec（AI 标书质量核查抽屉 UI 改造）
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import QualityCheckDetailDialog from './QualityCheckDetailDialog.vue'
import QualityCheckItemCard from './QualityCheckItemCard.vue'
import {
  stripReasonCode,
  extractBidPages,
  extractBidQuote,
  extractKbLinks,
  sumObjectiveActualScore,
  getStatusMeta,
  formatStatus
} from '../composables/qualityCheckDetail.js'

const pushMock = vi.fn()
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: pushMock })
}))

const globalStubs = {
  'el-dialog': {
    template: '<div class="el-dialog-mock" v-if="modelValue"><slot /></div>',
    props: ['modelValue', 'title']
  },
  'el-tag': {
    template: '<span class="el-tag-mock"><slot /></span>'
  }
}

function mountDialog(props = {}) {
  return mount(QualityCheckDetailDialog, {
    props: { modelValue: true, item: null, objectiveScore: null, ...props },
    global: { stubs: globalStubs }
  })
}

// ===== 纯函数 =====
describe('质量核查详情纯函数', () => {
  it('getStatusMeta/formatStatus：三态元信息单一数据源，未知状态回退', () => {
    expect(getStatusMeta('PASS')).toEqual({ icon: '✓', tagType: 'success', text: '通过' })
    expect(getStatusMeta('FAIL').tagType).toBe('danger')
    expect(getStatusMeta('MANUAL_REVIEW').text).toBe('待确认')
    expect(getStatusMeta('OTHER').tagType).toBe('info')
    expect(formatStatus('MANUAL_REVIEW')).toBe('待确认')
    expect(formatStatus('WEIRD')).toBe('WEIRD')
    expect(formatStatus(null)).toBe('未知')
  })

  it('stripReasonCode：剥离 MANUAL 结论 [REASON_CODE] 前缀', () => {
    expect(stripReasonCode('[EVIDENCE_INSUFFICIENT] 未找到证书复印件')).toBe('未找到证书复印件')
    expect(stripReasonCode('[LOW_CONFIDENCE]AI 无法确认')).toBe('AI 无法确认')
    expect(stripReasonCode('无前缀结论')).toBe('无前缀结论')
    expect(stripReasonCode(null)).toBe('')
  })

  it('extractBidPages：BID 证据页码去重聚合，无页码返回空', () => {
    expect(extractBidPages([
      { sourceType: 'BID', pageNumber: 12 },
      { sourceType: 'BID', pageNumber: 56 },
      { sourceType: 'BID', pageNumber: 12 },
      { sourceType: 'TENDER', pageNumber: 3 },
      { sourceType: 'BID', pageNumber: null }
    ])).toBe('第 12 / 56 页')
    expect(extractBidPages([{ sourceType: 'BID', pageNumber: null }])).toBe('')
    expect(extractBidPages([])).toBe('')
  })

  it('extractBidQuote：取第一条非空 BID 摘录', () => {
    expect(extractBidQuote([
      { sourceType: 'TENDER', quote: '招标原文' },
      { sourceType: 'BID', quote: '' },
      { sourceType: 'BID', quote: '投标原文段落' }
    ])).toBe('投标原文段落')
    expect(extractBidQuote([{ sourceType: 'TENDER', quote: '招标原文' }])).toBe('')
  })

  it('extractKbLinks：四库映射、按库去重、忽略未知来源', () => {
    const links = extractKbLinks([
      { sourceType: 'KNOWLEDGE_BASE', knowledgeSource: 'qualification' },
      { sourceType: 'KNOWLEDGE_BASE', knowledgeSource: 'qualification' },
      { sourceType: 'KNOWLEDGE_BASE', knowledgeSource: 'brand-authorization' },
      { sourceType: 'KNOWLEDGE_BASE', knowledgeSource: 'unknown-lib' },
      { sourceType: 'BID', knowledgeSource: 'personnel' }
    ])
    expect(links).toEqual([
      { source: 'qualification', label: '资质库', route: '/knowledge/qualification' },
      { source: 'brand-authorization', label: '品牌授权库', route: '/knowledge/brand-auth' }
    ])
    expect(extractKbLinks([])).toEqual([])
  })

  it('sumObjectiveActualScore：仅汇总客观项实际得分，无有效得分返回 null', () => {
    const items = [
      { code: 'D1', scoreType: '客观项' },
      { code: 'D2', scoreType: '客观项' },
      { code: 'A1', scoreType: '主观项' }
    ]
    const resultMap = {
      D1: { actualScore: 6 },
      D2: { actualScore: 3 },
      A1: { actualScore: 9 }
    }
    expect(sumObjectiveActualScore(items, resultMap)).toBe(9)
    expect(sumObjectiveActualScore(items, { D1: {}, D2: {} })).toBeNull()
    expect(sumObjectiveActualScore([], {})).toBeNull()
    // 真实零分计入合计（区别于无得分）
    expect(sumObjectiveActualScore(items, { D1: { actualScore: 0 }, D2: {} })).toBe(0)
  })
})

// ===== 详情弹窗字段对照表 =====
describe('质量核查详情弹窗字段对照表', () => {
  beforeEach(() => {
    pushMock.mockClear()
  })

  it('pass 项：仅显示检查状态/检查项说明/AI 检查结论', () => {
    const wrapper = mountDialog({
      item: {
        itemCode: 'cover_info', status: 'PASS', title: '封面信息完整性',
        conclusion: '封面项目名称、编号、投标人信息完整。',
        evidences: [{ sourceType: 'BID', pageNumber: 1, quote: '不应展示' }]
      }
    })
    const text = wrapper.text()
    expect(text).toContain('✓ 通过')
    expect(text).toContain('封面信息完整性')
    expect(text).toContain('封面项目名称、编号、投标人信息完整。')
    expect(text).not.toContain('问题定位')
    expect(text).not.toContain('原文摘录')
    expect(text).not.toContain('联动数据来源')
    expect(text).not.toContain('建议操作')
  })

  it('fail 项：显示问题定位/原文摘录/联动数据来源 + 修订建议文案', () => {
    const wrapper = mountDialog({
      item: {
        itemCode: 'qualification_veto', status: 'FAIL', title: '资格条件一票否决',
        conclusion: '检测到缺少 ISO27001 认证，存在废标风险。',
        suggestion: '后端建议（不独立展示）',
        evidences: [
          { sourceType: 'BID', pageNumber: 12, quote: '投标人须具备 ISO27001 认证证书' },
          { sourceType: 'BID', pageNumber: 78, quote: '' },
          { sourceType: 'KNOWLEDGE_BASE', knowledgeSource: 'qualification', sourceRecordId: 'q-1', matched: false }
        ]
      }
    })
    const text = wrapper.text()
    expect(text).toContain('✗ 不通过')
    expect(text).toContain('第 12 / 78 页')
    expect(text).toContain('投标人须具备 ISO27001 认证证书')
    expect(text).toContain('资质库')
    expect(text).toContain('高风险项，建议立即修订后重新上传标书并再次执行检查。')
  })

  it('manual 项：剥离 reasonCode 前缀 + 投标负责人复核文案；无页码不显示问题定位', () => {
    const wrapper = mountDialog({
      item: {
        itemCode: 'clarification_resp', status: 'MANUAL_REVIEW', title: '澄清文件响应',
        conclusion: '[LOW_CONFIDENCE] AI 无法确认澄清文件是否已完整响应。',
        evidences: []
      }
    })
    const text = wrapper.text()
    expect(text).toContain('⚠ 待确认')
    expect(text).toContain('AI 无法确认澄清文件是否已完整响应。')
    expect(text).not.toContain('[LOW_CONFIDENCE]')
    expect(text).toContain('请投标负责人复核后确认是否需要修订。')
    expect(text).not.toContain('问题定位')
    expect(text).not.toContain('原文摘录')
  })

  it('scoring_response 有客观项得分：结论前显示得分链接，点击通知跳转评分', async () => {
    const wrapper = mountDialog({
      objectiveScore: 42,
      item: {
        itemCode: 'SCORING_RESPONSE', status: 'MANUAL_REVIEW', title: '评分标准逐项对应与支撑材料',
        conclusion: '技术方案评分项中创新性未体现。',
        evidences: []
      }
    })
    const link = wrapper.find('.score-link')
    expect(link.exists()).toBe(true)
    expect(link.text()).toContain('客观项得分：42分')
    await link.trigger('click')
    expect(wrapper.emitted('go-to-score-parse')).toBeTruthy()
  })

  it('scoring_response 未打分（客观项得分为空）：不显示得分链接', () => {
    const wrapper = mountDialog({
      objectiveScore: null,
      item: {
        itemCode: 'SCORING_RESPONSE', status: 'PASS', title: '评分标准逐项对应与支撑材料',
        conclusion: '评分项已逐项对应。',
        evidences: []
      }
    })
    expect(wrapper.find('.score-link').exists()).toBe(false)
  })

  it('联动数据来源点击跳转对应知识库路由', async () => {
    const wrapper = mountDialog({
      item: {
        itemCode: 'qualification_match', status: 'FAIL', title: '资质匹配',
        conclusion: '缺少证书。',
        evidences: [
          { sourceType: 'KNOWLEDGE_BASE', knowledgeSource: 'performance', sourceRecordId: 'p-1', matched: true }
        ]
      }
    })
    await wrapper.find('.dt-source').trigger('click')
    expect(pushMock).toHaveBeenCalledWith('/knowledge/performance')
  })
})

// ===== 紧凑行 =====
describe('质量核查紧凑行', () => {
  function mountRow(item) {
    return mount(QualityCheckItemCard, {
      props: { item },
      global: { stubs: { 'el-tag': globalStubs['el-tag'] } }
    })
  }

  it('仅显示检查项与核查结论，右侧状态标签；点击发出 detail 事件', async () => {
    const wrapper = mountRow({
      itemCode: 'signature_seal', status: 'MANUAL_REVIEW', title: '签章完整性',
      severity: 'HIGH',
      conclusion: '[AMBIGUOUS] 第 32 页技术方案末尾缺少投标人公章。',
      evidences: [{ sourceType: 'BID', pageNumber: 32, quote: '不应在行内展示' }]
    })
    const text = wrapper.text()
    expect(text).toContain('签章完整性')
    expect(text).toContain('第 32 页技术方案末尾缺少投标人公章。')
    expect(text).not.toContain('[AMBIGUOUS]')
    expect(text).not.toContain('不应在行内展示')
    expect(text).not.toContain('高风险')
    expect(text).toContain('待确认')
    await wrapper.trigger('click')
    expect(wrapper.emitted('detail')).toBeTruthy()
  })

  it('状态图标与标签三态映射', () => {
    const pass = mountRow({ itemCode: 'a', status: 'PASS', title: 'A', conclusion: '' })
    const fail = mountRow({ itemCode: 'b', status: 'FAIL', title: 'B', conclusion: '' })
    expect(pass.find('.item-icon').text()).toBe('✓')
    expect(pass.text()).toContain('通过')
    expect(fail.find('.item-icon').text()).toBe('✗')
    expect(fail.text()).toContain('不通过')
  })
})
