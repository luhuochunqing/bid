// Input: a raw value or a Vue ref/computed
// Output: the unwrapped value
// Pos: src/utils/ - Unified ref/raw value unpacking helper
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

/**
 * 解包 ref/computed 或原始值。
 *
 * Vue composable 的调用方可能传入 ref 对象（响应式）也可能传入原始值（快照）。
 * 本函数统一处理两种情况，避免调用方误传 ref 对象导致 String(ref) === "[object Object]"
 * 或条件判断永远为 true 的问题。
 *
 * @param {*} v 原始值、ref、computed 或 nullish
 * @returns {*} 解包后的原始值；若入参为 null/undefined 则原样返回
 */
export function resolveOpt(v) {
  return v != null && typeof v === 'object' && v.__v_isRef ? v.value : v
}
