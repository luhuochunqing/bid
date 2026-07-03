// Input: vue-router 实例 + 项目 ID + 跳转选项
// Output: navigateToProject / navigateToProjectList —— 项目详情页统一跳转入口
// Pos: src/utils/ - 前端工具层
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { ElMessage } from 'element-plus'

/**
 * 标讯未关联项目时的提示文案
 */
export const PROJECT_NOT_LINKED_MESSAGE = '该标讯未关联项目'

/**
 * 统一项目详情页跳转入口
 *
 * 设计意图（specs/026-fix-project-detail-403-frontend/research.md R1）：
 * - 入口侧不预处理权限（避免额外 API 请求）
 * - 由项目详情页统一处理 403/404 响应
 * - projectId 为空时仅提示，不发起跳转
 *
 * @param {object} router vue-router 实例（useRouter() 返回值）
 * @param {string|number|null|undefined} projectId 项目 ID
 * @param {object} [options] 预留扩展位（当前未使用）
 * @returns {void}
 */
export function navigateToProject(router, projectId, _options = {}) {
  if (!projectId) {
    ElMessage.warning(PROJECT_NOT_LINKED_MESSAGE)
    return
  }
  router.push({ name: 'ProjectDetail', params: { id: String(projectId) } })
}

/**
 * 跳转回项目列表页
 *
 * 用于项目详情页错误状态界面的"返回项目列表"按钮。
 *
 * @param {object} router vue-router 实例
 * @returns {void}
 */
export function navigateToProjectList(router) {
  router.push('/project')
}
