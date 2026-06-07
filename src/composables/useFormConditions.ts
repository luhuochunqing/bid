// Input: conditions[], formData
// Output: fieldStates - 每个字段的 hidden/readonly 状态
// Pos: composables/ — 前端条件逻辑层（Pure Core）
// 维护声明: 纯函数逻辑，可单测.
// 与后端 ConditionEvaluator(java) 保持同步，支持 10 种操作符.
// 不依赖 Vue 响应式，仅做数据变换；响应式由调用方用 computed 包装.

import { ref, watch, computed } from 'vue'

/**
 * 字段状态：条件求值结果
 */
export interface FieldState {
  hidden: boolean
  readonly: boolean
  required: boolean
}

/**
 * 单条条件规则（对应后端 FormFieldCondition）
 */
export interface FormCondition {
  id?: number
  sourceField: string
  operator: ConditionOperator
  targetValue: string | null
  action: ConditionAction
  targetField: string
  displayOrder?: number
}

export type ConditionOperator =
  | 'eq'
  | 'neq'
  | 'in'
  | 'not_in'
  | 'contains'
  | 'not_contains'
  | 'gt'
  | 'gte'
  | 'lt'
  | 'lte'
  | 'exists'
  | 'not_exists'

export type ConditionAction =
  | 'show'
  | 'hide'
  | 'readonly'
  | 'unreadonly'
  | 'require'
  | 'unrequire'
  | 'skip'
  | 'unhide'

const ALL_OPERATORS = new Set([
  'eq', 'neq', 'in', 'not_in', 'contains', 'not_contains',
  'gt', 'gte', 'lt', 'lte', 'exists', 'not_exists'
])

/**
 * 纯函数：判断某条条件是否满足（模仿后端 ConditionEvaluator.evaluate）
 */
function evaluateCondition(condition: FormCondition, sourceValue: unknown): boolean {
  const op = condition.operator
  const target = condition.targetValue ?? ''
  const src = valueToString(sourceValue)

  switch (op) {
    case 'eq':
      return src === target
    case 'neq':
      return src !== target
    case 'in':
      return inList(src, target)
    case 'not_in':
      return !inList(src, target)
    case 'gt':
      return compare(sourceValue, target) > 0
    case 'gte':
      return compare(sourceValue, target) >= 0
    case 'lt':
      return compare(sourceValue, target) < 0
    case 'lte':
      return compare(sourceValue, target) <= 0
    case 'contains':
      return src.includes(target)
    case 'not_contains':
      return !src.includes(target)
    case 'exists':
      return !isEmpty(sourceValue)
    case 'not_exists':
      return isEmpty(sourceValue)
    default:
      return true
  }
}

function inList(value: string, targetList: string): boolean {
  if (!targetList || value === '') return false
  return targetList.split(',').map(s => s.trim()).includes(value)
}

function compare(fieldValue: unknown, target: string): number {
  if (fieldValue == null) return -1
  if (target == null) return 1

  const numVal = toNumber(fieldValue)
  const numTarget = toNumber(target)
  if (!isNaN(numVal) && !isNaN(numTarget)) {
    return numVal - numTarget
  }
  return valueToString(fieldValue).localeCompare(target)
}

function toNumber(value: unknown): number {
  if (typeof value === 'number') return value
  if (typeof value === 'string') return Number.parseFloat(value)
  return NaN
}

function valueToString(value: unknown): string {
  if (value == null) return ''
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}

function isEmpty(value: unknown): boolean {
  if (value == null) return true
  if (typeof value === 'string') return value.trim() === ''
  if (Array.isArray(value)) return value.length === 0
  return false
}

/**
 * 纯函数：对所有条件求值，返回每个字段的最终状态
 */
function evaluateAllConditions(
  conditions: FormCondition[],
  formData: Record<string, unknown>,
  initialStates: Record<string, FieldState>
): Record<string, FieldState> {
  const states = Object.fromEntries(
    Object.entries(initialStates).map(([k, v]) => [k, { ...v }])
  )

  const sorted = [...conditions].sort((a, b) => (a.displayOrder ?? 0) - (b.displayOrder ?? 0))

  for (const condition of sorted) {
    if (!ALL_OPERATORS.has(condition.operator)) continue

    const sourceValue = formData[condition.sourceField]
    const satisfied = evaluateCondition(condition, sourceValue)

    const targetField = condition.targetField
    if (!states[targetField]) {
      states[targetField] = { hidden: false, readonly: false, required: false }
    }

    if (!satisfied) continue

    switch (condition.action) {
      case 'hide':
        states[targetField].hidden = true
        break
      case 'show':
      case 'unhide':
        states[targetField].hidden = false
        break
      case 'readonly':
        states[targetField].readonly = true
        break
      case 'unreadonly':
        states[targetField].readonly = false
        break
      case 'require':
        states[targetField].required = true
        break
      case 'unrequire':
        states[targetField].required = false
        break
      case 'skip':
        break
    }
  }

  return states
}

// ---------------------------------------------------------------------------
// Public API
// ---------------------------------------------------------------------------

/**
 * useFormConditions — 前端条件逻辑 composable
 *
 * @param conditions - 条件规则数组（从 resolved schema 传入）
 * @param formDataRef - 表单数据的响应式引用
 * @returns 字段状态映射的 computed
 *
 * 用法示例：
 *   const fieldStates = useFormConditions(conditions, formData)
 *   // fieldStates.value['title'] => { hidden: false, readonly: false, required: true }
 */
export function useFormConditions(
  conditions: FormCondition[],
  formDataRef: { value: Record<string, unknown> }
) {
  const fieldStates = ref<Record<string, FieldState>>({})

  watch(
    () => formDataRef.value,
    (data) => {
      const init: Record<string, FieldState> = {}
      for (const key of Object.keys(data)) {
        init[key] = { hidden: false, readonly: false, required: false }
      }
      fieldStates.value = evaluateAllConditions(conditions, data, init)
    },
    { immediate: true, deep: true }
  )

  return computed(() => fieldStates.value)
}

export { evaluateCondition, evaluateAllConditions }
export type { FieldState }
