import { describe, expect, it } from 'vitest'
import tenderCreateSource from './TenderCreatePage.vue?raw'

describe('TenderCreatePage button state machine', () => {
  it('shows cancel + save when tender not yet created', () => {
    expect(tenderCreateSource).toContain('v-else-if="!createdTenderId"')
    expect(tenderCreateSource).toContain('@click="handleCancel">取消</el-button>')
    expect(tenderCreateSource).toContain('@click="handleSave">保存</el-button>')
  })

  it('shows return + assign for admin/lead when PENDING_ASSIGNMENT', () => {
    expect(tenderCreateSource).toContain("tenderStatus === 'PENDING_ASSIGNMENT' && isAdminOrLead")
    expect(tenderCreateSource).toContain('返回列表</el-button>')
    expect(tenderCreateSource).toContain('@click="openAssignDialog">分配</el-button>')
  })

  it('shows return + next-step on basic tab when TRACKING and authorized', () => {
    expect(tenderCreateSource).toContain("tenderStatus === 'TRACKING' && canProceedToNext")
    expect(tenderCreateSource).toContain("@click=\"handleNextStep\">下一步</el-button>")
  })

  it('shows return + submit on evaluation tab when TRACKING and authorized', () => {
    expect(tenderCreateSource).toContain("@click=\"handleSubmitEvaluation\">提交</el-button>")
    expect(tenderCreateSource).toContain(':loading="submittingEval"')
  })

  it('falls back to return-button-only for unauthorized or other states', () => {
    // v-else fallback should exist
    const vElsePattern = /<template v-else>\s*<el-button[^>]*@click="handleCancel">返回列表<\/el-button>\s*<\/template>/
    expect(tenderCreateSource).toMatch(vElsePattern)
  })

  it('has isAdminOrLead computed from userRole', () => {
    expect(tenderCreateSource).toContain('const isAdminOrLead = computed')
    expect(tenderCreateSource).toContain("role === 'bid_admin' || role === 'bid_lead' || role === 'admin'")
  })

  it('has canProceedToNext checking TRACKING + project leader match only', () => {
    expect(tenderCreateSource).toContain('const canProceedToNext = computed')
    expect(tenderCreateSource).toContain("tenderStatus.value !== 'TRACKING'")
    expect(tenderCreateSource).toContain('projectLeaderId.value && currentUserId.value === projectLeaderId.value')
    expect(tenderCreateSource).not.toContain('isAdminOrLead.value')
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
    // doAssign must accept payload to receive { assignee, remark } from AssignDialog
    const doAssignPattern = /async function doAssign\(payload\)/
    expect(tenderCreateSource).toMatch(doAssignPattern)
    // Payload values should be used with fallback to assignForm
    expect(tenderCreateSource).toContain("payload?.assignee ?? assignForm.value.assignee")
    expect(tenderCreateSource).toContain("payload?.remark ?? assignForm.value.remark")
  })

  it('unified evaluation submit delegates to handleSubmitEvaluation', () => {
    expect(tenderCreateSource).toContain('async function handleEvaluationSubmit()')
    expect(tenderCreateSource).toContain('await handleSubmitEvaluation()')
  })

  it('handleNextStep switches to evaluation tab instead of navigating', () => {
    expect(tenderCreateSource).toContain("activeTab.value = 'evaluation'")
    // Verify handleNextStep does set activeTab, and does NOT navigate to detail page
    // The pattern "router.push(`/bidding/" only exists in handleSubmitEvaluation
    const handleNextStepBlock = tenderCreateSource.match(/function handleNextStep[\s\S]*?^}/m)
    expect(handleNextStepBlock).toBeTruthy()
    expect(handleNextStepBlock[0]).not.toContain('router.push')
    expect(handleNextStepBlock[0]).toContain("activeTab.value = 'evaluation'")
  })
})
