// Input: visibilityRules[], currentUserRole, currentOrgId
// Output: fieldStates - 每个字段基于角色的 hidden/readonly 状态
// Pos: composables/ — 前端权限过滤层（Pure Core）
// 维护声明: 纯函数逻辑，可单测.
// 与后端 VisibilityApplicator + RoleBasedFieldFilter 保持对称.
// 不依赖 Vue 响应式，仅做数据变换；响应式由调用方包装.

import { computed } from 'vue'
import { useUserStore } from '@/stores/user.js'

/**
 * 字段状态：基于角色可见性规则的状态
 */
export interface VisibilityFieldState {
  hidden: boolean
  readonly: boolean
  readonlyText: boolean
}

/**
 * 单条可见性规则（对应后端 FieldVisibility）
 */
export interface VisibilityRule {
  id?: number
  fieldKey: string
  rolePattern: string | null  // null = 所有角色
  orgId: number | null        // null = 所有组织
  visible: boolean
  readonly: boolean
  hidden: boolean
}

/**
 * 检查用户的角色是否匹配 rolePattern
 * 支持格式：
 *   "ADMIN"          - 精确匹配
 *   "bid_specialist"  - 精确匹配
 *   "STAFF,bid_specialist" - 多角色之一
 *   "*"              - 所有角色
 *   null             - 所有角色
 */
function matchRole(rolePattern: string | null, userRole: string): boolean {
  if (!rolePattern || rolePattern === '*') return true

  const patterns = rolePattern.split(',').map(p => p.trim())
  return patterns.some(p => p === userRole || p === '*')
}

/**
 * 检查用户的 orgId 是否匹配
 */
function matchOrg(orgId: number | null, userOrgId: number | null): boolean {
  if (orgId === null) return true       // 规则对所有组织生效
  if (userOrgId === null) return false   // 用户无组织，但规则指定了组织
  return orgId === userOrgId
}

/**
 * 纯函数：基于可见性规则计算每个字段的状态
 * 规则优先级：取最高优先级（最宽松）的匹配规则
 */
function computeVisibilityStates(
  visibilityRules: VisibilityRule[],
  userRole: string,
  userOrgId: number | null
): Record<string, VisibilityFieldState> {
  const result: Record<string, VisibilityFieldState> = {}

  const grouped = new Map<string, VisibilityRule[]>()
  for (const rule of visibilityRules) {
    const list = grouped.get(rule.fieldKey) ?? []
    list.push(rule)
    grouped.set(rule.fieldKey, list)
  }

  for (const [fieldKey, rules] of grouped.entries()) {
    // 找最匹配的规则（role + org 都匹配）
    const matching = rules.filter(r =>
      matchRole(r.rolePattern, userRole) && matchOrg(r.orgId, userOrgId)
    )

    if (matching.length === 0) {
      // 无匹配规则：默认可见、可编辑
      result[fieldKey] = { hidden: false, readonly: false, readonlyText: false }
      continue
    }

    // 多条匹配时，取第一条（通常只有一条）
    const rule = matching[0]
    result[fieldKey] = {
      hidden: rule.hidden,
      readonly: rule.readonly,
      readonlyText: false
    }
  }

  return result
}

// ---------------------------------------------------------------------------
// Public API
// ---------------------------------------------------------------------------

/**
 * useFieldVisibility — 基于角色的字段可见性 composable
 *
 * @param visibilityRules - 可见性规则数组（从 resolved schema 传入）
 * @param userRoleOverride - 可选：手动指定角色，默认从 userStore 读取
 * @param userOrgIdOverride - 可选：手动指定 orgId，默认从 userStore 读取
 * @returns 字段状态映射的 computed
 *
 * 用法示例：
 *   const visStates = useFieldVisibility(visibilityRules)
 *   // visStates.value['budget'] => { hidden: false, readonly: true, readonlyText: false }
 */
export function useFieldVisibility(
  visibilityRules: VisibilityRule[],
  userRoleOverride?: string,
  userOrgIdOverride?: number | null
) {
  const userStore = useUserStore()

  return computed(() => {
    const role = userRoleOverride ?? userStore.userRole ?? 'staff'
    const orgId = userOrgIdOverride ?? null
    return computeVisibilityStates(visibilityRules, role, orgId)
  })
}

/**
 * 合并两个 FieldState 来源（useFormConditions 的条件状态 + useFieldVisibility 的权限状态）
 * 条件状态的优先级高于权限状态（即条件可以覆盖角色规则）
 */
export function mergeFieldStates(
  permissionStates: Record<string, VisibilityFieldState>,
  conditionStates: Record<string, { hidden: boolean; readonly: boolean; required: boolean }>
): Record<string, { hidden: boolean; readonly: boolean; readonlyText: boolean; required: boolean }> {
  const allKeys = new Set([
    ...Object.keys(permissionStates),
    ...Object.keys(conditionStates)
  ])

  const merged: Record<string, any> = {}

  for (const key of allKeys) {
    const perm = permissionStates[key]
    const cond = conditionStates[key]

    if (perm && cond) {
      merged[key] = {
        // 条件为 true 时无条件覆盖；条件为 false 时取权限状态
        hidden: cond.hidden || perm.hidden,
        readonly: cond.readonly || perm.readonly,
        readonlyText: perm.readonlyText,
        required: cond.required
      }
    } else if (cond) {
      merged[key] = {
        hidden: cond.hidden,
        readonly: cond.readonly,
        readonlyText: false,
        required: cond.required
      }
    } else if (perm) {
      merged[key] = {
        hidden: perm.hidden,
        readonly: perm.readonly,
        readonlyText: perm.readonlyText,
        required: false
      }
    } else {
      merged[key] = {
        hidden: false,
        readonly: false,
        readonlyText: false,
        required: false
      }
    }
  }

  return merged
}

export { matchRole, matchOrg, computeVisibilityStates }
export type { VisibilityFieldState, VisibilityRule }
