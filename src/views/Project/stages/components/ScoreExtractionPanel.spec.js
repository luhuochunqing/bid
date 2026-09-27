import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import ScoreExtractionPanel from './ScoreExtractionPanel.vue'
const item = (id, more = {}) => ({ itemId: id, schemeId: 's', name: id, category: '商务', computable: true, rawMaximum: 10, effectiveMaximum: 10, schemeRatio: null, scoreUnit: 'POINTS', validationStatus: 'REVIEW_REQUIRED', diagnostics: [], fieldSources: { ruleText: 'SOURCE', rawMaximum: 'MODEL_CLAIM' }, ruleText: '完整原文：必须提交盖章材料\n未提交不得分', materialRequirements: [], fact: { sourceMetadata: { modelRuleText: '模型概括', sourceSpans: [{ documentId: 'doc', pageNumber: 4, locator: '第4页第一行', rawText: '原始共享格' }] } }, ...more })
const snapshot = () => ({ taskId: 'new', taskStatus: 'COMPLETED', validationStatus: 'MANUAL_APPROVED', extractedAt: '2026-09-27T00:00:00', payloadVersion: 1, snapshotStatus: 'FROZEN', schemes: [{ schemeId: 's', title: '评分方案', extractedCount: 13, extractedKnownTotal: 100, extractedTotalComplete: false, computableCount: 12, computableTotal: 95, activeCount: 0, activeTotal: 0, declaredTotal: null, declaredTotalStatus: 'UNKNOWN' }], items: [item('可计算'), item('未知项', { computable: false, rawMaximum: null, effectiveMaximum: null, fieldSources: { ruleText: 'MODEL_CLAIM' }, diagnostics: ['FULL_RULE_UNIT_UNRESOLVED', 'WEIGHT_UNKNOWN'] }), item('父项', { computable: false, diagnostics: ['NOT_EFFECTIVE_SCORING_UNIT'] }), item('方案外', { schemeId: '', computable: false, diagnostics: ['OUT_OF_SCHEME'] })], adoption: { status: 'PARTIAL', adoptedItemIds: ['可计算'], remainingItemIds: ['未知项'] }, active: { taskId: 'old', fingerprint: 'fp', activatedAt: '2026-09-20T00:00:00', hasManualEdits: true } })
describe('ScoreExtractionPanel', () => {
  it('shows effective scheme metrics and all candidates including unadopted and structural rows', () => {
    const w = mount(ScoreExtractionPanel, { props: { snapshot: snapshot(), activeItems: [{ id: 9, weight: 100 }], activeTotal: 100 } })
    expect(w.text()).toContain('13 项')
    expect(w.text()).toContain('100 分（已知部分）')
    expect(w.text()).toContain('声明总分：未知')
    for (const label of ['可计算', '未知项', '父项', '方案外', '未采纳', '未核验', '不计分', '当前生效：1 项 / 100 分']) expect(w.text()).toContain(label)
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
  expect(w.text()).toContain('声明总分：200 分')
  expect(w.findAll('input[type="checkbox"]:not(:disabled)')).toHaveLength(1)
  expect(w.text()).toContain('比例换算依据待确认')
  expect(w.text()).not.toContain('RATIO_UNRESOLVED')
})

it('does not invent a zero active total for an absent active version', () => {
  const w = mount(ScoreExtractionPanel, { props: { snapshot: snapshot() } })
  expect(w.text()).toContain('当前生效：0 项 / 待确认')
})
