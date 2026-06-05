// Input: raw markdown strings (including malicious payloads)
// Output: assertions on renderSafeMarkdown output
// Pos: src/components/common/doc-insight/__tests__/ - unit tests for markdown sanitizer helper
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { describe, it, expect } from 'vitest'
import { renderSafeMarkdown } from '../markdownSanitizer.js'

describe('renderSafeMarkdown', () => {
  // --- edge / null cases ---

  it('returns empty string for null', () => {
    expect(renderSafeMarkdown(null)).toBe('')
  })

  it('returns empty string for undefined', () => {
    expect(renderSafeMarkdown(undefined)).toBe('')
  })

  it('returns empty string for empty string', () => {
    expect(renderSafeMarkdown('')).toBe('')
  })

  it('returns empty string for non-string number', () => {
    expect(renderSafeMarkdown(42)).toBe('')
  })

  it('returns empty string for non-string object', () => {
    expect(renderSafeMarkdown({})).toBe('')
  })

  // --- XSS stripping ---

  it('strips onerror from img XSS payload', () => {
    const xss = '<img src=x onerror=alert(1)>'
    const result = renderSafeMarkdown(xss)
    expect(result).not.toContain('onerror')
    expect(result).not.toContain('src=x')
  })

  it('strips script tags completely', () => {
    const xss = '<script>alert("xss")</script>'
    const result = renderSafeMarkdown(xss)
    expect(result).not.toContain('<script>')
    expect(result).not.toContain('alert')
  })

  it('strips iframe tags', () => {
    const xss = '<iframe src="javascript:alert(1)"></iframe>'
    const result = renderSafeMarkdown(xss)
    expect(result).not.toContain('<iframe')
  })

  it('strips inline on* event attributes injected into allowed tags', () => {
    const xss = '<p onclick="evil()">text</p>'
    const result = renderSafeMarkdown(xss)
    expect(result).not.toContain('onclick')
    expect(result).not.toContain('evil()')
  })

  // --- valid markdown renders to expected HTML tags ---

  it('renders # heading to <h1>', () => {
    const result = renderSafeMarkdown('# Hello World')
    expect(result).toContain('<h1')
    expect(result).toContain('Hello World')
  })

  it('renders ## heading to <h2>', () => {
    const result = renderSafeMarkdown('## Section')
    expect(result).toContain('<h2')
  })

  it('renders **bold** to <strong>', () => {
    const result = renderSafeMarkdown('**bold text**')
    expect(result).toContain('<strong>')
    expect(result).toContain('bold text')
  })

  it('renders *italic* to <em>', () => {
    const result = renderSafeMarkdown('*italic*')
    expect(result).toContain('<em>')
  })

  it('renders unordered list items to <ul> and <li>', () => {
    const result = renderSafeMarkdown('- item one\n- item two')
    expect(result).toContain('<ul>')
    expect(result).toContain('<li>')
    expect(result).toContain('item one')
  })

  it('renders ordered list items to <ol> and <li>', () => {
    const result = renderSafeMarkdown('1. first\n2. second')
    expect(result).toContain('<ol>')
    expect(result).toContain('<li>')
  })

  it('renders [link](url) to <a> with href', () => {
    const result = renderSafeMarkdown('[click here](https://example.com)')
    expect(result).toContain('<a')
    expect(result).toContain('href')
    expect(result).toContain('example.com')
  })

  it('renders inline `code` to <code>', () => {
    const result = renderSafeMarkdown('`some code`')
    expect(result).toContain('<code>')
  })

  it('renders fenced code block to <pre>', () => {
    const result = renderSafeMarkdown('```\ncode block\n```')
    expect(result).toContain('<pre>')
  })
})
