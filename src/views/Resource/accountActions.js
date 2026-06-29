/**
 * 解析账户列表行应按当前用户角色展示的操作项。
 *
 * 业务规则来源：CO-388
 * - 账户管理员：编辑、登记归还、下架
 * - 投标专员且为绑定联系人：编辑、登记归还、下架
 * - 投标专员且非绑定联系人：借用
 * - 申请人（项目负责人 / 销售）：申请使用
 * - 其他角色：无操作
 *
 * 注意：本函数依赖 `account.contactPerson` 为当前用户 userId，该数据口径由
 * CO-390（contactPerson 升级 userId + 历史数据迁移）保证。在 CO-390 完成前，
 * 投标专员「是否绑定联系人」的判断可能不准确。
 *
 * @param {Object} params
 * @param {boolean} params.isManager 当前用户是否为账户管理员
 * @param {boolean} params.isBidTeam 当前用户是否为投标专员
 * @param {boolean} params.isContactPerson 当前用户是否为该账户绑定联系人
 * @param {boolean} params.isApplicant 当前用户是否可以发起申请使用
 * @returns {{edit?: true, return?: true, takeDown?: true, borrow?: true, apply?: true}}
 */
export function resolveAccountActions({ isManager, isBidTeam, isContactPerson, isApplicant }) {
  if (isManager) {
    return { edit: true, return: true, takeDown: true }
  }

  if (isBidTeam) {
    return isContactPerson ? { edit: true, return: true, takeDown: true } : { borrow: true }
  }

  if (isApplicant) {
    return { apply: true }
  }

  return {}
}
