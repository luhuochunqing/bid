// Input: useDeleteGuard composable
// Output: 测试防重复点击守卫逻辑
// Pos: src/composables/ - 防复发测试：DELETE 请求期间禁止同一 documentId 重复触发

import { describe, it, expect, vi } from 'vitest'
import { useDeleteGuard } from './useDeleteGuard.js'

describe('useDeleteGuard - 防重复点击守卫', () => {
  it('documentId 为 null/undefined 时返回 false，不执行 deleteFn', async () => {
    const { safeDelete } = useDeleteGuard()
    const deleteFn = vi.fn()

    const resultNull = await safeDelete(null, deleteFn)
    expect(resultNull).toBe(false)
    expect(deleteFn).not.toHaveBeenCalled()

    const resultUndefined = await safeDelete(undefined, deleteFn)
    expect(resultUndefined).toBe(false)
    expect(deleteFn).not.toHaveBeenCalled()
  })

  it('首次调用：执行 deleteFn 并返回 true', async () => {
    const { safeDelete } = useDeleteGuard()
    const deleteFn = vi.fn().mockResolvedValue('done')

    const result = await safeDelete(842, deleteFn)

    expect(result).toBe(true)
    expect(deleteFn).toHaveBeenCalledTimes(1)
  })

  it('防复发核心断言：await 期间同一 documentId 重复调用被跳过', async () => {
    const { safeDelete } = useDeleteGuard()
    let resolveFirst
    const firstCallPromise = new Promise(resolve => { resolveFirst = resolve })
    const deleteFn = vi.fn(() => firstCallPromise)

    // 第一次调用（未 resolve，处于 pending）
    const firstCall = safeDelete(842, deleteFn)
    // 第二次调用（documentId 相同，应立即返回 false）
    const secondCall = await safeDelete(842, deleteFn)

    expect(secondCall).toBe(false)
    expect(deleteFn).toHaveBeenCalledTimes(1)

    // resolve 第一次调用
    resolveFirst('done')
    const firstResult = await firstCall
    expect(firstResult).toBe(true)
  })

  it('不同 documentId 不互相阻塞', async () => {
    const { safeDelete } = useDeleteGuard()
    let resolveA, resolveB
    const deleteFnA = vi.fn(() => new Promise(r => { resolveA = r }))
    const deleteFnB = vi.fn(() => new Promise(r => { resolveB = r }))

    const callA = safeDelete(842, deleteFnA)
    const callB = safeDelete(843, deleteFnB)

    expect(deleteFnA).toHaveBeenCalledTimes(1)
    expect(deleteFnB).toHaveBeenCalledTimes(1)

    resolveA('done')
    resolveB('done')
    expect(await callA).toBe(true)
    expect(await callB).toBe(true)
  })

  it('deleteFn 抛错时也释放锁，允许重试', async () => {
    const { safeDelete } = useDeleteGuard()
    const deleteFnFail = vi.fn().mockRejectedValue(new Error('network error'))
    const deleteFnSuccess = vi.fn().mockResolvedValue('done')

    // 第一次失败（safeDelete 不向上传播，返回 false）
    const failResult = await safeDelete(842, deleteFnFail)
    expect(failResult).toBe(false)
    // 第二次应能执行（锁已释放）
    const result = await safeDelete(842, deleteFnSuccess)
    expect(result).toBe(true)
    expect(deleteFnFail).toHaveBeenCalledTimes(1)
    expect(deleteFnSuccess).toHaveBeenCalledTimes(1)
  })

  it('isDeleting 状态在删除期间为 true，结束后为 false', async () => {
    const { isDeleting, safeDelete } = useDeleteGuard()
    let resolveDelete
    const deleteFn = vi.fn(() => new Promise(r => { resolveDelete = r }))

    expect(isDeleting.value).toBe(false)

    const call = safeDelete(842, deleteFn)
    expect(isDeleting.value).toBe(true)

    resolveDelete('done')
    await call
    expect(isDeleting.value).toBe(false)
  })

  it('deleteFn 抛错时 isDeleting 恢复为 false', async () => {
    const { isDeleting, safeDelete } = useDeleteGuard()
    const deleteFn = vi.fn().mockRejectedValue(new Error('fail'))

    expect(isDeleting.value).toBe(false)
    const result = await safeDelete(842, deleteFn)
    expect(result).toBe(false)
    expect(isDeleting.value).toBe(false)
  })
})
