// Input: 错误类型枚举（no-permission / not-found / network-error）+ 原始错误对象
// Output: ProjectLoadError 自定义错误类，用于项目详情加载失败时的错误传播
// Pos: src/utils/ - 前端工具层
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

/**
 * 项目加载失败错误类型
 * @enum {string}
 */
export const PROJECT_LOAD_ERROR_TYPE = {
  NO_PERMISSION: 'no-permission',
  NOT_FOUND: 'not-found',
  NETWORK_ERROR: 'network-error'
}

/**
 * 项目加载失败自定义错误类
 *
 * 用于 projectStore.getProjectById 在 API 失败时抛出，
 * 让上层（useProjectDetailBoot）能根据 errorType 渲染差异化错误界面。
 *
 * 典型用法：
 *   throw new ProjectLoadError(PROJECT_LOAD_ERROR_TYPE.NO_PERMISSION, '无权限访问该项目', error)
 *
 * @see specs/026-fix-project-detail-403-frontend/data-model.md §4
 */
export class ProjectLoadError extends Error {
  /**
   * @param {string} errorType 错误类型（见 PROJECT_LOAD_ERROR_TYPE）
   * @param {string} message 用户可见的错误描述
   * @param {Error} [cause] 原始错误对象（403/404/网络错误的原始 Error）
   */
  constructor(errorType, message, cause) {
    super(message)
    this.name = 'ProjectLoadError'
    this.errorType = errorType
    this.cause = cause
  }
}
