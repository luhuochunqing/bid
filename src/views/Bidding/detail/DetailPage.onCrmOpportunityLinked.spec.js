import { describe, expect, it } from 'vitest'
import detailPageSource from './DetailPage.vue?raw'

/**
 * CO-310 修复（回填链路恢复）：验证 onCrmOpportunityLinked 函数通过扩展后的
 * linkCrmOpportunity 端点一步完成关联+回填，不再单独调用 saveEvaluationDraft
 * 和 submitEvaluationFinal（原链路因 canFill 守卫导致 sales 403）。
 *
 * <p>修复方案：后端 linkCrmOpportunity 新增 evaluationPayload 字段，调用
 * backfillFromCrmLink 绕过 canFill 守卫；前端组装 evaluationPayload 传入。
 *
 * <p>采用源码字符串断言,与现有 DetailPage.spec.js 风格一致。
 * 函数边界通过 "async function onCrmOpportunityLinked" 起始 + 下一个 "// ----" 终止界定。
 */
describe('CO-310 修复 — onCrmOpportunityLinked 评估表回填链路恢复', () => {
  // 提取 onCrmOpportunityLinked 函数体(从函数声明到下一个顶层声明)
  const functionStart = detailPageSource.indexOf('async function onCrmOpportunityLinked')
  const functionEnd = detailPageSource.indexOf('// Suppress unused-var warnings')
  const functionBody = detailPageSource.slice(functionStart, functionEnd)

  it('函数存在', () => {
    expect(functionStart).toBeGreaterThan(-1)
    expect(functionEnd).toBeGreaterThan(functionStart)
    expect(functionBody.length).toBeGreaterThan(0)
  })

  it('保留 linkCrmOpportunity 调用(关联 CRM 商机的核心 API)', () => {
    expect(functionBody).toContain('tendersApi.linkCrmOpportunity')
  })

  it('通过 evaluationPayload 字段传递评估表数据（一步完成关联+回填）', () => {
    expect(functionBody).toContain('payload.evaluationPayload')
    expect(functionBody).toContain('evaluationBasic')
    expect(functionBody).toContain('evaluationCustomerInfos')
    // CO-312: 是否投标/弃标原因由项目负责人手动填写，bidRecommendation/evaluationRecommendation
    // 仅在 evaluationData.recommendation 存在时才组装（防御性条件分支，当前 selector 不传）
    expect(functionBody).toContain('if (evaluationData.recommendation)')
    expect(functionBody).toContain('bidRecommendation')
    expect(functionBody).toContain('evaluationRecommendation')
  })

  it('不再单独调用 saveEvaluationDraft（改由后端 backfillFromCrmLink 处理）', () => {
    expect(functionBody).not.toContain('tendersApi.saveEvaluationDraft')
  })

  it('不再单独调用 submitEvaluationFinal（改由后端 backfillFromCrmLink 处理）', () => {
    expect(functionBody).not.toContain('tendersApi.submitEvaluationFinal')
  })

  it('成功提示文案为"CRM商机已关联，请在评估表填写是否投标后提交"（CO-310 两步流程）', () => {
    expect(functionBody).toContain('CRM商机已关联，请在评估表填写是否投标后提交')
  })

  it('保留 loadTenderDetail 刷新(刷新标讯详情)', () => {
    expect(functionBody).toContain('await loadTenderDetail()')
  })

  it('保留 loadEvaluation 刷新(刷新评估表数据)', () => {
    expect(functionBody).toContain('tendersApi.loadEvaluation')
  })
})
