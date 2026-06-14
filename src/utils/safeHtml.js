// Input: raw HTML or markdown-derived string (any value)
// Output: sanitized HTML string safe for v-html; empty string on null/non-string input
// Pos: src/utils/ - global safeHtml helper for all v-html consumers
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import DOMPurify from 'dompurify'

// PURIFY_CONFIG mirrors src/components/common/doc-insight/markdownSanitizer.js
// to keep a single source of truth for sanitizer defaults.
// We re-declare it here (instead of importing) so this helper stays
// importable from any Vue component without crossing the doc-insight boundary.
const ALLOWED_TAGS = [
  'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
  'p', 'ul', 'ol', 'li',
  'strong', 'em', 'code', 'pre', 'blockquote',
  'a', 'hr', 'br',
  'table', 'thead', 'tbody', 'tr', 'th', 'td',
  'img', 'span', 'div', 'figure', 'figcaption'
]

const ALLOWED_ATTR = ['href', 'title', 'class', 'src', 'alt']

const PURIFY_CONFIG = {
  ALLOWED_TAGS,
  ALLOWED_ATTR,
  FORBID_TAGS: ['script', 'iframe', 'object', 'embed', 'style', 'form'],
  FORBID_ATTR: ['onerror', 'onload', 'onclick', 'onmouseover', 'onfocus', 'onblur', 'onkeydown', 'onkeyup']
}

/**
 * Sanitize arbitrary HTML for use with v-html.
 *
 * SECURITY: callers must pass server-controlled or previously-purified HTML
 * that requires only light tag allow-listing. This helper does NOT perform
 * markdown rendering — pair with `marked.parse` upstream if you need markdown.
 *
 * Returns an empty string for null, undefined, empty, or non-string inputs.
 */
export function safeHtml(input) {
  if (input == null || typeof input !== 'string' || input.length === 0) {
    return ''
  }
  return DOMPurify.sanitize(input, PURIFY_CONFIG)
}

export default safeHtml
