// Input: src/utils/safeHtml.js — DOMPurify wrapper used by v-html consumers
// Output: unit tests covering XSS payloads, javascript: URLs, and benign markup
// Pos: src/utils/__tests__/ — security regression for L3 helper
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { describe, expect, it } from 'vitest'
import { safeHtml } from '../safeHtml.js'

describe('safeHtml — XSS sanitization', () => {
  it('strips <script> tags entirely', () => {
    const result = safeHtml('<script>alert(1)</script>')
    expect(result).not.toContain('<script')
    expect(result).not.toContain('alert(1)')
  })

  it('strips onerror handlers from <img> tags', () => {
    const result = safeHtml('<img src=x onerror=alert(1)>')
    expect(result).not.toContain('onerror')
    expect(result).not.toContain('alert(1)')
  })

  it('strips onload and onclick handlers', () => {
    const payload = '<a href="#" onclick="steal()" onload="x()">click</a>'
    const result = safeHtml(payload)
    expect(result).not.toContain('onclick')
    expect(result).not.toContain('onload')
  })

  it('strips javascript: URLs in href', () => {
    const result = safeHtml('<a href="javascript:alert(1)">link</a>')
    expect(result.toLowerCase()).not.toContain('javascript:')
    expect(result).not.toContain('alert(1)')
  })

  it('preserves benign markup like <b>hello</b>', () => {
    const result = safeHtml('<b>hello</b>')
    // Allow the visible text to remain; tag may be stripped depending on allowlist.
    expect(result).toContain('hello')
  })

  it('preserves text content when stripping tags', () => {
    const result = safeHtml('hello world')
    expect(result).toContain('hello')
    expect(result).toContain('world')
  })

  it('returns empty string for empty input', () => {
    expect(safeHtml('')).toBe('')
  })

  it('returns empty string for null', () => {
    expect(safeHtml(null)).toBe('')
  })

  it('returns empty string for undefined', () => {
    expect(safeHtml(undefined)).toBe('')
  })

  it('returns empty string for non-string inputs without throwing', () => {
    expect(() => safeHtml(123)).not.toThrow()
    expect(safeHtml(123)).toBe('')

    expect(() => safeHtml({})).not.toThrow()
    expect(safeHtml({})).toBe('')

    expect(() => safeHtml([])).not.toThrow()
    expect(safeHtml([])).toBe('')

    expect(() => safeHtml(true)).not.toThrow()
    expect(safeHtml(true)).toBe('')
  })

  it('strips nested script tags', () => {
    const payload = '<div><script>alert(1)</script>safe</div>'
    const result = safeHtml(payload)
    expect(result).not.toContain('<script')
    expect(result).toContain('safe')
  })

  it('strips iframe tags', () => {
    const result = safeHtml('<iframe src="evil.com"></iframe>')
    expect(result).not.toContain('<iframe')
    expect(result).not.toContain('evil.com')
  })

  it('does not allow style tag injection', () => {
    const result = safeHtml('<style>body{display:none}</style>')
    expect(result).not.toContain('<style')
  })
})