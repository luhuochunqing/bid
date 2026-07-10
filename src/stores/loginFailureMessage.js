// Input: login API failure payloads from axios or normalized API responses
// Output: user-facing login failure message
// Pos: src/stores/ - Login error presentation helpers
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

const BAD_CREDENTIAL_MESSAGES = new Set([
  '用户名或密码错误',
  'Bad credentials',
  'Invalid credentials',
])

// 后端 ExceptionResponseStrategy.buildWithPrefix 产生的前缀，需要转成用户友好消息
const PREFIX_MESSAGE_MAP = {
  'ROLE_NOT_AUTHORIZED': '您没有该系统的访问权限，请联系管理员',
  'ACCOUNT_DISABLED': '账户已停用',
}

function resolvePrefixedMessage(serverMessage) {
  if (!serverMessage) return null
  for (const [prefix, friendly] of Object.entries(PREFIX_MESSAGE_MAP)) {
    if (serverMessage.startsWith(prefix + ':')) {
      return friendly
    }
  }
  return null
}

export function resolveLoginFailureMessage(errorOrResult) {
  const status = errorOrResult?.response?.status ?? errorOrResult?.status
  const serverMessage = errorOrResult?.response?.data?.msg || errorOrResult?.message

  if (status === 401 || BAD_CREDENTIAL_MESSAGES.has(serverMessage)) {
    return '密码错误，请重新输入'
  }

  // 403 等非密码错误：优先把后端带前缀的消息转成用户友好文案
  const friendly = resolvePrefixedMessage(serverMessage)
  if (friendly) {
    return friendly
  }

  return serverMessage || '登录失败，请稍后重试'
}
