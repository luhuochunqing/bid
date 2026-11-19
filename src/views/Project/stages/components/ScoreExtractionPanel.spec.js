import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import ScoreExtractionPanel from './ScoreExtractionPanel.vue'
const item = (id, more = {}) => ({ itemId: id, schemeId: 's', name: id, category: '商务', computable: true, rawMaximum: 10, effectiveMaximum: 10, schemeRatio: null, scoreUnit: 'POINTS', validationStatus: 'REVIEW_REQUIRED', diagnostics: [], fieldSources: { ruleText: 'SOURCE', rawMaximum: 'MODEL_CLAIM' }, ruleText: '完整原文：必须提交盖章材料\n未提交不得分', materialRequirements: [], fact: { sourceMetadata: { modelRuleText: '模型概括', sourceSpans: [{ documentId: 'doc', pageNumber: 4, locator: '第4页第一行', rawText: '原始共享格' }] } }, ...more })
const snapshot = () => ({ taskId: 'new', taskStatus: 'COMPLETED', validationStatus: 'MANUAL_APPROVED', extractedAt: '2026-09-27T00:00:00', payloadVersion: 1, snapshotStatus: 'FROZEN', schemes: [{ schemeId: 's', title: '评分方案', extractedCount: 13, extractedKnownTotal: 100, extractedTotalComplete: false, computableCount: 12, computableTotal: 95, activeCount: 0, activeTotal: 0, declaredTotal: null, declaredTotalStatus: 'UNKNOWN' }], items: [item('可计算'), item('未知项', { computable: false, rawMaximum: null, effectiveMaximum: null, fieldSources: { ruleText: 'MODEL_CLAIM' }, diagnostics: ['FULL_RULE_UNIT_UNRESOLVED', 'WEIGHT_UNKNOWN'] }), item('父项', { computable: false, diagnostics: ['NOT_EFFECTIVE_SCORING_UNIT'] }), item('方案外', { schemeId: '', computable: false, diagnostics: ['OUT_OF_SCHEME'] })], adoption: { status: 'PARTIAL', adoptedItemIds: ['可计算'], remainingItemIds: ['未知项'] }, active: { taskId: 'old', fingerprint: 'fp', activatedAt: '2026-09-20T00:00:00', hasManualEdits: true } })
describe('ScoreExtractionPanel', () => {
  it('shows effective scheme metrics and all candidates including unadopted and structural rows', () => {
    const w = mount(ScoreExtractionPanel, { props: { snapshot: snapshot(), activeItems: [{ id: 9, weight: 100 }], activeTotal: 100 } })
    expect(w.text()).toContain('13 项')
    expect(w.text()).toContain('已确认分值：100 分')
    expect(w.text()).toContain('招标文件总分：待确认')
    for (const label of ['可计算', '未知项', '父项', '方案外', '未采纳', '未核验', '暂不计入总分', '当前用于评分：1 项 / 100 分']) expect(w.text()).toContain(label)
    expect(w.findAll('input[type="checkbox"]:not(:disabled)')).toHaveLength(1)
    expect(w.text()).not.toContain('已完成复核')
  })
  it('keeps full rule, shared source and model explanation distinct; no missing materials assertion', () => {
    const w = mount(ScoreExtractionPanel, { props: { snapshot: snapshot() } })
    for (const label of ['必须提交盖章材料', '未提交不得分', '模型概括', '原始共享格', '第4页第一行', '本项完整原文边界待核对', '候选最大分（模型提取，未核验）']) expect(w.text()).toContain(label)
    expect(w.text()).not.toContain('无材料要求')
    expect(w.text()).not.toContain('FULL_RULE_UNIT_UNRESOLVED')
  })
  it('requires selection and reason, emits replacement selection with diff visible', async () => {
    const w = mount(ScoreExtractionPanel, { props: { snapshot: snapshot(), activeItems: [{ id: 9, dim: '旧规则', detail: '旧内容', weight: 100 }], activeTotal: 100 } })
    const button = w.get('[data-test="activate-extraction"]')
    expect(button.attributes('disabled')).toBeDefined()
    await w.get('input[type="checkbox"]:not(:disabled)').setValue(true)
    await w.get('textarea').setValue('核对后选取')
    expect(w.text()).toContain('完整替换')
    expect(w.text()).toContain('旧规则')
    await button.trigger('click')
    expect(w.emitted('activate')[0][0]).toEqual({ schemeId: 's', selectedItemIds: ['可计算'], reason: '核对后选取' })
  })
})

it('requires explicit scheme choice and never selects other-scheme or unsupported weighted candidates', async () => {
  const data = snapshot()
  data.schemes.push({ schemeId: 'other', title: '另一方案', extractedCount: 1, computableCount: 1, computableTotal: 200, extractedKnownTotal: 200, extractedTotalComplete: true, declaredTotal: 200, declaredTotalStatus: 'KNOWN' })
  data.items.push(item('比例项', { computable: false, rawMaximum: 30, schemeRatio: null, effectiveMaximum: null, scoreUnit: 'PERCENTAGE', diagnostics: ['RATIO_UNRESOLVED'] }), item('另一方案项', { schemeId: 'other', effectiveMaximum: 200 }))
  const w = mount(ScoreExtractionPanel, { props: { snapshot: data } })
  expect(w.findAll('input[type="checkbox"]:not(:disabled)')).toHaveLength(0)
  await w.get('select').setValue('other')
  expect(w.text()).toContain('招标文件总分：200 分')
  expect(w.findAll('input[type="checkbox"]:not(:disabled)')).toHaveLength(1)
  expect(w.text()).toContain('方案比例：未提供')
  expect(w.text()).not.toContain('RATIO_UNRESOLVED')
})

it('does not invent a zero active total for an absent active version', () => {
  const w = mount(ScoreExtractionPanel, { props: { snapshot: snapshot() } })
  expect(w.text()).toContain('当前用于评分：0 项 / 待确认')
  expect(w.text()).not.toContain('还差 0 分')
})

it('shows the unresolved gap when the extracted result is short of the declared total', () => {
  const data = snapshot()
  data.schemes[0].declaredTotal = 100
  data.schemes[0].declaredTotalStatus = 'KNOWN'
  data.schemes[0].extractedKnownTotal = 91
  const w = mount(ScoreExtractionPanel, { props: { snapshot: data, activeItems: [{ id: 9, weight: 91 }], activeTotal: 91 } })
  expect(w.text()).toContain('本次解析仍有 9 分待核对')
  expect(w.text()).toContain('距招标文件声明总分还差 9 分')
})

it('shows excess points for human review rather than treating a total over the source maximum as complete', () => {
  const data = snapshot()
  Object.assign(data.schemes[0], { declaredTotal: 100, declaredTotalStatus: 'KNOWN', extractedKnownTotal: 105 })
  const w = mount(ScoreExtractionPanel, { props: { snapshot: data } })
  expect(w.text()).toContain('超过招标文件总分 5 分，请人工复核')
})

it('does not compare against an unverified source total', () => {
  const data = snapshot()
  Object.assign(data.schemes[0], { declaredTotal: 100, declaredTotalStatus: 'UNKNOWN', extractedKnownTotal: 91 })
  const w = mount(ScoreExtractionPanel, { props: { snapshot: data } })
  expect(w.text()).toContain('招标文件总分：待确认')
  expect(w.text()).not.toContain('9 分待核对')
})

it('allows selecting a verified zero-point deduction without adding points', async () => {
  const data = snapshot()
  data.items = [item('扣分项', { rawMaximum: 0, effectiveMaximum: 0, ruleText: '验收不通过扣1分。' })]
  const w = mount(ScoreExtractionPanel, { props: { snapshot: data } })
  expect(w.text()).toContain('扣分规则')
  await w.get('input[type="checkbox"]').setValue(true)
  await w.get('textarea').setValue('核对原文')
  await w.get('[data-test="activate-extraction"]').trigger('click')
  expect(w.emitted('activate')[0][0].selectedItemIds).toEqual(['扣分项'])
})

it('shows task source diagnostics even when the latest extraction has no items and older items remain active', () => {
  const data = snapshot()
  Object.assign(data, { items: [], schemes: [], sourceDiagnostics: ['SOURCE_SCORE_ROW_UNCOVERED:table-r4'] })
  const w = mount(ScoreExtractionPanel, { props: { snapshot: data, activeItems: [{ id: 9, dim: '旧正式项', weight: 100 }], activeTotal: 100 } })
  expect(w.get('[data-test="extraction-source-diagnostics"]').text()).toContain('原文评分表仍有未完整覆盖的评分行')
  expect(w.text()).toContain('SOURCE_SCORE_ROW_UNCOVERED:table-r4')
  expect(w.text()).toContain('当前用于评分：1 项 / 100 分')
  expect(w.text()).toContain('本次尚无候选项')
})

it('keeps unknown source diagnostic details readable as text', () => {
  const data = { ...snapshot(), sourceDiagnostics: ['UNFAMILIAR:<img src=x onerror=alert(1)>'] }
  const w = mount(ScoreExtractionPanel, { props: { snapshot: data } })
  expect(w.get('[data-test="extraction-source-diagnostics"]').text()).toContain('UNFAMILIAR:<img src=x onerror=alert(1)>')
  expect(w.find('img').exists()).toBe(false)
})

it('displays objective and subjective scoreType labels on candidate items', () => {
  const data = snapshot()
  data.items = [
    item('客观指标', { scoreType: 'OBJECTIVE' }),
    item('主观方案', { scoreType: 'SUBJECTIVE' }),
  ]
  const w = mount(ScoreExtractionPanel, { props: { snapshot: data } })
  const cards = w.findAll('article.candidate')
  expect(cards).toHaveLength(2)
  expect(cards[0].text()).toContain('客观项')
  expect(cards[1].text()).toContain('主观项')
})

it.each(['', '1', '一', '评分项 21', '3 | 营业总收入', '商务评分（20'])(
  'excludes invalid historical name %j while allowing a valid rule',
  async (name) => {
    const data = snapshot()
    data.items = [
      item('invalid', { name, computable: true }),
      item('valid', { name: '营业总收入', computable: true }),
    ]
    const w = mount(ScoreExtractionPanel, { props: { snapshot: data } })
    const invalid = w.get('[data-item-id="invalid"]')
    expect(invalid.get('input[type="checkbox"]').attributes('disabled')).toBeDefined()
    expect(invalid.text()).toContain('评分项名称无效')
    const valid = w.get('[data-item-id="valid"] input[type="checkbox"]')
    expect(valid.attributes('disabled')).toBeUndefined()
    await valid.setValue(true)
    await w.get('textarea').setValue('核对后选取')
    await w.get('[data-test="activate-extraction"]').trigger('click')
    expect(w.emitted('activate')[0][0].selectedItemIds).toEqual(['valid'])
  },
)
