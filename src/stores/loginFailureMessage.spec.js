// Input: loginFailureMessage.js
// Output: unit tests for user-facing login failure messages
// Pos: src/stores/ - Login error presentation tests
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { describe, expect, it } from 'vitest'
import { resolveLoginFailureMessage } from './loginFailureMessage.js'

describe('resolveLoginFailureMessage', () => {
  it('maps login 401 failures to an explicit password error', () => {
    expect(resolveLoginFailureMessage({
      response: {
        status: 401,
        data: { msg: '用户名或密码错误' },
      },
    })).toBe('密码错误，请重新输入')
  })

  it('maps normalized bad credential messages to an explicit password error', () => {
    expect(resolveLoginFailureMessage({ message: 'Bad credentials' })).toBe('密码错误，请重新输入')
  })

  it('preserves non-authentication business messages', () => {
    expect(resolveLoginFailureMessage({ message: '账号已停用' })).toBe('账号已停用')
  })
})
