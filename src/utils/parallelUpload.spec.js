import { describe, expect, it, vi } from 'vitest'
import { parallelUpload } from './parallelUpload'

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

describe('parallelUpload', () => {
  it('全部成功时返回 successes 且 failures 为空', async () => {
    const files = ['a', 'b', 'c']
    const uploader = vi.fn(async (file) => `ok-${file}`)
    const { successes, failures } = await parallelUpload(files, uploader)
    expect(successes.map((s) => s.file)).toEqual(['a', 'b', 'c'])
    expect(successes.map((s) => s.result)).toEqual(['ok-a', 'ok-b', 'ok-c'])
    expect(failures).toHaveLength(0)
    expect(uploader).toHaveBeenCalledTimes(3)
  })

  it('部分失败时不阻塞其他文件，失败项汇总到 failures', async () => {
    const files = ['a', 'b', 'c']
    const uploader = vi.fn(async (file) => {
      if (file === 'b') throw new Error('b failed')
      return `ok-${file}`
    })
    const { successes, failures } = await parallelUpload(files, uploader)
    expect(successes.map((s) => s.file)).toEqual(['a', 'c'])
    expect(failures).toHaveLength(1)
    expect(failures[0].file).toBe('b')
    expect(failures[0].error.message).toBe('b failed')
  })

  it('并发上限为 1 时退化为串行（验证 worker 池控制）', async () => {
    const files = ['a', 'b', 'c']
    const order = []
    const uploader = vi.fn(async (file) => {
      order.push(file)
      await sleep(5)
      return `ok-${file}`
    })
    const { successes } = await parallelUpload(files, uploader, { concurrency: 1 })
    expect(successes.map((s) => s.file)).toEqual(['a', 'b', 'c'])
    // 串行执行顺序应与输入顺序一致
    expect(order).toEqual(['a', 'b', 'c'])
  })

  it('空文件列表返回空结果，不调用 uploader', async () => {
    const uploader = vi.fn()
    const { successes, failures } = await parallelUpload([], uploader)
    expect(successes).toHaveLength(0)
    expect(failures).toHaveLength(0)
    expect(uploader).not.toHaveBeenCalled()
  })

  it('结果顺序与输入文件顺序一致（不因并发完成顺序错乱）', async () => {
    const files = ['slow', 'fast', 'medium']
    const delays = { slow: 30, fast: 5, medium: 15 }
    const uploader = vi.fn(async (file) => {
      await sleep(delays[file])
      return file
    })
    const { successes } = await parallelUpload(files, uploader, { concurrency: 3 })
    expect(successes.map((s) => s.file)).toEqual(['slow', 'fast', 'medium'])
    expect(successes.map((s) => s.result)).toEqual(['slow', 'fast', 'medium'])
  })
})
