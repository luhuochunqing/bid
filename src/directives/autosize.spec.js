import { describe, expect, it } from 'vitest'
import { vAutosize } from './autosize.js'

describe('vAutosize directive', () => {
  it('sets textarea height to scrollHeight on mounted', () => {
    const ta = document.createElement('textarea')
    // jsdom 默认 scrollHeight=0，模拟一个非零值
    Object.defineProperty(ta, 'scrollHeight', { configurable: true, get: () => 200 })
    document.body.appendChild(ta)

    vAutosize.mounted(ta)

    expect(ta.style.height).toBe('200px')

    ta.remove()
  })

  it('resets height to auto before measuring so shrink works', () => {
    const ta = document.createElement('textarea')
    Object.defineProperty(ta, 'scrollHeight', { configurable: true, get: () => 80 })
    ta.style.height = '500px' // 模拟之前撑开的状态
    document.body.appendChild(ta)

    vAutosize.updated(ta)

    expect(ta.style.height).toBe('80px')

    ta.remove()
  })
})
