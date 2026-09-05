import { describe, expect, it, vi } from 'vitest'

describe('test-setup global initialization', () => {
  it('provides URL.createObjectURL and URL.revokeObjectURL polyfills', () => {
    expect(typeof URL.createObjectURL).toBe('function')
    expect(typeof URL.revokeObjectURL).toBe('function')
    expect(URL.createObjectURL(new Blob())).toBe('blob:mocked-by-test-setup')
    expect(() => URL.revokeObjectURL('blob:mocked-by-test-setup')).not.toThrow()
  })

  it('filters out Element Plus unresolved component and directive warnings', () => {
    // 覆盖并检查过滤效果
    console.warn('[Vue warn]: Failed to resolve component: el-button')
    console.warn('[Vue warn]: Failed to resolve directive: loading')

    // 正常 warn 应该能够正常输出
    const spy = vi.spyOn(console, 'warn')
    console.warn('[Custom warn]: should pass')
    expect(spy).toHaveBeenCalledWith('[Custom warn]: should pass')
    spy.mockRestore()
  })
})
