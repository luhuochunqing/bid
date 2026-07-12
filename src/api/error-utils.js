// Input: API error from axios or normalized error
// Output: helpers to avoid duplicate toast on globally-handled 429 rate-limit errors
// Pos: src/api/ - shared API error utilities
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { ElMessage } from 'element-plus'

/**
 * Check whether an error is an HTTP 429 (rate limit) response.
 * @param {any} error
 * @returns {boolean}
 */
export function isRateLimitError(error) {
  return error?.response?.status === 429
}

/**
 * Show an error toast unless the error is a 429 rate-limit response.
 * 429 is already handled by the global axios interceptor with a friendly message,
 * so business catch blocks should skip it to avoid duplicate / conflicting toasts.
 *
 * @param {any} error
 * @param {string} fallbackMessage
 */
export function notifyErrorUnlessRateLimit(error, fallbackMessage) {
  if (isRateLimitError(error)) {
    return
  }
  const serverMsg = error?.response?.data?.msg || error?.response?.data?.message
  ElMessage.error(serverMsg || error?.message || fallbackMessage)
}
