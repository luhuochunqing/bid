import { resolveAccountActions, isCurrentUserContactPerson, canRevealPassword, isBorrowerWithinWindow } from '../accountActions.js'

/**
 * Account 表格行的操作权限构造器。
 *
 * - rowActions(row) 返回单行的 actions 对象（用于 detail dialog / AccountRowActions）
 * - canRevealPasswordFor(row) 判断小眼睛可见性
 *
 * 两者共享 userStore / userRoleCode 上下文，集中在此避免散落。
 */
export function useAccountRowActions({ userStore, userRoleCode }) {
  const rowActions = (row) => resolveAccountActions({
    isManager: userStore.isBidManager,
    isBidTeam: userRoleCode.value === 'bid-Team',
    isContactPerson: isCurrentUserContactPerson(row, userStore.currentUser),
    isApplicant: userRoleCode.value === 'bid-projectLeader' || userRoleCode.value === 'sales',
    status: row.status
  })

  // CO-400 round5 + CO-524: 小眼睛可见 = 管理员 OR (投标专员且为绑定联系人) OR 借用人窗口期内
  const canRevealPasswordFor = (row) => canRevealPassword({
    isManager: userStore.isBidManager,
    isBidTeam: userRoleCode.value === 'bid-Team',
    isContactPerson: isCurrentUserContactPerson(row, userStore.currentUser),
    isBorrowerWithinWindow: isBorrowerWithinWindow(row, userStore.currentUser)
  })

  return { rowActions, canRevealPasswordFor }
}
