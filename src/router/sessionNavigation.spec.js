// Input: registered login navigator behavior
// Output: unit tests for resilient login navigation fallback
// Pos: src/router/ - Session navigation bridge test
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { beforeEach, describe, expect, it, vi } from 'vitest'

const loadModule = async () => {
  vi.resetModules()
  return import('./sessionNavigation.js')
}

describe('sessionNavigation', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('falls back to hard redirect when registered login navigation rejects', async () => {
    const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {})
    const { navigateToLogin, registerLoginNavigator } = await loadModule()
    const assign = vi.fn()
    const originalLocation = window.location

    try {
      delete window.location
      window.location = {
        pathname: '/bidding',
        assign,
      }

      registerLoginNavigator(() => Promise.reject(new Error('router failed')))

      await navigateToLogin()

      expect(assign).toHaveBeenCalledWith('/login')
      expect(warnSpy).toHaveBeenCalledWith(
        'Navigation to login failed, falling back to hard redirect:',
        expect.any(Error)
      )
    } finally {
      window.location = originalLocation
    }
  })
})
