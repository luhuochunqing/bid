/**
 * 纯核心：将后端限流元数据映射为前端用户可读文案。
 * 不依赖 HTTP 库、UI 框架或 Vue 生态，只负责业务决策。
 */
export const DEFAULT_RATE_LIMIT_MESSAGE = '操作太快了，请稍等几秒再试'

const WAIT_MESSAGE_TEMPLATE = '操作太快了，请等待 {seconds} 秒后再试'

/**
 * @param {object} input
 * @param {number} [input.status]
 * @param {object} [input.data]
 * @param {object} [input.headers]
 * @returns {{ isRateLimit: boolean, message: string, waitSeconds: number | null }}
 */
export function resolveRateLimitMessage(input = {}) {
  const { status, data, headers } = input

  if (status !== 429) {
    return { isRateLimit: false, message: '', waitSeconds: null }
  }

  const serverMsg = data?.msg
  if (typeof serverMsg === 'string' && serverMsg.trim().length > 0) {
    return {
      isRateLimit: true,
      message: serverMsg,
      waitSeconds: extractRetryAfterSeconds(headers),
    }
  }

  const waitSeconds = extractRetryAfterSeconds(headers)
  if (waitSeconds != null) {
    return {
      isRateLimit: true,
      message: WAIT_MESSAGE_TEMPLATE.replace('{seconds}', String(waitSeconds)),
      waitSeconds,
    }
  }

  return {
    isRateLimit: true,
    message: DEFAULT_RATE_LIMIT_MESSAGE,
    waitSeconds: null,
  }
}

function extractRetryAfterSeconds(headers) {
  if (!headers || typeof headers !== 'object') {
    return null
  }

  const rawValue = headers['retry-after'] ?? headers['Retry-After']
  if (rawValue == null || rawValue === '') {
    return null
  }

  const parsed = Number(rawValue)
  if (!Number.isFinite(parsed) || parsed <= 0) {
    return null
  }

  return Math.ceil(parsed)
}
