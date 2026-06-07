// Input: raw markdown string (any value)
// Output: sanitized HTML string safe for v-html; empty string on null/non-string input
// Pos: src/components/common/doc-insight/ - markdown-to-safe-HTML helper
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { marked } from 'marked'
import DOMPurify from 'dompurify'

const ALLOWED_TAGS = [
  'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
  'p', 'ul', 'ol', 'li',
  'strong', 'em', 'code', 'pre', 'blockquote',
  'a', 'hr', 'br',
  'table', 'thead', 'tbody', 'tr', 'th', 'td'
]

const ALLOWED_ATTR = ['href', 'title', 'class']

const PURIFY_CONFIG = {
  ALLOWED_TAGS,
  ALLOWED_ATTR,
  FORBID_TAGS: ['script', 'iframe', 'object', 'embed', 'style', 'form'],
  FORBID_ATTR: ['onerror', 'onload', 'onclick', 'onmouseover', 'onfocus', 'onblur', 'onkeydown', 'onkeyup']
}

/**
 * Convert raw markdown to sanitized HTML.
 * Returns an empty string for null, undefined, empty, or non-string inputs.
 */
export function renderSafeMarkdown(raw) {
  if (!raw || typeof raw !== 'string') return ''
  const html = marked.parse(raw)
  return DOMPurify.sanitize(html, PURIFY_CONFIG)
}
