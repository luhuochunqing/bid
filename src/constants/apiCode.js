/**
 * API 响应码常量
 *
 * 后端统一响应格式：{ code, message, data, success? }
 * - code = 200 表示业务成功（主要 API 约定）
 * - code = 0   表示业务成功（少数 AI/市场预测 API 沿用此约定）
 * - 其他 code 表示业务错误或 HTTP 错误映射
 *
 * 业务错误码（如 40101 账号已停用）也在此登记，避免散落在业务代码中。
 */
export const ApiCode = Object.freeze({
  // 成功
  SUCCESS: 200,
  SUCCESS_ZERO: 0,

  // HTTP 错误
  BAD_REQUEST: 400,
  UNAUTHORIZED: 401,
  FORBIDDEN: 403,
  NOT_FOUND: 404,
  CONFLICT: 409,
  SERVER_ERROR: 500,

  // 业务错误码
  ACCOUNT_DISABLED: 40101
})

export default ApiCode
