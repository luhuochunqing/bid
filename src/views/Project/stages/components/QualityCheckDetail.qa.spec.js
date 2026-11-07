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
  formatStatus,
  getQualDetailStatusMeta,
  isQualificationMatchItem,
  isCaCertificateItem,
  isNotApplicableItem,
  parseCaComparison
} from '../composables/qualityCheckDetail.js'

const pushMock = vi.fn()
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: pushMock })
}))

const globalStubs = {
  'el-dialog': {
    template: '<div v-if="modelValue" class="el-dialog-stub"><slot /></div>',
    props: ['modelValue']
  },
  'el-tag': {
    template: '<span class="el-tag-stub" :class="type"><slot /></span>',
    props: ['type', 'effect']
  }
}

function mountDialog(props = {}) {
  return mount(QualityCheckDetailDialog, {
    props: {
      modelValue: true,
      item: {
        itemCode: 'test_item',
        status: 'MANUAL_REVIEW',
        title: '测试检查项',
        conclusion: '需要人工复核。'
      },
      ...props
    },
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

  it('getStatusMeta/isNotApplicableItem：PASS且结论标明免收/不涉及/不适用时自适应识别为【不涉及】', () => {
    expect(isNotApplicableItem('PASS', '【不涉及】招标文件明确免收投标保证金')).toBe(true)
    expect(isNotApplicableItem('PASS', '招标文件第3.4款规定不要求递交响应担保')).toBe(true)
    expect(isNotApplicableItem('PASS', '本项目不适用')).toBe(true)
    expect(isNotApplicableItem('FAIL', '【不涉及】xxx')).toBe(false)
    expect(isNotApplicableItem('PASS', '已附投标保证金转账回单')).toBe(false)

    expect(getStatusMeta('PASS', '【不涉及】招标文件未要求缴纳保证金')).toEqual({
      icon: '✓', tagType: 'info', text: '不涉及'
    })
    expect(formatStatus('PASS', '【不涉及】招标文件未要求缴纳保证金')).toBe('不涉及')
    expect(formatStatus('PASS', '正常通过')).toBe('通过')
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

  it('extractKbLinks：支持五库/资源库映射（含 ca-certificate 直达 CA 证书管理）、按库去重、忽略未知来源', () => {
    const links = extractKbLinks([
      { sourceType: 'KNOWLEDGE_BASE', knowledgeSource: 'qualification' },
      { sourceType: 'KNOWLEDGE_BASE', knowledgeSource: 'qualification' },
      { sourceType: 'KNOWLEDGE_BASE', knowledgeSource: 'brand-authorization' },
      { sourceType: 'KNOWLEDGE_BASE', knowledgeSource: 'ca-certificate' },
      { sourceType: 'KNOWLEDGE_BASE', knowledgeSource: 'unknown-lib' },
      { sourceType: 'BID', knowledgeSource: 'personnel' }
    ])
    expect(links).toEqual([
      { source: 'qualification', label: '资质库', route: '/knowledge/qualification' },
      { source: 'brand-authorization', label: '品牌授权库', route: '/knowledge/brand-auth' },
      { source: 'ca-certificate', label: 'CA证书管理', route: '/resource/ca-management' }
    ])
    expect(extractKbLinks([])).toEqual([])
  })

  it('isCaCertificateItem：准确识别 CA_CERTIFICATE 检查项', () => {
    expect(isCaCertificateItem('CA_CERTIFICATE')).toBe(true)
    expect(isCaCertificateItem('ca_certificate')).toBe(true)
    expect(isCaCertificateItem('QUALIFICATION_MATCH')).toBe(false)
    expect(isCaCertificateItem(null)).toBe(false)
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

  it('getQualDetailStatusMeta / isQualificationMatchItem：资质比对状态与检查项识别', () => {
    expect(getQualDetailStatusMeta('MATCHED')).toEqual({ text: '已满足', tagType: 'success' })
    expect(getQualDetailStatusMeta('ATTENTION')).toEqual({ text: '需关注', tagType: 'warning' })
    expect(getQualDetailStatusMeta('EXPIRED')).toEqual({ text: '已过期', tagType: 'danger' })
    expect(getQualDetailStatusMeta('UNMATCHED')).toEqual({ text: '未匹配', tagType: 'danger' })
    expect(getQualDetailStatusMeta('OTHER')).toEqual({ text: '待确认', tagType: 'info' })

    expect(isQualificationMatchItem('QUALIFICATION_MATCH')).toBe(true)
    expect(isQualificationMatchItem('qualification_match')).toBe(true)
    expect(isQualificationMatchItem('QUALIFICATION_VETO')).toBe(true)
    expect(isQualificationMatchItem('cover_info')).toBe(false)
    expect(isQualificationMatchItem(null)).toBe(false)
  })

  it('parseCaComparison：正确解析三段式结论并提取需求、台账现状与状态', () => {
    const failConclusion = `【招标文件要求】：须使用【河北 CA】CA数字证书/电子签章进行电子投标。
【企业CA台账比对】：【未拥有】未在 CA 证书管理中检索到匹配【河北 CA】平台的有效 CA 证书。
【判定结论】：未满足本项目电子投标用锁要求，存在无法递交与解密风险！`
    const parsedFail = parseCaComparison(failConclusion, [{ sourceType: 'TENDER', quote: '编制需使用河北 CA' }])
    expect(parsedFail.requirementText).toBe('须使用【河北 CA】CA数字证书/电子签章进行电子投标。')
    expect(parsedFail.tenderQuote).toBe('编制需使用河北 CA')
    expect(parsedFail.stockText).toContain('【未拥有】')
    expect(parsedFail.statusClass).toBe('not-have')
    expect(parsedFail.statusText).toBe('未拥有')
    expect(parsedFail.tagType).toBe('danger')

    const passConclusion = `【招标文件要求】：须使用【河北 CA】CA数字证书/电子签章进行电子投标。
【企业CA台账比对】：【已拥有】已在【CA证书管理】检索到匹配【河北 CA】的有效 CA 证书。
【判定结论】：在库可用，满足电子投标及开标解密要求。`
    const parsedPass = parseCaComparison(passConclusion, [
      { sourceType: 'KNOWLEDGE_BASE', knowledgeSource: 'ca-certificate', matched: true, quote: 'CA证书: 河北CA（有效，在库）' }
    ])
    expect(parsedPass.statusClass).toBe('have')
    expect(parsedPass.statusText).toBe('已拥有')
    expect(parsedPass.tagType).toBe('success')
    expect(parsedPass.matchedCertDetail).toBe('CA证书: 河北CA（有效，在库）')
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
        suggestion: '请核对招标要求的具体证书材料。',
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
    expect(text).toContain('请核对招标要求的具体证书材料。')
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
          { sourceType: 'KNOWLEDGE_BASE', knowledgeSource: 'qualification', sourceRecordId: 'q-1', matched: true }
        ]
      }
    })
    await wrapper.find('.dt-source').trigger('click')
    expect(pushMock).toHaveBeenCalledWith('/knowledge/qualification')
  })

  it('qualification_match 项防御过滤非资质库链接（不显示业绩库等杂音）', () => {
    const wrapper = mountDialog({
      item: {
        itemCode: 'qualification_match', status: 'FAIL', title: '资质匹配',
        conclusion: '缺少证书。',
        evidences: [
          { sourceType: 'KNOWLEDGE_BASE', knowledgeSource: 'qualification', sourceRecordId: 'q-1', matched: true },
          { sourceType: 'KNOWLEDGE_BASE', knowledgeSource: 'performance', sourceRecordId: 'p-1', matched: true }
        ]
      }
    })
    const links = wrapper.findAll('.dt-source')
    expect(links.length).toBe(1)
    expect(links[0].text()).toContain('资质库')
  })

  it('非 qualification_match/veto 项（如 qualification_review）保留业绩库等来源', () => {
    const wrapper = mountDialog({
      item: {
        itemCode: 'qualification_review', status: 'FAIL', title: '资质业绩审查',
        conclusion: '待审查。',
        evidences: [
          { sourceType: 'KNOWLEDGE_BASE', knowledgeSource: 'qualification', sourceRecordId: 'q-1', matched: true },
          { sourceType: 'KNOWLEDGE_BASE', knowledgeSource: 'performance', sourceRecordId: 'p-1', matched: true }
        ]
      }
    })
    const links = wrapper.findAll('.dt-source')
    expect(links.length).toBe(2)
  })

  it('CA_CERTIFICATE 项点击联动数据来源直达 /resource/ca-management，并在结论中提示录入证书', async () => {
    const wrapper = mountDialog({
      item: {
        itemCode: 'CA_CERTIFICATE',
        status: 'FAIL',
        title: '电子标 CA 数字证书及签章',
        conclusion: '【核查不通过】未在系统 CA 库中匹配到对应平台的有效 CA 证书。请前往【资源管理-CA证书管理】录入对应交易平台的 CA 证书。',
        evidences: []
      }
    })
    const sourceLink = wrapper.find('.dt-source')
    expect(sourceLink.exists()).toBe(true)
    expect(sourceLink.text()).toContain('CA证书管理')
    await sourceLink.trigger('click')
    expect(pushMock).toHaveBeenCalledWith('/resource/ca-management')

    expect(wrapper.text()).toContain('请前往【资源管理-CA证书管理】录入对应交易平台的 CA 证书。')
    expect(wrapper.text()).toContain('高风险项，建议立即修订后重新上传标书并再次执行检查。')
  })

  it('qualification_match 项携带 details 时渲染结构化资质比对清单', () => {
    const wrapper = mountDialog({
      item: {
        itemCode: 'qualification_match',
        status: 'MANUAL_REVIEW',
        title: '资质与证书有效性',
        conclusion: '部分资质需复核。',
        details: [
          {
            requirement: '投标人须具备有效的质量管理体系认证（ISO9001）',
            matchedCertName: 'ISO9001质量管理体系认证',
            status: 'MATCHED',
            bidPageNumber: 28,
            remainingDays: 120,
            remark: '已匹配企业资质库有效证书'
          },
          {
            requirement: '具有有效的安全生产许可证',
            matchedCertName: '安全生产许可证',
            status: 'ATTENTION',
            bidPageNumber: null,
            remainingDays: 15,
            remark: '证书即将到期（剩余 15 天）'
          },
          {
            requirement: '电力工程施工总承包特级资质',
            matchedCertName: '',
            status: 'UNMATCHED',
            bidPageNumber: null,
            remainingDays: null,
            remark: '企业资质库及投标文件中均未检索到'
          }
        ]
      }
    })
    const text = wrapper.text()
    expect(text).toContain('资质比对清单')
    expect(text).toContain('共 3 项')
    expect(text).toContain('招标资质要求')
    expect(text).toContain('我司持有资质')
    expect(text).toContain('标书位置')
    expect(text).toContain('核查说明')
    expect(text).toContain('ISO9001质量管理体系认证')
    expect(text).toContain('第 28 页')
    expect(text).toContain('剩余 120 天')
    expect(text).toContain('剩余 15 天')
    expect(text).toContain('（资质库未匹配）')
    expect(text).toContain('未定位')
  })

  it('qualification_match 项 details 为空时不渲染资质比对清单', () => {
    const wrapper = mountDialog({
      item: {
        itemCode: 'qualification_match',
        status: 'PASS',
        title: '资质与证书有效性',
        conclusion: '全部通过。',
        details: []
      }
    })
    expect(wrapper.find('.qual-compare-table').exists()).toBe(false)
  })

  it('CA_CERTIFICATE 项渲染专属 CA 证书供需比对看板（招标文件要求 vs 企业 CA 台账比对）', async () => {
    const wrapper = mountDialog({
      item: {
        itemCode: 'CA_CERTIFICATE',
        status: 'FAIL',
        title: 'CA证书及电子签章有效性',
        conclusion: `【招标文件要求】：须使用【河北 CA】CA数字证书/电子签章进行电子投标。
【企业CA台账比对】：【未拥有】未在 CA 证书管理中检索到匹配【河北 CA】平台的有效 CA 证书。
【判定结论】：未满足本项目电子投标用锁要求，存在无法递交与解密风险！`,
        evidences: [
          { sourceType: 'TENDER', quote: '编制投标文件需使用企业河北 CA' }
        ]
      }
    })
    const text = wrapper.text()
    expect(text).toContain('CA 证书供需比对')
    expect(text).toContain('未拥有')
    expect(text).toContain('招标文件要求')
    expect(text).toContain('须使用【河北 CA】CA数字证书/电子签章进行电子投标。')
    expect(text).toContain('“编制投标文件需使用企业河北 CA”')
    expect(text).toContain('企业 CA 台账比对')
    expect(text).toContain('未在 CA 证书管理中检索到匹配【河北 CA】平台的有效 CA 证书。')
    expect(text).toContain('前往【CA证书管理】查看台账 ↗')

    const jumpLink = wrapper.find('.ca-jump-link a')
    expect(jumpLink.exists()).toBe(true)
    await jumpLink.trigger('click')
    expect(pushMock).toHaveBeenCalledWith('/resource/ca-management?tab=ca')
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

  it('状态图标与标签状态映射（含不涉及自适应）', () => {
    const pass = mountRow({ itemCode: 'a', status: 'PASS', title: 'A', conclusion: '' })
    const fail = mountRow({ itemCode: 'b', status: 'FAIL', title: 'B', conclusion: '' })
    const notApplicable = mountRow({
      itemCode: 'DEPOSIT_RECEIPT',
      status: 'PASS',
      title: '投标保证金回单',
      conclusion: '【不涉及】招标文件未要求缴纳保证金'
    })
    expect(pass.find('.item-icon').text()).toBe('✓')
    expect(pass.text()).toContain('通过')
    expect(fail.find('.item-icon').text()).toBe('✗')
    expect(fail.text()).toContain('不通过')
    expect(notApplicable.find('.item-icon').text()).toBe('✓')
    expect(notApplicable.text()).toContain('不涉及')
  })
})

describe('质量核查建议证据边界', () => {
  it('优先展示后端给出的具体修订动作', () => {
    const wrapper = mountDialog({ item: { itemCode: 'SCORING_RESPONSE', status: 'FAIL',
      title: '评分项响应', conclusion: '项目经理字段留白', suggestion: '请填写项目经理姓名并补充任职证明。' } })
    expect(wrapper.text()).toContain('请填写项目经理姓名并补充任职证明。')
    expect(wrapper.text()).not.toContain('高风险项，建议立即修订后重新上传标书并再次执行检查。')
  })
})
