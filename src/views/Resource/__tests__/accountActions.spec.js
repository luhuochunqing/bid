import { describe, expect, it } from 'vitest'
import { resolveAccountActions, canRevealPassword } from '../accountActions.js'

describe('accountActions — 项目负责人视角', () => {
  it('项目负责人只能发起借用申请', () => {
    const actions = resolveAccountActions({
      isManager: false,
      isBidTeam: false,
      isContactPerson: false,
      isApplicant: true
    })

    expect(actions).toEqual({ apply: true })
    expect(actions.edit).toBeUndefined()
    expect(actions.takeDown).toBeUndefined()
    expect(actions.return).toBeUndefined()
  })

  it('项目负责人不可查看账户密码', () => {
    const canReveal = canRevealPassword({
      isManager: false,
      isBidTeam: false,
      isContactPerson: false,
      isBorrowerWithinWindow: false
    })

    expect(canReveal).toBe(false)
  })
})
