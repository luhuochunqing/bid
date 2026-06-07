// Input: login API failure payloads from axios or normalized API responses
// Output: user-facing login failure message
// Pos: src/stores/ - Login error presentation helpers
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

const BAD_CREDENTIAL_MESSAGES = new Set([
  '用户名或密码错误',
  'Bad credentials',
  'Invalid credentials',
])

export function resolveLoginFailureMessage(errorOrResult) {
  const status = errorOrResult?.response?.status ?? errorOrResult?.status
  const serverMessage = errorOrResult?.response?.data?.msg || errorOrResult?.message

  if (status === 401 || BAD_CREDENTIAL_MESSAGES.has(serverMessage)) {
    return '密码错误，请重新输入'
  }

  return serverMessage || '登录失败，请稍后重试'
}
