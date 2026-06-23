import { describe, expect, it } from 'vitest'
import tenderCreateSource from './TenderCreatePage.vue?raw'
import tenderActionBarSource from './list/components/TenderActionBar.vue?raw'

describe('TenderCreatePage button state machine', () => {
  it('shows cancel + save when tender not yet created', () => {
    expect(tenderActionBarSource).toContain('v-else-if="!createdTenderId"')
    expect(tenderActionBarSource).toContain('@click="$emit(\'cancel\')">取消</el-button>')
    expect(tenderActionBarSource).toContain('@click="$emit(\'save\')">保存</el-button>')
  })

  it('shows return + assign for admin/lead when PENDING_ASSIGNMENT', () => {
    expect(tenderActionBarSource).toContain("tenderStatus === 'PENDING_ASSIGNMENT' && isAdminOrLead")
    expect(tenderActionBarSource).toContain('返回列表</el-button>')
    expect(tenderActionBarSource).toContain('@click="$emit(\'assign\')">分配</el-button>')
  })

  it('shows return + next-step on basic tab when TRACKING and authorized', () => {
    expect(tenderActionBarSource).toContain("tenderStatus === 'TRACKING' && canProceedToNext")
    expect(tenderActionBarSource).toContain("@click=\"$emit('next-step')\">下一步</el-button>")
  })

  it('shows return + submit on evaluation tab when TRACKING and authorized', () => {
    expect(tenderActionBarSource).toContain("@click=\"$emit('submit-eval')\">提交</el-button>")
    expect(tenderActionBarSource).toContain(':loading="submittingEval"')
  })

  it('falls back to return-button-only for unauthorized or other states', () => {
    const vElsePattern = /<template v-else>\s*<el-button[^>]*@click="\$emit\('cancel'\)">返回列表<\/el-button>\s*<\/template>/
    expect(tenderActionBarSource).toMatch(vElsePattern)
  })

  it('has isAdminOrLead computed using isBidManager', () => {
    expect(tenderCreateSource).toContain('const isAdminOrLead = computed')
    expect(tenderCreateSource).toContain('isBidManager')
  })

  it('has canProceedToNext checking TRACKING + project leader match only', () => {
    expect(tenderCreateSource).toContain('const canProceedToNext = computed')
    expect(tenderCreateSource).toContain("tenderStatus.value === 'TRACKING'")
    expect(tenderCreateSource).toContain('projectManagerId === currentUserId.value')
  })

  it('disables save while document upload or parse is still running', () => {
    expect(tenderActionBarSource).toContain('parsingDocument: Boolean')
    expect(tenderActionBarSource).toContain(':disabled="!canSave || parsingDocument"')
    expect(tenderCreateSource).toContain(':parsing-document="parsingDocument"')
    expect(tenderCreateSource).toContain('if (parsingDocument.value)')
  })

  it('fetches tender detail after successful save', () => {
    expect(tenderCreateSource).toContain('await fetchTenderDetail()')
  })

  it('integrates AssignDialog for assignment flow', () => {
    expect(tenderCreateSource).toContain('<AssignDialog')
    expect(tenderCreateSource).toContain('@submit="doAssign"')
    expect(tenderCreateSource).toContain('batchTendersApi')
  })

  it('doAssign receives payload parameter for AssignDialog integration', () => {
    const doAssignPattern = /async function doAssign\(payload\)/
    expect(tenderCreateSource).toMatch(doAssignPattern)
    expect(tenderCreateSource).toContain("payload?.assignee ?? assignForm.value.assignee")
    expect(tenderCreateSource).toContain("payload?.remark ?? assignForm.value.remark")
  })

  it('unified evaluation submit delegates to handleSubmitEvaluation', () => {
    expect(tenderCreateSource).toContain('async function handleSubmitEvaluation()')
  })

  it('handleNextStep switches to evaluation tab instead of navigating', () => {
    expect(tenderCreateSource).toContain("activeTab.value = 'evaluation'")
    const handleNextStepBlock = tenderCreateSource.match(/function handleNextStep\(\)\s*\{[^}]*\}/m)
    expect(handleNextStepBlock).toBeTruthy()
    expect(handleNextStepBlock[0]).toContain("activeTab.value = 'evaluation'")
  })
})
